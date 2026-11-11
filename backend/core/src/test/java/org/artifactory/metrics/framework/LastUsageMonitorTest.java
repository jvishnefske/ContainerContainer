package org.artifactory.metrics.framework;

import ch.qos.logback.classic.Level;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Igor Usenko
 */
class LastUsageMonitorTest {

    private static final int MAX_IDLE_TIME = 10;
    public static final String MESSAGE = "message";

    private LastUsageMonitor monitor;
    private MemoryAppender appender;
    private TimeSupplier time;

    @BeforeEach
    void setUp() {
        appender = MemoryAppenderUtil.createMemoryAppender(LastUsageMonitor.class, Level.DEBUG);
        time = new TimeSupplier();
        monitor = new LastUsageMonitor(MAX_IDLE_TIME, MESSAGE, time);
    }

    @Test
    void noActivity() {
        monitor.check();

        assertTrue(appender.contains("No previous usage registered", Level.DEBUG));
    }

    @Test
    void whenLastActivityIsTimeouted() {
        monitor.register();
        time.setTime(MAX_IDLE_TIME + 10);

        monitor.check();

        assertTrue(appender.contains(MESSAGE, Level.WARN));
    }

    @Test
    void whenLastActivityIsTimeoutedButNotifiedAlready() {
        monitor.register();
        time.setTime(MAX_IDLE_TIME + 10);
        monitor.check();

        time.setTime(MAX_IDLE_TIME + 20);
        monitor.check();

        time.setTime(MAX_IDLE_TIME + 30);
        monitor.check();

        assertEquals(2, appender.search("Already notified - skipping").size());
        assertEquals(1, appender.search(MESSAGE).size());
    }

    @Test
    void whenLastActivityIsTimeoutedButHappenedAgain() {
        monitor.register();
        time.setTime(MAX_IDLE_TIME + 10);
        monitor.check();

        assertTrue(appender.contains(MESSAGE, Level.WARN));
        appender.reset();

        time.setTime(MAX_IDLE_TIME + 20);
        monitor.register();
        monitor.check();

        assertTrue(appender.contains("Idle time is ok - no need to notify", Level.DEBUG));
    }

    @Test
    void whenLastActivityIsNotTimeouted() {
        monitor.register();
        time.setTime(MAX_IDLE_TIME - 5);

        monitor.check();

        assertTrue(appender.contains("Idle time is ok - no need to notify", Level.DEBUG));
    }

    @Setter
    static class TimeSupplier implements Supplier<Long> {

        private long time;

        @Override
        public Long get() {
            return time;
        }
    }
}