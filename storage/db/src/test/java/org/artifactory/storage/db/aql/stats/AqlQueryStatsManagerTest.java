package org.artifactory.storage.db.aql.stats;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class AqlQueryStatsManagerTest extends ArtifactoryHomeBoundJUnitTest {

    private AqlStatsCapturingAppender appender;
    private Logger logbackLogger;
    private Level originalLevel;

    @BeforeEach
    void setUp() {
        appender = new AqlStatsCapturingAppender();
        appender.start();
        logbackLogger = (Logger) LoggerFactory.getLogger(AqlQueryStatsManager.class);
        originalLevel = logbackLogger.getLevel();
        logbackLogger.setLevel(Level.INFO);
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        logbackLogger.setLevel(originalLevel);
        appender.stop();
    }

    @Test
    void applyEventCreatesEntryOnFirstSight() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(event("aaa", "items.find({\"name\":\"_\"})",
                "items.find({\"name\":\"foo.jar\"})", 100, 5));

        assertEquals(1, manager.trackedEntriesCount());
        assertTrue(manager.tracksChecksum("aaa"));

        // Verify the recorded values through the observable report rather than internal state.
        manager.swapAndReport();
        String entry = findLineContaining("checksum=aaa");
        assertTrue(entry.contains("calls=1"), entry);
        assertTrue(entry.contains("totalMs=100"), entry);
        assertTrue(entry.contains("totalRows=5"), entry);
        assertTrue(entry.contains("minMs=100"), entry);
        assertTrue(entry.contains("maxMs=100"), entry);
        assertTrue(entry.contains("sample=items.find({\"name\":\"foo.jar\"})"), entry);
    }

    @Test
    void applyEventAggregatesMultipleObservations() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        String checksum = "abc";
        manager.applyEvent(event(checksum, "tpl", "first-sample", 100, 5));
        manager.applyEvent(event(checksum, "tpl", "second-sample", 200, 10));
        manager.applyEvent(event(checksum, "tpl", "third-sample", 50, 2));

        manager.swapAndReport();
        String entry = findLineContaining("checksum=abc");
        assertTrue(entry.contains("calls=3"), entry);
        assertTrue(entry.contains("totalMs=350"), entry);
        assertTrue(entry.contains("totalRows=17"), entry);
        assertTrue(entry.contains("minMs=50"), "min should track smallest observation -- " + entry);
        assertTrue(entry.contains("maxMs=200"), "max should track largest observation -- " + entry);
        // First-sight sample is sticky -- subsequent events do not overwrite it.
        assertTrue(entry.contains("sample=first-sample"),
                "sampleQuery must be captured once at entry creation and never overwritten -- " + entry);
    }

    @Test
    void evictionPreservesTopNinetyByDurationAndTopTenByCalls() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();

        // Pre-load 990 "high-duration" entries (dur-0..dur-989) with descending totalMs.
        // These will dominate the top-90-by-duration eviction rule.
        for (int i = 0; i < 990; i++) {
            manager.applyEvent(event("dur-" + i, "t", "s", 1_000_000L - i, 1));
        }
        // Pre-load 10 "high-call-count" entries with low duration but many calls.
        // These will dominate the top-10-by-calls eviction rule.
        for (int i = 0; i < 10; i++) {
            String id = "calls-" + i;
            for (int call = 0; call < 100 + i; call++) {
                manager.applyEvent(event(id, "t", "s", 1L, 1));
            }
        }
        // Map size at this point = 1000 exactly -- one below the eviction trigger threshold.
        assertEquals(1000, manager.trackedEntriesCount());
        assertEquals(0, manager.getEvictionCount(), "no eviction should have fired yet");

        // Adding ONE more distinct key triggers eviction. The new key is added AFTER eviction.
        manager.applyEvent(event("trigger", "t", "s", 1L, 1));

        // After a single eviction the map has at most MAX_KEPT_AFTER_EVICTION (=100)
        // entries from the eviction-kept set, plus the one new "trigger" entry.
        assertTrue(manager.trackedEntriesCount() <= AqlQueryStatsManager.MAX_KEPT_AFTER_EVICTION + 1,
                "After eviction + 1 new entry, map size should be <= "
                        + (AqlQueryStatsManager.MAX_KEPT_AFTER_EVICTION + 1)
                        + " -- got " + manager.trackedEntriesCount());
        assertEquals(1, manager.getEvictionCount(),
                "exactly one eviction should have fired across this test");

        // All 10 high-call entries must survive (top 10 by calls).
        for (int i = 0; i < 10; i++) {
            assertTrue(manager.tracksChecksum("calls-" + i),
                    "High-call entry calls-" + i + " should survive eviction by calls");
        }
        // The 90 highest-duration entries must survive (top 90 by duration).
        for (int i = 0; i < 90; i++) {
            assertTrue(manager.tracksChecksum("dur-" + i),
                    "High-duration entry dur-" + i + " should survive eviction by duration");
        }
        // dur-989 was the lowest-totalMs duration entry -- it must have been evicted.
        assertFalse(manager.tracksChecksum("dur-989"),
                "Lowest-duration entry should have been evicted");
        // Evicted counters must reflect the lost ~900 entries.
        assertTrue(manager.getEvictedCalls() > 0,
                "evictedCalls counter must be incremented when entries are evicted");
        assertTrue(manager.getEvictedDurationMs() > 0,
                "evictedDurationMs counter must be incremented when entries are evicted");
    }

    @Test
    void swapAndReportClearsStateAndPreservesNothing() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(event("k1", "t", "s", 100, 5));
        manager.applyEvent(event("k2", "t", "s", 200, 10));

        assertEquals(2, manager.trackedEntriesCount());

        manager.swapAndReport();

        assertEquals(0, manager.trackedEntriesCount(),
                "swapAndReport must replace the map with a fresh empty one");
        assertEquals(0L, manager.getEvictedCalls(),
                "swapAndReport must reset evictedCalls");
        assertEquals(0L, manager.getEvictedDurationMs(),
                "swapAndReport must reset evictedDurationMs");
        assertEquals(0L, manager.getEvictionCount(),
                "swapAndReport must reset evictionCount");
    }

    @Test
    void swapAndReportIsNoOpWhenNothingToReport() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.swapAndReport();
        assertTrue(appender.lines.isEmpty(),
                "Empty tick must produce zero log lines -- got: " + appender.lines);
    }

    @Test
    void offerDoesNotBlockAndCountsDropsWhenQueueFull() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        // Inject a tiny capacity-2 queue so we can fill it without the worker draining.
        manager.setQueueForTest(new LinkedBlockingQueue<>(2));

        manager.offer(event("a", "t", "s", 10, 1));
        manager.offer(event("b", "t", "s", 10, 1));

        // The next two offers must be silently dropped (no exception, no block-forever).
        // Generous timing bound to avoid CI flakiness under GC pressure; the real proof
        // of non-blocking is that the test completes at all.
        long startNanos = System.nanoTime();
        manager.offer(event("c", "t", "s", 10, 1));
        manager.offer(event("d", "t", "s", 10, 1));
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

        assertTrue(elapsedMs < 1000,
                "offer() on a full queue must NOT block -- elapsed " + elapsedMs + "ms");
        assertEquals(2L, manager.getDroppedEvents(),
                "Two over-capacity offers should bump droppedEvents by 2");
    }

    @Test
    void offerIsNoOpWhenFeatureDisabled() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.offer(event("a", "t", "s", 10, 1));
        assertFalse(manager.isEnabled(),
                "Manager without an initialized queue is treated as disabled");
        assertEquals(0L, manager.getDroppedEvents(),
                "offer() into a null queue must NOT count as a drop -- the feature is disabled");
    }

    @Test
    void offerRejectsNullEvent() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.setQueueForTest(new LinkedBlockingQueue<>(10));
        manager.offer(null);
        assertEquals(0L, manager.getDroppedEvents(),
                "Null event should be silently ignored, not counted as a drop");
    }

    @Test
    void init_whenFeatureDisabled_doesNotInitializeQueue() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.init();
        assertFalse(manager.isEnabled(), "Feature disabled by default; queue must not be initialized");
    }

    @Test
    void init_whenFeatureEnabled_initializesQueueAndLogsInfo() {
        setStringSystemProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.init();
        try {
            assertTrue(manager.isEnabled(), "Feature enabled; queue must be initialized");
            assertTrue(appender.lines.stream().anyMatch(l -> l.contains("AQL statistics collection enabled")),
                    "init must emit an INFO line confirming enablement -- got: " + appender.lines);
        } finally {
            manager.shutdown();
            setStringSystemProperty(ConstantValues.aqlStatisticsCollectionEnabled, "false");
        }
    }

    @Test
    void shutdown_whenWorkerNotStarted_isNoOp() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        assertDoesNotThrow(manager::shutdown, "shutdown on a never-initialized manager must not throw");
    }

    @Test
    void shutdown_gracefullyTerminatesRunningWorker() {
        setStringSystemProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.init();
        assertTrue(manager.isEnabled());

        long startMs = System.currentTimeMillis();
        manager.shutdown();
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertTrue(elapsedMs < 6_000L,
                "shutdown must complete within the join timeout -- elapsed " + elapsedMs + "ms");
        setStringSystemProperty(ConstantValues.aqlStatisticsCollectionEnabled, "false");
    }

    @Test
    void isEnabled_returnsTrueWhenQueueIsInitialized() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        assertFalse(manager.isEnabled(), "No queue injected; must report disabled");
        manager.setQueueForTest(new LinkedBlockingQueue<>(10));
        assertTrue(manager.isEnabled(), "Queue injected; must report enabled");
    }

    @Test
    void scheduledTriggerReport_whenFeatureDisabled_isNoOp() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.scheduledTriggerReport();
        assertTrue(appender.lines.isEmpty(),
                "No log lines should be emitted when the feature is disabled -- got: " + appender.lines);
    }

    @Test
    void scheduledTriggerReport_whenQueueFull_logsWarning() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.setQueueForTest(new LinkedBlockingQueue<>(1));
        manager.offer(event("fill", "t", "s", 1, 1));
        manager.scheduledTriggerReport();
        assertTrue(appender.lines.stream().anyMatch(l -> l.contains("AQL stats queue is full")),
                "Full-queue condition must produce a WARN log -- got: " + appender.lines);
    }

    @Test
    void scheduledTriggerReport_postsReportSentinel_workerExecutesReport() throws InterruptedException {
        setStringSystemProperty(ConstantValues.aqlStatisticsCollectionEnabled, "true");
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.init();
        try {
            manager.offer(event("e2e", "tpl", "sample-query", 99, 3));
            manager.scheduledTriggerReport();
            awaitLogLine("checksum=e2e", 5_000);
            String line = findLineContaining("checksum=e2e");
            assertTrue(line.contains("calls=1"), line);
            assertTrue(line.contains("totalMs=99"), line);
            assertTrue(line.contains("totalRows=3"), line);
        } finally {
            manager.shutdown();
            setStringSystemProperty(ConstantValues.aqlStatisticsCollectionEnabled, "false");
        }
    }

    @Test
    void truncate_nullTemplateAndSample_renderedAsEmptyString() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(new AqlStatsEvent("nullchk", null, null, 10, 1));
        manager.swapAndReport();
        String line = findLineContaining("checksum=nullchk");
        assertFalse(line.contains("template=null"),
                "null template must be rendered as empty string, not the literal 'null' -- " + line);
        assertFalse(line.contains("sample=null"),
                "null sample must be rendered as empty string, not the literal 'null' -- " + line);
    }

    @Test
    void truncate_stringExceedingLimit_isTruncatedWithSuffix() {
        setLongSystemProperty(ConstantValues.aqlStatisticsLogFieldMaxChars, 5);
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(event("trunchk", "a-very-long-template-value", "short", 10, 1));
        manager.swapAndReport();
        String line = findLineContaining("checksum=trunchk");
        assertTrue(line.contains("...[truncated]"),
                "Template exceeding the max-chars limit must be truncated with the suffix -- " + line);
    }

    @Test
    void swapAndReport_dropsOnly_emitsSummaryAndResetsCounter() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.setQueueForTest(new LinkedBlockingQueue<>(1));
        manager.offer(event("accepted", "t", "s", 1, 1)); // fills the queue
        manager.offer(event("dropped1", "t", "s", 1, 1)); // dropped -- droppedEvents = 1
        manager.offer(event("dropped2", "t", "s", 1, 1)); // dropped -- droppedEvents = 2
        assertEquals(2L, manager.getDroppedEvents());

        manager.swapAndReport(); // entries empty, but dropped > 0 → must NOT be a no-op

        String summary = findLineContaining("AQL stats summary");
        assertTrue(summary.contains("droppedEvents=2"),
                "Summary must report the drop count even when no entries were processed -- " + summary);
        assertEquals(0L, manager.getDroppedEvents(),
                "droppedEvents counter must reset to 0 after swapAndReport");
    }

    @Test
    void recordObservation_middleValue_doesNotUpdateMinOrMax() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        String checksum = "mid";
        manager.applyEvent(event(checksum, "t", "s", 50, 0));   // min=max=50
        manager.applyEvent(event(checksum, "t", "s", 200, 0));  // max → 200
        manager.applyEvent(event(checksum, "t", "s", 100, 0));  // between min and max: neither updated

        manager.swapAndReport();
        String line = findLineContaining("checksum=mid");
        assertTrue(line.contains("minMs=50"), "Middle value must not lower the minimum -- " + line);
        assertTrue(line.contains("maxMs=200"), "Middle value must not raise the maximum -- " + line);
        assertTrue(line.contains("calls=3"), line);
    }

    @Test
    void swapAndReport_summaryReflectsEvictedCounters() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        for (int i = 0; i < AqlQueryStatsManager.MAX_ENTRIES; i++) {
            manager.applyEvent(event("key-" + i, "t", "s", 100 + i, 1));
        }
        manager.applyEvent(event("overflow", "t", "s", 1, 1));
        assertTrue(manager.getEvictedCalls() > 0, "Eviction must have occurred");

        manager.swapAndReport();

        String summary = findLineContaining("AQL stats summary");
        assertFalse(summary.contains("totalEvictedMs=0") && summary.contains("totalEvictedCalls=0"),
                "Summary must reflect non-zero evicted counters after eviction -- " + summary);
    }

    private void awaitLogLine(String needle, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (appender.lines.stream().anyMatch(l -> l.contains(needle))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError(
                "Log line containing `" + needle + "` did not appear within " + timeoutMs + "ms; got: " +
                        appender.lines);
    }

    private String findLineContaining(String needle) {
        return appender.lines.stream()
                .filter(l -> l.contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No log line contained `" + needle + "`; got: " + appender.lines));
    }

    private static AqlStatsEvent event(String checksum, String template, String sample,
            long durationMs, long rows) {
        return new AqlStatsEvent(checksum, template, sample, durationMs, rows);
    }

}
