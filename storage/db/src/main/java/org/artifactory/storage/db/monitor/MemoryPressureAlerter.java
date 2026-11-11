package org.artifactory.storage.db.monitor;

import com.jfrog.base.util.NumberFormatter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Samples JVM memory and GC counters on a fixed schedule and logs when any of
 * three indicators crosses a configured threshold:
 * <ol>
 *   <li><b>GC overload</b>: GC time / wall-clock time, over the last
 *       {@code gcOverheadWindowSeconds}.</li>
 *   <li><b>High heap sustained</b>: heap-used fraction at-or-above the warn
 *       threshold for <em>every</em> sample in the last
 *       {@code highHeapSustainedSeconds}.</li>
 *   <li><b>Rapid heap rise</b>: heap-used fraction grew by at least the
 *       threshold (in percentage points) over the last
 *       {@code heapRiseWindowSeconds}.</li>
 * </ol>
 *
 * <p>Per tick, each indicator emits at most one line at the level of the
 * highest tier its metric reaches: WARN above the warn threshold, DEBUG above
 * the debug threshold. The GC-overload indicator has two tiers (DEBUG and WARN);
 * the heap indicators have two (DEBUG and WARN). Below the lowest tier nothing
 * is logged.
 *
 * @author Eli Fishman
 */
@Slf4j
@Service
public class MemoryPressureAlerter {

    /** Upper bound for how long historical samples are retained. Must be at
     *  least as large as the longest configurable indicator window; sized
     *  generously above the 60 s defaults so the buffer comfortably covers
     *  any realistic tuning without becoming a user-tunable knob. */
    private static final Duration HISTORY_RETENTION = Duration.ofMinutes(5);

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans =
            ManagementFactory.getGarbageCollectorMXBeans();
    private final Deque<Sample> history = new ArrayDeque<>(128);

    @Value("${artifactory.memoryPressure.enabled:true}")
    private boolean enabled;

    // ----- GC overload tiers -----
    @Value("${artifactory.memoryPressure.gcOverheadDebugThreshold:0.04}")
    private double gcOverheadDebugThreshold;
    @Value("${artifactory.memoryPressure.gcOverheadWarnThreshold:0.08}")
    private double gcOverheadWarnThreshold;
    @Value("${artifactory.memoryPressure.gcOverheadWindowSeconds:60}")
    private long gcOverheadWindowSeconds;

    // ----- High-heap-sustained tiers -----
    @Value("${artifactory.memoryPressure.highHeapDebugThreshold:0.80}")
    private double highHeapDebugThreshold;
    @Value("${artifactory.memoryPressure.highHeapWarnThreshold:0.98}")
    private double highHeapWarnThreshold;
    @Value("${artifactory.memoryPressure.highHeapSustainedSeconds:60}")
    private long highHeapSustainedSeconds;

    // ----- Rapid heap-rise tiers (percentage-point delta over the window) -----
    @Value("${artifactory.memoryPressure.heapRiseDebugThreshold:0.10}")
    private double heapRiseDebugThreshold;
    @Value("${artifactory.memoryPressure.heapRiseWarnThreshold:0.40}")
    private double heapRiseWarnThreshold;
    @Value("${artifactory.memoryPressure.heapRiseWindowSeconds:60}")
    private long heapRiseWindowSeconds;

    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("MemoryPressureAlerter disabled by configuration");
            return;
        }
        log.info("MemoryPressureAlerter enabled: gc[{}/{}]/{}s, heap[{}/{}]/{}s, heapRise[{}/{}]/{}s",
                formatPercent(gcOverheadDebugThreshold), formatPercent(gcOverheadWarnThreshold),
                gcOverheadWindowSeconds,
                formatPercent(highHeapDebugThreshold), formatPercent(highHeapWarnThreshold),
                highHeapSustainedSeconds,
                formatPercent(heapRiseDebugThreshold), formatPercent(heapRiseWarnThreshold),
                heapRiseWindowSeconds);
    }

    @Scheduled(fixedRateString = "${artifactory.memoryPressure.interval.millis:5000}",
            initialDelayString = "${artifactory.memoryPressure.initial.delay.millis:30000}")
    void tick() {
        if (!enabled) {
            return;
        }
        tickWithSample(takeSample());
    }

    /** Runs one evaluation cycle on the given sample. Package-private as a
     *  test seam; no-op when the alerter is disabled. */
    void tickWithSample(Sample now) {
        if (!enabled) {
            return;
        }
        try {
            history.addLast(now);

            while (!history.isEmpty()
                    && now.elapsedSince(history.peekFirst()).compareTo(HISTORY_RETENTION) > 0) {
                history.pollFirst();
            }

            checkGcOverload(now);
            checkHighHeapSustained(now);
            checkRapidHeapRise(now);
        } catch (Exception e) {
            log.debug("MemoryPressureAlerter tick failed", e);
        }
    }

    private void checkGcOverload(Sample now) {
        Duration window = Duration.ofSeconds(gcOverheadWindowSeconds);
        Sample past = sampleAtLeast(window, now).orElse(null);
        if (past == null) {
            return;
        }

        Duration elapsed = now.elapsedSince(past);
        Duration gcElapsed = now.gcElapsedSince(past);
        double overhead = elapsed.isZero() || elapsed.isNegative()
                ? 0.0
                : (double) gcElapsed.toNanos() / elapsed.toNanos();

        if (overhead >= gcOverheadWarnThreshold) {
            log.warn("MemoryPressureAlerter: trigger=GC_OVERLOAD JVM spent {} of the last {}s in "
                            + "stop-the-world GC pauses (warn tier {}). Heap={}. "
                            + "GC death spiral; pod kill probable within minutes.",
                    formatPercent(overhead), elapsed.toSeconds(),
                    formatPercent(gcOverheadWarnThreshold), formatPercent(now.heapFraction));
        } else if (overhead >= gcOverheadDebugThreshold) {
            log.debug("MemoryPressureAlerter: trigger=GC_OVERLOAD JVM spent {} of the last {}s in GC "
                            + "(debug tier {}). Heap={}.",
                    formatPercent(overhead), elapsed.toSeconds(),
                    formatPercent(gcOverheadDebugThreshold), formatPercent(now.heapFraction));
        }
    }

    private void checkHighHeapSustained(Sample now) {
        Duration window = Duration.ofSeconds(highHeapSustainedSeconds);
        boolean windowComplete = !history.isEmpty()
                && now.elapsedSince(history.peekFirst()).compareTo(window) >= 0;
        boolean sustainedAtWarn = windowComplete
                && allSamplesInWindowAtLeast(now, window, highHeapWarnThreshold);

        if (sustainedAtWarn) {
            log.warn("MemoryPressureAlerter: trigger=HIGH_HEAP_SUSTAINED JVM heap has been >= {} "
                            + "(currently {}) for the last {}s (warn tier). Late-stage memory "
                            + "pressure; pod kill likely once GC cannot reclaim further.",
                    formatPercent(highHeapWarnThreshold), formatPercent(now.heapFraction),
                    window.toSeconds());
        } else if (!Double.isNaN(now.heapFraction) && now.heapFraction >= highHeapDebugThreshold) {
            log.debug("MemoryPressureAlerter: trigger=HIGH_HEAP_SUSTAINED heap={} (debug tier {}).",
                    formatPercent(now.heapFraction), formatPercent(highHeapDebugThreshold));
        }
    }

    private void checkRapidHeapRise(Sample now) {
        Duration window = Duration.ofSeconds(heapRiseWindowSeconds);
        Sample past = sampleAtLeast(window, now).orElse(null);
        if (past == null) {
            return;
        }
        if (Double.isNaN(now.heapFraction) || Double.isNaN(past.heapFraction)) {
            return;
        }

        double rise = now.heapFraction - past.heapFraction;
        if (rise >= heapRiseWarnThreshold) {
            log.warn("MemoryPressureAlerter: trigger=RAPID_HEAP_RISE JVM heap rose by {} in "
                            + "the last {}s ({} -> {}, warn tier {}). Memory-cliff "
                            + "pattern; OOM kill imminent.",
                    formatPercent(rise), window.toSeconds(),
                    formatPercent(past.heapFraction), formatPercent(now.heapFraction),
                    formatPercent(heapRiseWarnThreshold));
        } else if (rise >= heapRiseDebugThreshold) {
            log.debug("MemoryPressureAlerter: trigger=RAPID_HEAP_RISE heap rose by {} in {}s "
                            + "(debug tier {}).",
                    formatPercent(rise), window.toSeconds(), formatPercent(heapRiseDebugThreshold));
        }
    }

    private boolean allSamplesInWindowAtLeast(Sample now, Duration window, double threshold) {
        return history.stream()
                .filter(sample -> now.elapsedSince(sample).compareTo(window) <= 0)
                .allMatch(sample -> !Double.isNaN(sample.heapFraction)
                        && sample.heapFraction >= threshold);
    }

    private Sample takeSample() {
        long timeNanos = System.nanoTime();
        long gcMillis = gcBeans.stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .filter(collectionMillis -> collectionMillis > 0)
                .sum();
        Duration gcCumulative = Duration.ofMillis(gcMillis);

        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        double heapFraction = (heap.getMax() > 0)
                ? (double) heap.getUsed() / heap.getMax()
                : Double.NaN;
        return new Sample(timeNanos, gcCumulative, heapFraction);
    }

    private Optional<Sample> sampleAtLeast(Duration window, Sample now) {
        Sample best = null;
        for (Sample sample : history) {
            if (now.elapsedSince(sample).compareTo(window) >= 0) {
                best = sample;
            } else {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private static String formatPercent(double fraction) {
        return Double.isNaN(fraction) ? "NaN" : NumberFormatter.formatPercentage(fraction);
    }

    /** Point-in-time JVM memory snapshot. Package-private for tests.
     *  {@code timeNanos} stays a raw {@link System#nanoTime()} reading because it is a
     *  monotonic offset, not a wall-clock instant. */
    static final class Sample {
        final long timeNanos;
        final Duration gcCumulative;
        final double heapFraction;

        Sample(long timeNanos, Duration gcCumulative, double heapFraction) {
            this.timeNanos = timeNanos;
            this.gcCumulative = gcCumulative;
            this.heapFraction = heapFraction;
        }

        /** Wall-clock time between {@code earlier} and this sample. */
        Duration elapsedSince(Sample earlier) {
            return Duration.ofNanos(this.timeNanos).minus(Duration.ofNanos(earlier.timeNanos));
        }

        /** GC time accumulated between {@code earlier} and this sample. */
        Duration gcElapsedSince(Sample earlier) {
            return this.gcCumulative.minus(earlier.gcCumulative);
        }
    }
}
