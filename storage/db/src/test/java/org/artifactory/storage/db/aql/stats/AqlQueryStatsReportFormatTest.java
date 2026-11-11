package org.artifactory.storage.db.aql.stats;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Amir Avidan
 */
class AqlQueryStatsReportFormatTest extends ArtifactoryHomeBoundJUnitTest {

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
    void summaryLineCarriesAllRequiredFields() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(new AqlStatsEvent("aaa", "tpl-a", "sample-a", 100L, 5L));
        manager.applyEvent(new AqlStatsEvent("aaa", "tpl-a", "ignored-second-sample", 200L, 7L));

        manager.swapAndReport();

        String summary = findLineContaining("AQL stats summary");
        assertTrue(summary.contains("totalMs=300"), "summary should carry totalMs -- " + summary);
        assertTrue(summary.contains("totalCalls=2"), "summary should carry totalCalls -- " + summary);
        assertTrue(summary.contains("totalEvictedMs=0"), "summary should carry totalEvictedMs -- " + summary);
        assertTrue(summary.contains("totalEvictedCalls=0"), "summary should carry totalEvictedCalls -- " + summary);
        assertTrue(summary.contains("evictionCount=0"), "summary should carry evictionCount -- " + summary);
        assertTrue(summary.contains("droppedEvents=0"), "summary should carry droppedEvents -- " + summary);
    }

    @Test
    void topByDurationEntryUsesKeyValueFormat() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(new AqlStatsEvent("c1", "tpl-1", "sample-1", 100L, 5L));
        manager.swapAndReport();

        String line = findLineContaining("AQL top-by-duration");
        assertTrue(line.contains("rank=1"), "entry should carry rank -- " + line);
        assertTrue(line.contains("checksum=c1"), "entry should carry checksum -- " + line);
        assertTrue(line.contains("calls=1"), "entry should carry calls -- " + line);
        assertTrue(line.contains("totalMs=100"), "entry should carry totalMs -- " + line);
        assertTrue(line.contains("maxMs=100"), "entry should carry maxMs -- " + line);
        assertTrue(line.contains("minMs=100"), "entry should carry minMs -- " + line);
        assertTrue(line.contains("totalRows=5"), "entry should carry totalRows -- " + line);
        // Use key=value style consistent with the rest of the line so simple log parsers
        // (cut -d'=' / awk) work uniformly across every field.
        assertTrue(line.contains("template=tpl-1"),
                "template field should use key=value format -- " + line);
        assertTrue(line.contains("sample=sample-1"),
                "sample field should use key=value format -- " + line);
    }

    @Test
    void topByCallsSectionEmitted() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(new AqlStatsEvent("c1", "tpl-1", "sample-1", 100L, 5L));
        manager.swapAndReport();
        assertTrue(appender.lines.stream().anyMatch(l -> l.contains("AQL top-by-calls")),
                "report must include the top-by-calls section");
    }

    @Test
    void reportProducesOneLinePerTopEntryOrderedByDurationDesc() {
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        // 3 entries with different durations.
        manager.applyEvent(new AqlStatsEvent("low", "t-low", "s-low", 10L, 1L));
        manager.applyEvent(new AqlStatsEvent("hi", "t-hi", "s-hi", 1000L, 1L));
        manager.applyEvent(new AqlStatsEvent("mid", "t-mid", "s-mid", 100L, 1L));
        manager.swapAndReport();

        List<String> durationLines = appender.lines.stream()
                .filter(l -> l.contains("AQL top-by-duration"))
                .toList();
        assertEquals(3, durationLines.size(), "Expected exactly 3 top-by-duration lines");
        assertTrue(durationLines.get(0).contains("rank=1") && durationLines.get(0).contains("checksum=hi"),
                "Highest totalMs should be rank=1 -- " + durationLines.get(0));
        assertTrue(durationLines.get(1).contains("rank=2") && durationLines.get(1).contains("checksum=mid"),
                "Middle totalMs should be rank=2 -- " + durationLines.get(1));
        assertTrue(durationLines.get(2).contains("rank=3") && durationLines.get(2).contains("checksum=low"),
                "Lowest totalMs should be rank=3 -- " + durationLines.get(2));
    }

    @Test
    void truncatesPathologicallyLongTemplateOrSample() {
        // Defensive: a runaway long query must not produce a 100-MB log line.
        String hugeTemplate = "items.find({\"name\":\"" + "x".repeat(3000) + "\"})";
        String hugeSample = "items.find({\"name\":\"" + "y".repeat(3000) + "\"})";
        AqlQueryStatsManager manager = new AqlQueryStatsManager();
        manager.applyEvent(new AqlStatsEvent("huge", hugeTemplate, hugeSample, 1L, 1L));
        manager.swapAndReport();

        String line = findLineContaining("AQL top-by-duration");
        assertTrue(line.length() < hugeTemplate.length() + hugeSample.length(),
                "Long template/sample should be truncated in the log output -- length " + line.length());
        assertTrue(line.contains("[truncated]"),
                "Truncation marker should appear in the log -- " + line);
    }

    private String findLineContaining(String needle) {
        return appender.lines.stream()
                .filter(l -> l.contains(needle))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("No log line contained `" + needle + "`; got: " + appender.lines));
    }

}
