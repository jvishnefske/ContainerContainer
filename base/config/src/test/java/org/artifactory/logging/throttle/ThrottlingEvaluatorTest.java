package org.artifactory.logging.throttle;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.boolex.EvaluationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottlingEvaluatorTest {
    @Test
    void throttlerShouldRespectGlobalRateLimit() throws EvaluationException {
        TestClock clock = new TestClock();

        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(clock, Metrics.globalRegistry);
        evaluator.setGlobalPeriod(10_000);
        evaluator.setGlobalLimit(2);

        // Check that limit works (within the given window of time)
        boolean actual = evaluator.evaluate(new LoggingEvent());
        assertFalse(actual, "limit=2: should pass first event");
        actual = evaluator.evaluate(new LoggingEvent());
        assertFalse(actual, "limit=2: should pass second event");
        actual = evaluator.evaluate(new LoggingEvent());
        assertTrue(actual, "limit=2: should throttle third event");

        // Mimic the advance of time, but still within the time window
        clock.plus(5_000, ChronoUnit.MILLIS);
        actual = evaluator.evaluate(new LoggingEvent());
        assertTrue(actual, "Should throttle fourth event, while still within the time window");

        // Mimic the "jump" to the next window of time - should restart the counter
        clock.plus(5_001, ChronoUnit.MILLIS);
        actual = evaluator.evaluate(new LoggingEvent());
        assertFalse(actual, "Should pass fourth event when it fails into the new time window");
    }

    @Test
    void throttlerShouldRespectPerClassAndGlobalLimit() throws EvaluationException {
        // Arrange
        TestClock clock = new TestClock();

        LoggingEvent event1 = new LoggingEvent();
        event1.setLoggerName("logger-1");
        event1.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException("one")));

        LoggingEvent event2 = new LoggingEvent();
        event2.setLoggerName("logger-2");
        event2.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException("two")));

        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(clock, Metrics.globalRegistry);
        evaluator.setPerLoggerPeriod(10_000);
        evaluator.setPerLoggerLimit(1);
        evaluator.setGlobalPeriod(15_000);
        evaluator.setGlobalLimit(4);

        // Act and assert
        boolean actual = evaluator.evaluate(event1);
        assertFalse(actual, "The first event from logger-1 should pass");

        actual = evaluator.evaluate(event2);
        assertFalse(actual, "The first event from logger-2 should pass");

        // Advancing clock by 5 sec still keeps us within the "throttle window" for both global and per-logger limits.
        clock.plus(5_000, ChronoUnit.MILLIS);

        actual = evaluator.evaluate(event1);
        assertTrue(actual, "The second event from logger-1 should be throttled because per-logger limit is 1");

        actual = evaluator.evaluate(event2);
        assertTrue(actual, "The second event from logger-2 should be throttled because per-logger limit is 1");

        // Advancing clock by additional 5 seconds will reset the per-logger rate limits, but not the global rate limit
        clock.plus(5_000, ChronoUnit.MILLIS);

        actual = evaluator.evaluate(event1);
        assertTrue(actual, "The 5th log event should be throttled because the global limit is 4");

        // Reset the global throttling period as well
        clock.plus(5_001, ChronoUnit.MILLIS);

        actual = evaluator.evaluate(event1);
        assertFalse(actual, "An event from the logger-1 should pass after reset of throttling time window");
    }

    @Test
    void zeroGlobalLimitThrottlesAllCalls() throws EvaluationException {
        ThrottlingEvaluator evaluator = new ThrottlingEvaluator();
        evaluator.setGlobalLimit(0);

        boolean actual = evaluator.evaluate(new LoggingEvent());
        assertTrue(actual, "Setting global limit to zero should throttle all events");
    }

    @Test
    void negativeGlobalLimitAllowsAllCalls() throws EvaluationException {
        ThrottlingEvaluator evaluator = new ThrottlingEvaluator();
        evaluator.setGlobalLimit(-1);

        boolean actual = evaluator.evaluate(new LoggingEvent());
        assertFalse(actual, "Negative global limit should allow all events");
    }

    @Test
    void zeroPerLoggerLimitThrottlesAllCalls() throws EvaluationException {
        ThrottlingEvaluator evaluator = new ThrottlingEvaluator();
        evaluator.setPerLoggerLimit(0);

        boolean actual = evaluator.evaluate(new LoggingEvent());
        assertTrue(actual, "Setting per-logger limit to zero should throttle all events");
    }

    @Test
    void negativePerLoggerLimitAllowsAllCalls() throws EvaluationException {
        ThrottlingEvaluator evaluator = new ThrottlingEvaluator();
        evaluator.setPerLoggerLimit(-1);

        boolean actual = evaluator.evaluate(new LoggingEvent());
        assertFalse(actual, "Negative per-logger limit should allow all events");
    }

    @Test
    void throttlerShouldReportMetrics() throws EvaluationException {
        MeterRegistry registry = new SimpleMeterRegistry();
        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(Clock.systemUTC(), registry);
        evaluator.setGlobalLimit(2);
        evaluator.setPerLoggerLimit(1);

        LoggingEvent event1 = new LoggingEvent();
        event1.setLoggerName("logger-1");
        event1.setThrowableProxy(new ThrowableProxy(new IllegalArgumentException("one")));

        evaluator.evaluate(event1);
        evaluator.evaluate(event1);
        evaluator.evaluate(new LoggingEvent());

        Search metrics = registry.find("logback.throttle");
        List<String> actual = metrics.counters().stream().map(x -> {
            StringBuilder builder = new StringBuilder("{");
            x.getId().getTags().stream().sorted(Comparator.comparing(Tag::getKey)).forEach(tag -> {
                if (builder.length() > 1) {
                    builder.append(", ");
                }
                builder.append(tag.getKey()).append("=").append(tag.getValue());
            });
            builder.append("}: ");
            builder.append(x.count());
            return builder.toString();
        }).sorted().toList();

        assertEquals(List.of("{ex=(none), logger=(none), name=(none), skipped=y}: 1.0",
                "{ex=java.lang.IllegalArgumentException, logger=logger-1, name=(none), skipped=n}: 1.0",
                "{ex=java.lang.IllegalArgumentException, logger=logger-1, name=(none), skipped=y}: 1.0"), actual);
    }

    @Test
    void throttlerShouldNotReportMetricsWhenDisabled() throws EvaluationException {
        MeterRegistry registry = new SimpleMeterRegistry();
        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(Clock.systemUTC(), registry);
        evaluator.setCollectMetrics(false);

        evaluator.evaluate(new LoggingEvent());

        Search metrics = registry.find("logback.throttle");
        assertTrue(metrics.counters().isEmpty(), "ThrottlingEvaluator should not report metrics when disabled");
    }

    @Test
    void throttlerShouldUseCompositeKeyForThrottling() throws EvaluationException {
        // Arrange
        TestClock clock = new TestClock();

        // Same logger, same exception type - should share quota
        LoggingEvent event1 = new LoggingEvent();
        event1.setLoggerName("logger-1");
        event1.setThrowableProxy(new ThrowableProxy(createExceptionFromMethod1()));

        LoggingEvent event2 = new LoggingEvent();
        event2.setLoggerName("logger-1");
        event2.setThrowableProxy(new ThrowableProxy(createExceptionFromMethod1()));

        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(clock, Metrics.globalRegistry);
        evaluator.setPerLoggerPeriod(10_000);
        evaluator.setPerLoggerLimit(1);

        // Act and assert
        boolean actual = evaluator.evaluate(event1);
        assertFalse(actual, "First exception should pass");

        actual = evaluator.evaluate(event2);
        assertTrue(actual, "Second exception should be throttled (same composite key: loggerName|exceptionClassName)");
    }

    @Test
    void throttlerShouldDistinguishDifferentExceptionTypesFromSameLogger() throws EvaluationException {
        // Arrange
        TestClock clock = new TestClock();
        String loggerClassName = "com.example.SomeClass";

        // Same logger, different exception types - should get separate quotas
        NullPointerException exception1 = new NullPointerException("test");
        IllegalArgumentException exception2 = new IllegalArgumentException("test");

        LoggingEvent event1 = new LoggingEvent();
        event1.setLoggerName(loggerClassName);
        event1.setThrowableProxy(new ThrowableProxy(exception1));

        LoggingEvent event2 = new LoggingEvent();
        event2.setLoggerName(loggerClassName);
        event2.setThrowableProxy(new ThrowableProxy(exception2));

        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(clock, Metrics.globalRegistry);
        evaluator.setPerLoggerPeriod(10_000);
        evaluator.setPerLoggerLimit(1);

        // Act and assert
        // First exception should pass
        boolean actual = evaluator.evaluate(event1);
        assertFalse(actual, "First NullPointerException should pass");

        // Different exception type should also pass (different key: loggerName|exceptionClassName)
        actual = evaluator.evaluate(event2);
        assertFalse(actual, "First IllegalArgumentException should pass (different exception type, so different key)");

        // Second event of same type should be throttled (same key)
        actual = evaluator.evaluate(event1);
        assertTrue(actual, "Second NullPointerException should be throttled (same key: loggerName|exceptionClassName)");
    }

    @Test
    void throttlerShouldHandleEventWithoutThrowableProxy() throws EvaluationException {
        TestClock clock = new TestClock();

        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("logger-1");

        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(clock, Metrics.globalRegistry);
        evaluator.setPerLoggerPeriod(10_000);
        evaluator.setPerLoggerLimit(1);

        // Act and assert
        boolean actual = evaluator.evaluate(event);
        assertFalse(actual, "Event without throwable proxy should pass");

        // Second event should be throttled (same composite key: logger-1|(none))
        actual = evaluator.evaluate(event);
        assertTrue(actual, "Second event without throwable proxy should be throttled");
    }

    @Test
    void throttlerShouldHandleExceptionWithEmptyStackTrace() throws EvaluationException {
        TestClock clock = new TestClock();

        // Create exception with empty stack trace
        NullPointerException exception = new NullPointerException("test");
        exception.setStackTrace(new StackTraceElement[0]); // Empty stack trace

        LoggingEvent event1 = new LoggingEvent();
        event1.setLoggerName("logger-1");
        event1.setThrowableProxy(new ThrowableProxy(exception));

        LoggingEvent event2 = new LoggingEvent();
        event2.setLoggerName("logger-1");
        event2.setThrowableProxy(new ThrowableProxy(exception));

        ThrottlingEvaluator evaluator = new ThrottlingEvaluator(clock, Metrics.globalRegistry);
        evaluator.setPerLoggerPeriod(10_000);
        evaluator.setPerLoggerLimit(1);

        // Act and assert
        boolean actual = evaluator.evaluate(event1);
        assertFalse(actual, "Event with empty stack trace should pass");

        // Second event should be throttled (same composite key: logger-1|NullPointerException)
        actual = evaluator.evaluate(event2);
        assertTrue(actual, "Second event with empty stack trace should be throttled");
    }

    // Helper methods to create exceptions from different locations
    private NullPointerException createExceptionFromMethod1() {
        try {
            throw new NullPointerException("test from method1");
        } catch (NullPointerException e) {
            return e;
        }
    }

    private IllegalArgumentException createIllegalArgumentExceptionFromMethod1() {
        try {
            throw new IllegalArgumentException("test from method1");
        } catch (IllegalArgumentException e) {
            return e;
        }
    }
}