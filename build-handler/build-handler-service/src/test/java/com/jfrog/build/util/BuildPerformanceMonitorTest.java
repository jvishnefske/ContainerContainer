package com.jfrog.build.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPerformanceMonitorTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        clearThreadContext();
        logger = (Logger) LoggerFactory.getLogger(BuildPerformanceMonitor.class);
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        clearThreadContext();
    }

    @Test
    void printOperationStats_shouldLogNothingButHeader_whenOnlyInitialised() {
        BuildPerformanceMonitor.initOperationStats(build("my-build", "5"), "my-repo");

        BuildPerformanceMonitor.printOperationStats();

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("Build performance stats for my-build/5/2024-01-01T00:00:00.000+0000 in repo: my-repo:",
                event.getFormattedMessage());
    }

    @Test
    void logOperationStats_shouldReportElapsedMillisForOperation() {
        long opStart = System.nanoTime() - MILLISECONDS.toNanos(200);

        BuildPerformanceMonitor.initOperationStats(build("b", "1"), "repo");
        BuildPerformanceMonitor.logOperationStats("fetch-builds", opStart);
        BuildPerformanceMonitor.printOperationStats();

        String message = lastMessage();
        Matcher matcher = Pattern.compile("\\* fetch-builds took: (\\d+) ms").matcher(message);
        assertTrue(matcher.find(), "Unexpected message: " + message);
        assertTrue(Long.parseLong(matcher.group(1)) >= 200, "Elapsed millis should be at least the elapsed offset");
    }

    @Test
    void printOperationStats_shouldJoinHeaderAndOperationsInOrder() {
        BuildPerformanceMonitor.initOperationStats(build("b", "1"), "repo");
        BuildPerformanceMonitor.logOperationStats("first", System.nanoTime());
        BuildPerformanceMonitor.logOperationStats("second", System.nanoTime());

        BuildPerformanceMonitor.printOperationStats();

        String message = lastMessage();
        String[] lines = message.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("Build performance stats for b/1/"));
        assertTrue(lines[1].startsWith("* first took:"));
        assertTrue(lines[2].startsWith("* second took:"));
    }

    @Test
    void printOperationStats_shouldResetContext_soSubsequentStatsStartFresh() {
        BuildPerformanceMonitor.initOperationStats(build("b", "1"), "repo");
        BuildPerformanceMonitor.logOperationStats("first-cycle", System.nanoTime());
        BuildPerformanceMonitor.printOperationStats();
        appender.list.clear();

        BuildPerformanceMonitor.initOperationStats(build("b", "1"), "repo");
        BuildPerformanceMonitor.logOperationStats("second-cycle", System.nanoTime());
        BuildPerformanceMonitor.printOperationStats();

        String message = lastMessage();
        assertTrue(message.contains("second-cycle"), "Unexpected message: " + message);
        assertFalse(message.contains("first-cycle"), "Stats from the previous cycle leaked: " + message);
    }

    @Test
    void operationStats_shouldBeNoOp_whenNotInitialised() {
        // After the refactoring, logging/printing without initOperationStats() leaves no context and emits nothing.
        BuildPerformanceMonitor.logOperationStats("orphan-op", System.nanoTime());
        BuildPerformanceMonitor.printOperationStats();

        assertTrue(appender.list.isEmpty(), "No stats should be logged when not initialised");
    }

    @Test
    void context_shouldBeIsolatedPerThread() throws InterruptedException {
        // Accumulate stats on the main thread but do not print yet.
        BuildPerformanceMonitor.initOperationStats(build("b", "1"), "repo");
        BuildPerformanceMonitor.logOperationStats("main-op", System.nanoTime());

        AtomicReference<String> workerMessage = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            BuildPerformanceMonitor.initOperationStats(build("b", "1"), "repo");
            BuildPerformanceMonitor.logOperationStats("worker-op", System.nanoTime());
            BuildPerformanceMonitor.printOperationStats();
            workerMessage.set(lastMessage());
        });
        worker.start();
        worker.join();

        // The worker's context must not see the main thread's operation.
        assertTrue(workerMessage.get().contains("worker-op"), "Unexpected worker message: " + workerMessage.get());
        assertFalse(workerMessage.get().contains("main-op"), "Main thread stats leaked into worker thread");

        appender.list.clear();
        BuildPerformanceMonitor.printOperationStats();
        String mainMessage = lastMessage();
        assertTrue(mainMessage.contains("main-op"), "Unexpected main message: " + mainMessage);
        assertFalse(mainMessage.contains("worker-op"), "Worker thread stats leaked into main thread");
    }

    private String lastMessage() {
        List<ILoggingEvent> events = appender.list;
        assertFalse(events.isEmpty(), "Expected at least one log event");
        return events.getLast().getFormattedMessage();
    }

    private static Build build(String name, String number) {
        Build build = new Build();
        build.setName(name);
        build.setNumber(number);
        build.setStarted("2024-01-01T00:00:00.000+0000");
        return build;
    }

    private static void clearThreadContext() {
        try {
            Field contextField = BuildPerformanceMonitor.class.getDeclaredField("CONTEXT");
            contextField.setAccessible(true);
            ((ThreadLocal<?>) contextField.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to reset BuildPerformanceMonitor thread context", e);
        }
    }
}
