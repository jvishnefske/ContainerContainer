package org.artifactory.storage.db.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MemoryPressureAlerter}.
 *
 * <p>Tests drive {@link MemoryPressureAlerter#tickWithSample(MemoryPressureAlerter.Sample)}
 * with synthetic samples, inject thresholds via {@link ReflectionTestUtils},
 * and assert on log output captured by a Logback {@link ListAppender}.
 *
 * @author  Eli Fishman
 */
class MemoryPressureAlerterTest extends ArtifactoryHomeBoundJUnitTest {

    private static final long SECOND_NS = TimeUnit.SECONDS.toNanos(1);

    private MemoryPressureAlerter alerter;
    private Logger alerterLogger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        alerter = new MemoryPressureAlerter();
        configureDefaults(alerter);

        alerterLogger = (Logger) LoggerFactory.getLogger(MemoryPressureAlerter.class);
        originalLevel = alerterLogger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        alerterLogger.addAppender(appender);
        alerterLogger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        alerterLogger.detachAppender(appender);
        alerterLogger.setLevel(originalLevel);
    }

    @Test
    void disabled_doesNothing() {
        ReflectionTestUtils.setField(alerter, "enabled", false);

        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, 30 * SECOND_NS, 0.50));

        assertEquals(0L, countLogsAtOrAbove(Level.DEBUG),
                "Disabled alerter must not emit any log lines");
    }

    @Test
    void gcOverload_warnTier_logsWarn() {
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, 24 * SECOND_NS, 0.50));

        assertTrue(countLogsAtLevel(Level.WARN) >= 1,
                "WARN tier must produce at least one warn log line");
    }


    @Test
    void gcOverload_debugTier_logsDebugNotWarn() {
        // 5% GC time over a 60s window: above the 4% debug tier, below the 40%
        // warn tier (test default). The class emits this at INFO level — the
        // field is named "Debug" historically; the actual log level is INFO.
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, 3 * SECOND_NS, 0.50));

        assertEquals(0L, countLogsAtLevel(Level.WARN),
                "5% GC time must not reach the WARN tier");
        assertTrue(countLogsAtLevel(Level.DEBUG) >= 1,
                "5% GC time must reach the debug tier (which logs at INFO)");
    }

    @Test
    void gcOverload_belowDebugTier_doesNotLog() {
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, SECOND_NS, 0.50));

        assertEquals(0L, countLogsAtOrAbove(Level.DEBUG),
                "1.7% GC time is below the lowest tier and must produce no log lines");
    }

    @Test
    void highHeapSustained_warnTier_logsWarn_whenAllSamplesAtOrAboveThreshold() {
        // Three samples spanning a full 60 s window, all at 0.99 (above the 0.98 warn tier).
        alerter.tickWithSample(sample(0L, 0L, 0.99));
        alerter.tickWithSample(sample(30 * SECOND_NS, 0L, 0.99));
        alerter.tickWithSample(sample(60 * SECOND_NS, 0L, 0.99));

        assertTrue(countLogsAtLevel(Level.WARN) >= 1,
                "HIGH_HEAP_SUSTAINED warn must fire when every sample in the window "
                        + "is at or above the threshold");
    }

    @Test
    void highHeapSustained_warnTier_doesNotFire_whenOneSampleBelowThreshold() {
        alerter.tickWithSample(sample(0L, 0L, 0.99));
        alerter.tickWithSample(sample(30 * SECOND_NS, 0L, 0.80)); // dip
        alerter.tickWithSample(sample(60 * SECOND_NS, 0L, 0.99));

        assertEquals(0L, countLogsAtLevel(Level.WARN),
                "HIGH_HEAP_SUSTAINED warn must NOT fire when any sample in the window "
                        + "drops below the warn tier");
    }

    @Test
    void rapidHeapRise_warnTier_logsWarn_whenRiseExceedsFortyPp() {
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, 0L, 0.91));

        assertTrue(countLogsAtLevel(Level.WARN) >= 1,
                "RAPID_HEAP_RISE warn must fire on a 41 pp rise over the window");
    }

    @Test
    void rapidHeapRise_belowDebugTier_doesNotLog() {
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, 0L, 0.55));

        assertEquals(0L, countLogsAtOrAbove(Level.DEBUG),
                "5 pp rise is below the rapid-heap-rise debug tier of 10 pp");
    }

    @Test
    void nanHeapFraction_isHandledGracefully() {
        alerter.tickWithSample(sample(0L, 0L, Double.NaN));
        alerter.tickWithSample(sample(60 * SECOND_NS, 0L, Double.NaN));

        // The contract is "no NPE, no false-positive WARN". The class
        // intentionally short-circuits any check that would coerce NaN.
        assertEquals(0L, countLogsAtLevel(Level.WARN),
                "NaN heap must not produce any WARN log line");
    }

    @Test
    void init_enabled_logsConfiguredThresholds() {
        alerter.init();

        assertTrue(anyLogContains(Level.INFO, "MemoryPressureAlerter enabled"),
                "init() in the enabled state must emit an INFO line with the configured thresholds");
    }

    @Test
    void init_disabled_logsDisabledReason() {
        ReflectionTestUtils.setField(alerter, "enabled", false);

        alerter.init();

        assertTrue(anyLogContains(Level.INFO, "MemoryPressureAlerter disabled by configuration"),
                "init() in the disabled state must emit the disabled-by-configuration INFO line");
    }

    @Test
    void tick_disabled_doesNotSampleOrLog() {
        ReflectionTestUtils.setField(alerter, "enabled", false);

        alerter.tick();

        assertEquals(0, history().size(),
                "Disabled tick() must not produce a sample");
        assertEquals(0L, countLogsAtOrAbove(Level.DEBUG),
                "Disabled tick() must not emit any log line");
    }

    @Test
    void tick_enabled_appendsRealJvmSampleToHistory() {
        int sizeBefore = history().size();

        assertDoesNotThrow(() -> alerter.tick(),
                "tick() against the live JVM MX-beans must not throw");

        assertEquals(sizeBefore + 1, history().size(),
                "Enabled tick() must append exactly one sample produced by takeSample()");
        assertNotNull(history().peekLast(),
                "The appended sample must be non-null");
    }

    @Test
    void tickWithSample_nullSample_isSwallowedAndLoggedAtDebug() {
        // ArrayDeque#addLast(null) throws NullPointerException. The catch block in
        // tickWithSample is supposed to swallow any failure and log at DEBUG so the
        // scheduled task never propagates an exception out of the JVM-monitoring thread.
        assertDoesNotThrow(() -> alerter.tickWithSample(null),
                "tickWithSample must never propagate an exception from a bad sample");

        assertTrue(anyLogContains(Level.DEBUG, "MemoryPressureAlerter tick failed"),
                "The swallowed exception must produce a DEBUG diagnostic line");
        assertEquals(0, history().size(),
                "A failed addLast must leave history untouched (no half-state)");
    }

    @Test
    void tickWithSample_evictsSamplesOlderThanHistoryRetention() {
        // HISTORY_RETENTION is 5 minutes. A sample 10 minutes in the past must be
        // evicted by the retention loop on the next tick.
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(10 * 60 * SECOND_NS, 0L, 0.50));

        assertEquals(1, history().size(),
                "Samples older than HISTORY_RETENTION must be evicted from the deque");
    }

    @Test
    void gcOverload_zeroElapsedWindow_triggersDivideByZeroGuardWithoutFiring() {
        // Pathological config: a zero-second GC window makes sampleAtLeast pick "now"
        // as its own past, so elapsed == 0. The divide-by-zero guard must coerce
        // overhead to 0.0 and emit no GC_OVERLOAD line (rather than +Infinity / NaN).
        ReflectionTestUtils.setField(alerter, "gcOverheadWindowSeconds", 0L);

        assertDoesNotThrow(() -> {
            alerter.tickWithSample(sample(0L, 0L, 0.50));
            alerter.tickWithSample(sample(0L, 10 * SECOND_NS, 0.50));
        }, "Zero-elapsed window must not throw (divide-by-zero guard)");

        assertFalse(anyLogContains(Level.WARN, "GC_OVERLOAD"),
                "Zero-elapsed window must not fire GC_OVERLOAD at WARN");
        assertFalse(anyLogContains(Level.DEBUG, "GC_OVERLOAD"),
                "Zero-elapsed window must not fire GC_OVERLOAD at any tier");
    }

    @Test
    void highHeapSustained_debugTier_firesOnHeapAboveDebugBelowWarn() {
        // Single sample at 82%: above debug tier (80%), below warn tier (98%). The
        // only emission must be the HIGH_HEAP_SUSTAINED debug line.
        alerter.tickWithSample(sample(0L, 0L, 0.82));

        assertTrue(anyLogContains(Level.DEBUG, "HIGH_HEAP_SUSTAINED"),
                "Heap above the debug tier must produce a HIGH_HEAP_SUSTAINED DEBUG line");
        assertEquals(0L, countLogsAtLevel(Level.WARN),
                "Heap below the warn tier must not fire any WARN line");
    }

    @Test
    void rapidHeapRise_debugTier_firesOnRiseAboveDebugBelowWarn() {
        // 15 pp rise over a 60 s window: above the 10 pp debug tier, below the 40 pp
        // warn tier. The only emission must be the RAPID_HEAP_RISE debug line — heap
        // stays below the high-heap-sustained debug tier (80%), so no HIGH_HEAP line.
        alerter.tickWithSample(sample(0L, 0L, 0.50));
        alerter.tickWithSample(sample(60 * SECOND_NS, 0L, 0.65));

        assertTrue(anyLogContains(Level.DEBUG, "RAPID_HEAP_RISE"),
                "A 15 pp rise must produce a RAPID_HEAP_RISE DEBUG line");
        assertEquals(0L, countLogsAtLevel(Level.WARN),
                "A 15 pp rise must not reach the RAPID_HEAP_RISE warn tier");
    }

    private static MemoryPressureAlerter.Sample sample(
            long tNanos, long gcCumulativeNanos, double heapFraction) {
        return new MemoryPressureAlerter.Sample(
                tNanos, Duration.ofNanos(gcCumulativeNanos), heapFraction);
    }

    private static void configureDefaults(MemoryPressureAlerter a) {
        ReflectionTestUtils.setField(a, "enabled", true);

        ReflectionTestUtils.setField(a, "gcOverheadDebugThreshold", 0.04);
        ReflectionTestUtils.setField(a, "gcOverheadWarnThreshold", 0.40);
        ReflectionTestUtils.setField(a, "gcOverheadWindowSeconds", 60L);

        ReflectionTestUtils.setField(a, "highHeapDebugThreshold", 0.80);
        ReflectionTestUtils.setField(a, "highHeapWarnThreshold", 0.98);
        ReflectionTestUtils.setField(a, "highHeapSustainedSeconds", 60L);

        ReflectionTestUtils.setField(a, "heapRiseDebugThreshold", 0.10);
        ReflectionTestUtils.setField(a, "heapRiseWarnThreshold", 0.40);
        ReflectionTestUtils.setField(a, "heapRiseWindowSeconds", 60L);
    }

    private long countLogsAtLevel(Level level) {
        return logs().stream().filter(e -> e.getLevel().equals(level)).count();
    }

    private long countLogsAtOrAbove(Level level) {
        return logs().stream().filter(e -> e.getLevel().toInt() >= level.toInt()).count();
    }

    private boolean anyLogContains(Level level, String needle) {
        return logs().stream()
                .filter(e -> e.getLevel().equals(level))
                .anyMatch(e -> e.getFormattedMessage().contains(needle));
    }

    private Deque<?> history() {
        return (Deque<?>) ReflectionTestUtils.getField(alerter, "history");
    }

    private List<ILoggingEvent> logs() {
        return appender.list;
    }
}
