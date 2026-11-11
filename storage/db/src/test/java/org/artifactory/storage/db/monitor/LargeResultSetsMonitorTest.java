package org.artifactory.storage.db.monitor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LargeResultSetsMonitorTest {
    SimpleMeterRegistry meterRegistry;
    LargeResultSetsMonitor monitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        monitor = new LargeResultSetsMonitor(meterRegistry);
    }

    @Test
    void shouldMonitor_returnsFalse_forStreaming() {
        assertFalse(monitor.shouldMonitor("SELECT * FROM test", true, null));
    }

    @Test
    void shouldMonitor_returnsTrue_forNonStreaming() {
        assertTrue(monitor.shouldMonitor("SELECT * FROM test", false, null));
    }

    @Test
    void onQueryFetchCompleted_shouldNotReport_whenRowsCountBelowThreshold() {
        LargeResultSetsMonitor spyMonitor = spy(monitor);
        spyMonitor.setMinRowsCountToReport(100);
        DbQueryContext ctx = new DbQueryContext("q", false, null);
        spyMonitor.onQueryFetchCompleted(ctx, 99, 1000);
        verify(spyMonitor, never()).reportLargeResultSet(any(), anyLong(), anyLong());
    }

    @Test
    void onQueryFetchCompleted_shouldReport_whenRowsCountIsAboveThreshold() {
        LargeResultSetsMonitor spyMonitor = spy(monitor);
        spyMonitor.setMinRowsCountToReport(100);
        DbQueryContext ctx = new DbQueryContext("q", false, null);
        spyMonitor.onQueryFetchCompleted(ctx, 100, 1000);
        verify(spyMonitor, times(1)).reportLargeResultSet(ctx, 100L, 1000L);
    }

    @Test
    void reportLargeResultSet_shouldReportCounterMetric() {
        LargeResultSetsMonitor testMonitor = new LargeResultSetsMonitor(meterRegistry);
        testMonitor.setTimeSupplier(() -> 0L); // avoid throttling
        DbQueryContext ctx = new DbQueryContext("q", false, null, false,
                "action", "kind", "group", "pkg", "actor");
        testMonitor.reportLargeResultSet(ctx, 123, 456);
        double count = meterRegistry.get("db_query_large")
                .tag("action", "action")
                .tag("group", "group")
                .tag("package", "pkg")
                .counter().count();
        assertEquals(1.0, count, 0.0001);
    }

    @Test
    void reportLargeResultSet_throttlesLogging() {
        LargeResultSetsMonitor testMonitor = new LargeResultSetsMonitor(meterRegistry);

        testMonitor.setTimeSupplier(new LongSupplier() {
            long t = 0;

            @Override
            public long getAsLong() {
                return t += 1000;
            }
        });
        testMonitor.setMinLogIntervalMsec(1500);
        DbQueryContext ctx = new DbQueryContext("q", false,
                "hint", false, "action", "kind",
                "group", "pkg", "ci");

        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(LargeResultSetsMonitor.class);
        Level originalLevel = logger.getLevel();
        try {
            listAppender.start();
            logger.addAppender(listAppender);
            logger.setLevel(Level.INFO);

            // First call logs
            testMonitor.reportLargeResultSet(ctx, 123, 100);
            assertEquals(1, listAppender.list.size());

            // Second call within interval does not log
            testMonitor.reportLargeResultSet(ctx, 123, 300);
            assertEquals(1, listAppender.list.size());

            // Third call within interval logs again
            testMonitor.reportLargeResultSet(ctx, 902, 70_000_000);
            assertEquals(2, listAppender.list.size());

            ILoggingEvent event = listAppender.list.getLast();
            String actual = event.getFormattedMessage();
            assertEquals("Large result set: query='q', rows=902, " +
                         "action=action (kind/group, package=pkg, actor=ci), hint=hint, fetch-ms=70",
                    actual);
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(listAppender);
        }
    }

    @Test
    void throttleLogging_returnsPreviousTime_whenWithinInterval() {
        monitor.setMinLogIntervalMsec(1000);
        long actual = monitor.throttleLogging(1000, 1500);
        assertEquals(1000, actual);
    }

    @Test
    void throttleLogging_ReturnsNowIfOutsideInterval() {
        monitor.setMinLogIntervalMsec(1000);
        long actual = monitor.throttleLogging(1000, 2500);
        assertEquals(2500, actual);
    }
}
