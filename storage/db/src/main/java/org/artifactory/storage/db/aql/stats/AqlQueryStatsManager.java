package org.artifactory.storage.db.aql.stats;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bounded queue + single daemon worker thread that drains AQL stats events into a map and emits
 * a periodic report to {@code artifactory-service.log}. Request threads only call the
 * non-blocking {@link #offer(AqlStatsEvent)}; queue-full events are silently dropped and counted.
 *
 * <p>The report cadence is driven by Spring's {@link Scheduled} -- the scheduler thread simply
 * posts a {@link #REPORT_SENTINEL} event onto the queue, so the worker stays the single writer
 * to the in-memory map and no locking is needed.
 *
 * <p>Gated by {@link ConstantValues#aqlStatisticsCollectionEnabled} (default OFF). When off the
 * worker is not started, {@link #offer} is a no-op and the scheduler tick is a no-op -- a hot
 * enable requires a restart.
 *
 * @author Amir Avidan
 */
@Slf4j
@Service
public class AqlQueryStatsManager {

    static final int MAX_ENTRIES = 1000;
    static final int MAX_KEPT_BY_DURATION = 90;
    static final int MAX_KEPT_BY_CALLS = 10;
    static final int MAX_KEPT_AFTER_EVICTION = MAX_KEPT_BY_DURATION + MAX_KEPT_BY_CALLS;
    static final int MAX_REPORTED_BY_DURATION = 50;
    static final int MAX_REPORTED_BY_CALLS = 10;

    private static final String WORKER_THREAD_NAME = "aql-stats-worker";
    private static final long WORKER_SHUTDOWN_JOIN_SECONDS = 5L;

    private static final Comparator<AqlStatsEntry> BY_DURATION_DESC =
            Comparator.comparingLong(AqlStatsEntry::getTotalMs).reversed();
    private static final Comparator<AqlStatsEntry> BY_CALLS_DESC =
            Comparator.comparingLong(AqlStatsEntry::getCalls).reversed();

    private static final String TOP_ENTRY_LOG_FORMAT =
            "{} | rank={} | checksum={} | calls={} | totalMs={} | maxMs={} | minMs={} | totalRows={} | " +
                    "template={} | sample={}";

    /**
     * Identity-checked marker telling the worker to swap and emit the periodic report.
     */
    private static final AqlStatsEvent REPORT_SENTINEL =
            new AqlStatsEvent("__aql_stats_report__", null, null, 0L, 0L);

    private final AtomicReference<BlockingQueue<AqlStatsEvent>> queue = new AtomicReference<>();
    private final AtomicLong droppedEvents = new AtomicLong();

    // Worker-thread-only state: no locking needed.
    private Map<String, AqlStatsEntry> entries = new HashMap<>();
    private long evictedCalls;
    private long evictedDurationMs;
    private long evictionCount;

    private Thread worker;

    @PostConstruct
    public void init() {
        if (!ConstantValues.aqlStatisticsCollectionEnabled.getBoolean()) {
            log.debug("AQL statistics collection is disabled ({}). Worker thread not started.",
                    ConstantValues.aqlStatisticsCollectionEnabled.getPropertyName());
            return;
        }
        int queueCapacity = Math.max(1, ConstantValues.aqlStatisticsQueueSize.getInt());
        this.queue.set(new ArrayBlockingQueue<>(queueCapacity));
        startWorker();
        log.info("AQL statistics collection enabled: queueCapacity={}", queueCapacity);
    }

    @PreDestroy
    public void shutdown() {
        if (worker == null) {
            return;
        }
        worker.interrupt();
        try {
            worker.join(TimeUnit.SECONDS.toMillis(WORKER_SHUTDOWN_JOIN_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Non-blocking enqueue. Silently drops on overflow (counted), no-op when feature off.
     */
    public void offer(AqlStatsEvent event) {
        BlockingQueue<AqlStatsEvent> q = this.queue.get();
        if (q == null || event == null) {
            return;
        }
        if (!q.offer(event)) {
            droppedEvents.incrementAndGet();
        }
    }

    public boolean isEnabled() {
        return queue.get() != null;
    }

    /**
     * Posts a report sentinel onto the queue so the worker performs the swap+report on its own
     * thread. No-op when the feature is off. The sentinel never counts as "dropped" -- on the
     * (extremely unlikely) overflow case we simply skip this tick; the next tick will report.
     */
    @Scheduled(fixedRateString = "#{${artifactory.aql.statistics.report.interval.minutes} * 60 * 1000}")
    public void scheduledTriggerReport() {
        BlockingQueue<AqlStatsEvent> q = this.queue.get();
        if (q == null) {
            return;
        }
        if (!q.offer(REPORT_SENTINEL)) {
            log.warn("AQL stats queue is full; skipping this report tick.");
        }
    }

    @VisibleForTesting
    long getDroppedEvents() {
        return droppedEvents.get();
    }

    @VisibleForTesting
    int trackedEntriesCount() {
        return entries.size();
    }

    @VisibleForTesting
    boolean tracksChecksum(String checksum) {
        return entries.containsKey(checksum);
    }

    @VisibleForTesting
    long getEvictedCalls() {
        return evictedCalls;
    }

    @VisibleForTesting
    long getEvictedDurationMs() {
        return evictedDurationMs;
    }

    @VisibleForTesting
    long getEvictionCount() {
        return evictionCount;
    }

    @VisibleForTesting
    void setQueueForTest(BlockingQueue<AqlStatsEvent> queue) {
        this.queue.set(queue);
    }

    private void startWorker() {
        worker = new Thread(this::runWorkerLoop, WORKER_THREAD_NAME);
        worker.setDaemon(true);
        worker.start();
    }

    private void runWorkerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                processNext(queue.get().take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("AQL stats worker swallowed an unexpected error; continuing", e);
            }
        }
    }

    private void processNext(AqlStatsEvent event) {
        if (event == REPORT_SENTINEL) {
            swapAndReport();
        } else {
            applyEvent(event);
        }
    }

    @VisibleForTesting
    void applyEvent(AqlStatsEvent event) {
        AqlStatsEntry entry = entries.get(event.checksum());
        if (entry == null) {
            if (entries.size() >= MAX_ENTRIES) {
                evict();
            }
            entry = new AqlStatsEntry(event.checksum(), event.template(), event.sampleQuery());
            entries.put(event.checksum(), entry);
        }
        entry.recordObservation(event.durationMs(), event.rowsCount());
    }

    /**
     * Keep top-90-by-duration + top-10-by-calls (deduplicated); accumulate the rest into evicted counters.
     */
    private void evict() {
        List<AqlStatsEntry> all = new ArrayList<>(entries.values());
        Set<String> survivorKeys = selectSurvivorKeys(all);

        Map<String, AqlStatsEntry> kept = HashMap.newHashMap(survivorKeys.size());
        for (AqlStatsEntry entry : all) {
            if (survivorKeys.contains(entry.getChecksum())) {
                kept.put(entry.getChecksum(), entry);
            } else {
                accumulateEvicted(entry);
            }
        }
        entries = kept;
        evictionCount++;
        log.debug("AQL stats map evicted: kept={}, evictedCallsTotal={}, evictedDurationMsTotal={}, evictionCount={}",
                kept.size(), evictedCalls, evictedDurationMs, evictionCount);
    }

    private static Set<String> selectSurvivorKeys(List<AqlStatsEntry> all) {
        return Stream.concat(
                        topChecksumsBy(all, BY_DURATION_DESC, MAX_KEPT_BY_DURATION),
                        topChecksumsBy(all, BY_CALLS_DESC, MAX_KEPT_BY_CALLS))
                .collect(Collectors.toSet());
    }

    private static Stream<String> topChecksumsBy(List<AqlStatsEntry> all, Comparator<AqlStatsEntry> order, int limit) {
        return all.stream()
                .sorted(order)
                .limit(limit)
                .map(AqlStatsEntry::getChecksum);
    }

    private void accumulateEvicted(AqlStatsEntry entry) {
        evictedCalls += entry.getCalls();
        evictedDurationMs += entry.getTotalMs();
    }

    @VisibleForTesting
    void swapAndReport() {
        ReportSnapshot snapshot = snapshotAndReset();
        if (snapshot.isEmpty()) {
            return;
        }
        logSummary(snapshot);
        logTop(snapshot.entries(), "AQL top-by-duration", BY_DURATION_DESC, MAX_REPORTED_BY_DURATION);
        logTop(snapshot.entries(), "AQL top-by-calls", BY_CALLS_DESC, MAX_REPORTED_BY_CALLS);
    }

    private ReportSnapshot snapshotAndReset() {
        Map<String, AqlStatsEntry> entriesSnapshot = entries;
        long evictedCallsSnapshot = evictedCalls;
        long evictedDurationMsSnapshot = evictedDurationMs;
        long evictionCountSnapshot = evictionCount;
        long droppedSnapshot = droppedEvents.getAndSet(0);

        long totalCalls = evictedCallsSnapshot;
        long totalMs = evictedDurationMsSnapshot;
        for (AqlStatsEntry entry : entriesSnapshot.values()) {
            totalCalls += entry.getCalls();
            totalMs += entry.getTotalMs();
        }

        entries = new HashMap<>();
        evictedCalls = 0;
        evictedDurationMs = 0;
        evictionCount = 0;

        return new ReportSnapshot(entriesSnapshot, totalCalls, totalMs,
                evictedCallsSnapshot, evictedDurationMsSnapshot,
                evictionCountSnapshot, droppedSnapshot);
    }

    private static void logSummary(ReportSnapshot snapshot) {
        log.info("AQL stats summary | totalMs={} | totalCalls={} | totalEvictedMs={} | totalEvictedCalls={} | " +
                        "evictionCount={} | droppedEvents={}",
                snapshot.totalMs(), snapshot.totalCalls(), snapshot.evictedDurationMs(), snapshot.evictedCalls(),
                snapshot.evictionCount(), snapshot.dropped());
    }

    private static void logTop(Map<String, AqlStatsEntry> snapshot, String prefix,
            Comparator<AqlStatsEntry> order, int limit) {
        List<AqlStatsEntry> top = snapshot.values().stream()
                .sorted(order)
                .limit(limit)
                .toList();
        int rank = 0;
        for (AqlStatsEntry entry : top) {
            rank++;
            log.info(TOP_ENTRY_LOG_FORMAT, prefix, rank, entry.getChecksum(), entry.getCalls(), entry.getTotalMs(),
                    entry.getMaxMs(), entry.getMinMs(), entry.getTotalRows(), entry.getTemplate(),
                    entry.getSampleQuery());
        }
    }

    /**
     * Immutable per-tick snapshot of all state the report needs. Lets {@link #swapAndReport()}
     * read top-to-bottom without juggling parallel local variables, and keeps {@link #logSummary}
     * a one-liner with no derived computation.
     */
    private record ReportSnapshot(Map<String, AqlStatsEntry> entries,
                                  long totalCalls,
                                  long totalMs,
                                  long evictedCalls,
                                  long evictedDurationMs,
                                  long evictionCount,
                                  long dropped) {
        boolean isEmpty() {
            return entries.isEmpty() && evictedCalls == 0 && evictedDurationMs == 0 && dropped == 0;
        }
    }
}
