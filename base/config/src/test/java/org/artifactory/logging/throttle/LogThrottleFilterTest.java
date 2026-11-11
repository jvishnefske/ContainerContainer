package org.artifactory.logging.throttle;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.spi.FilterReply;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.mockito.Mockito;
import org.slf4j.Marker;
import org.testng.annotations.Test;

import java.time.temporal.ChronoUnit;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class LogThrottleFilterTest extends ArtifactoryHomeBoundTest {

    private LogThrottleFilter logThrottleFilter;

    @Test
    public void testLogEventAllowed() {
        logThrottleFilter = new LogThrottleFilter();

        Logger logger = mockLogger();

        FilterReply reply = decideToLog(logger);

        assertEquals(FilterReply.NEUTRAL, reply);
    }

    @Test
    public void testLogEventDenied() {
        logThrottleFilter = new LogThrottleFilter();
        logThrottleFilter.setThrottleTopLimit(2);

        Logger logger = mockLogger();

        // Simulate reaching the log messages limit
        for (int i = 0; i < 2; i++) {
            decideToLog(logger);
        }

        // After reaching the limit, the log event should be denied
        FilterReply reply = decideToLog(logger);
        assertEquals(FilterReply.DENY, reply);
    }

    @Test
    public void testNewTimeFrame() {
        TestClock clock = new TestClock();

        logThrottleFilter = new LogThrottleFilter(clock);
        logThrottleFilter.setThrottleTopLimit(2);
        logThrottleFilter.setExpiryTimeFrameMillis(1000);

        Logger logger = mockLogger();

        // Simulate reaching the log messages limit
        for (int i = 0; i < 2; i++) {
            decideToLog(logger);
        }

        // Advance clock by 1 second to reach expiry time
        clock.plus(1000, ChronoUnit.MILLIS);

        // After entry was expired, we start a new count and the log event should be allowed
        FilterReply reply = decideToLog(logger);
        assertEquals(FilterReply.NEUTRAL, reply);
    }

    @Test
    public void testLogEventDeniedAfterReachLimitInNewTimeFrame() {
        TestClock clock = new TestClock();

        logThrottleFilter = new LogThrottleFilter(clock);
        logThrottleFilter.setThrottleTopLimit(2);
        logThrottleFilter.setExpiryTimeFrameMillis(1000);

        Logger logger = mockLogger();

        // Simulate reaching the log messages limit
        for (int i = 0; i < 2; i++) {
            decideToLog(logger);
        }

        // Advance clock by 1 second to reach expiry time
        clock.plus(1000, ChronoUnit.MILLIS);

        // After entry was expired, we start a new count and the log event should be allowed
        FilterReply reply = decideToLog(logger);
        assertEquals(FilterReply.NEUTRAL, reply);

        // Simulate reaching the log messages limit again
        for (int i = 0; i < 2; i++) {
            decideToLog(logger);
        }

        // After reaching the limit again, the log event should be denied
        reply = decideToLog(logger);
        assertEquals(FilterReply.DENY, reply);
    }

    @Test
    public void testEventWithNoMarkerIsNotThrottled() {
        logThrottleFilter = new LogThrottleFilter();
        logThrottleFilter.setThrottleTopLimit(2);
        logThrottleFilter.start();

        Logger logger = mockLogger();

        for (int i = 0; i < 2; i++) {
            logThrottleFilter.decide(null, logger, Level.INFO, "Test message", null, null);
        }

        FilterReply reply = logThrottleFilter.decide(null, logger, Level.INFO, "Test message", null, null);

        assertEquals(FilterReply.NEUTRAL, reply);
    }

    @Test
    public void testThrottlePerLoggerClass() {
        logThrottleFilter = new LogThrottleFilter();
        logThrottleFilter.setThrottleTopLimit(2);

        Logger loggerA = mockLogger("LoggerA");
        Logger loggerB = mockLogger("LoggerB");

        // LoggerA: Should allow first 2 logs, deny the 3rd
        assertEquals(FilterReply.NEUTRAL, decideToLog(loggerA));
        assertEquals(FilterReply.NEUTRAL, decideToLog(loggerA));
        assertEquals(FilterReply.DENY, decideToLog(loggerA));

        // LoggerB: Should allow first 2 logs, deny the 3rd (independent of LoggerA)
        assertEquals(FilterReply.NEUTRAL, decideToLog(loggerB));
        assertEquals(FilterReply.NEUTRAL, decideToLog(loggerB));
        assertEquals(FilterReply.DENY, decideToLog(loggerB));
    }

    private Logger mockLogger() {
        return mockLogger("testLogger");
    }

    private Logger mockLogger(String name) {
        Logger logger = Mockito.mock(Logger.class);
        when(logger.getName()).thenReturn(name);
        return logger;
    }

    private FilterReply decideToLog(Logger logger) {
        Marker marker = LogThrottleMarker.getInstance();
        return logThrottleFilter.decide(marker, logger, Level.INFO, "Test message", null, null);
    }
}