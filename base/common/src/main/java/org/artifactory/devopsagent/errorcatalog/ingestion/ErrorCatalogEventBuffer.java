package org.artifactory.devopsagent.errorcatalog.ingestion;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Bounded in-memory buffer for ERROR/WARN events. Two parallel structures:
 * <ul>
 *   <li>An aggregated map keyed by {@code signatureHash} — increments a count and advances
 *       the most-recent timestamp per unique signature.</li>
 *   <li>A bounded ring buffer of raw events — captures the per-event payload for the raw table.</li>
 * </ul>
 *
 * <p>{@link #offer(ErrorCatalogEvent)} is the appender entry point — non-blocking, never throws.
 * {@link #drain()} is the flush job's entry point — atomically swaps the aggregated map and
 * drains the raw queue.</p>
 */
@Slf4j
public class ErrorCatalogEventBuffer {

    /**
     * Snapshot of one signature group between flush windows.
     */
    public record AggregatedEntry(
            String signatureHash,
            String severity,
            String loggerClass,
            String exceptionClass,
            String templateMessage,
            long firstOccurredAtMs,
            long lastOccurredAtMs,
            long occurrenceCount) {
    }

    private final int maxSignatures;
    private final BlockingQueue<ErrorCatalogEvent> rawQueue;

    private final AtomicReference<ConcurrentHashMap<String, MutableAggregator>> aggregatedRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    private final LongAdder overflowDropped = new LongAdder();
    private final LongAdder rawDropped = new LongAdder();
    private final LongAdder offered = new LongAdder();

    public ErrorCatalogEventBuffer() {
        this(ConstantValues.errorCatalogBufferMaxSignatures.getInt(),
                ConstantValues.errorCatalogBufferMaxRawEvents.getInt());
    }

    public ErrorCatalogEventBuffer(int maxSignatures, int maxRawEvents) {
        this.maxSignatures = Math.max(1, maxSignatures);
        this.rawQueue = new ArrayBlockingQueue<>(Math.max(1, maxRawEvents));
    }

    /**
     * Non-blocking enqueue. On signature-cap overflow the event is dropped from the aggregated map
     * (the overflow counter increments). On raw-queue overflow the raw side is dropped — the
     * aggregated count is still bumped so dedup-level visibility is preserved.
     */
    public void offer(ErrorCatalogEvent event) {
        if (event == null) {
            return;
        }
        offered.increment();
        boolean[] capExceeded = {false};
        // Snapshot the current aggregated map up-front so a concurrent drain() can't shift the
        // compute() and size() reads to different map references.
        ConcurrentHashMap<String, MutableAggregator> current = aggregatedRef.get();
        // Aggregated side: compute() ensures atomic read-modify-write per signature.
        current.compute(event.signatureHash(), (key, existing) -> {
            if (existing == null) {
                if (current.size() >= maxSignatures) {
                    capExceeded[0] = true;
                    return null;
                }
                return MutableAggregator.from(event);
            }
            existing.merge(event);
            return existing;
        });
        if (capExceeded[0]) {
            overflowDropped.increment();
            return;
        }
        // Raw side: drop on full rather than block the logging thread.
        if (!rawQueue.offer(event)) {
            rawDropped.increment();
        }
    }

    /**
     * Atomically swap out the aggregated map and drain the raw queue. Returns both snapshots.
     */
    public DrainResult drain() {
        ConcurrentHashMap<String, MutableAggregator> previous =
                aggregatedRef.getAndSet(new ConcurrentHashMap<>());

        Map<String, AggregatedEntry> aggregatedSnapshot = new HashMap<>(previous.size());
        for (Map.Entry<String, MutableAggregator> e : previous.entrySet()) {
            aggregatedSnapshot.put(e.getKey(), e.getValue().toAggregatedEntry());
        }

        List<ErrorCatalogEvent> rawSnapshot = new ArrayList<>(rawQueue.size());
        rawQueue.drainTo(rawSnapshot);

        return new DrainResult(aggregatedSnapshot, rawSnapshot);
    }

    /** Approximate count of distinct signatures currently held. */
    public int signatureCount() {
        return aggregatedRef.get().size();
    }

    /** Approximate count of raw events currently queued. */
    public int rawQueueDepth() {
        return rawQueue.size();
    }

    public long overflowDroppedCount() {
        return overflowDropped.sum();
    }

    public long rawDroppedCount() {
        return rawDropped.sum();
    }

    public long offeredCount() {
        return offered.sum();
    }

    public record DrainResult(Map<String, AggregatedEntry> aggregated, List<ErrorCatalogEvent> rawEvents) {
    }

    /**
     * Per-signature mutable accumulator. Access is serialized by {@link ConcurrentHashMap#compute}
     * so non-volatile fields are safe.
     */
    private static final class MutableAggregator {
        final String signatureHash;
        final String severity;
        final String loggerClass;
        final String exceptionClass;
        final String templateMessage;
        final long firstOccurredAtMs;
        long lastOccurredAtMs;
        long count;

        private MutableAggregator(ErrorCatalogEvent e) {
            this.signatureHash = e.signatureHash();
            this.severity = e.severity();
            this.loggerClass = e.loggerClass();
            this.exceptionClass = e.exceptionClass();
            this.templateMessage = e.templateMessage();
            this.firstOccurredAtMs = e.occurredAtMs();
            this.lastOccurredAtMs = e.occurredAtMs();
            this.count = 1L;
        }

        static MutableAggregator from(ErrorCatalogEvent e) {
            return new MutableAggregator(e);
        }

        void merge(ErrorCatalogEvent e) {
            if (e.occurredAtMs() > lastOccurredAtMs) {
                lastOccurredAtMs = e.occurredAtMs();
            }
            count++;
        }

        AggregatedEntry toAggregatedEntry() {
            return new AggregatedEntry(signatureHash, severity, loggerClass, exceptionClass, templateMessage,
                    firstOccurredAtMs, lastOccurredAtMs, count);
        }
    }
}
