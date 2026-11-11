package org.artifactory.logging.throttle;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.boolex.EvaluationException;
import ch.qos.logback.core.boolex.EventEvaluatorBase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.Getter;
import lombok.Setter;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A custom Logback evaluator, controlling the rate limit of log events, allowing up to the <code>limit</code>
 * events per given period of time.  For example, not more than 100 messages in an hour. The ThrottlingEvaluator
 * supports two different sets of limits: the global one and per-logger name.
 * <p>
 * Sample usage (in the logback.xml):
 * <pre>
 * {@code
 *     <evaluator class="org.artifactory.logging.throttle.ThrottlingEvaluator" name="THROTTLE_EXCEPTIONS">
 *         <!-- Global rate limit: not more than 20 stack trace dumps per 10 minutes -->
 *         <globalPeriod>${log.exceptions.throttle.global.period:-600000}</globalPeriod>
 *         <globalLimit>${log.exceptions.throttle.global.limit:-20}</globalLimit>
 *         <!-- Per-logger rate limit: not more than 5 stack trace dumps per hour (per logger) -->
 *         <perLoggerPeriod>${log.exceptions.throttle.per-logger.period:-3600000}</perLoggerPeriod>
 *         <perLoggerLimit>${log.exceptions.throttle.per-logger.limit:-5}</perLoggerLimit>
 *     </evaluator>
 *
 *     <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
 *         <encoder>
 *             <pattern>[%thread] - %msg%n%ex{full,THROTTLE_EXCEPTIONS}</pattern>
 *         </encoder>
 *     </appender>
 * }
 * </pre>
 * </p>
 * <p>
 *     This class reports the number of evaluated log events as a {@code logback_throttle_total} metric, having
 *     the following labels:
 *     <ul>
 *         <li><<b>name</b> - the name of the evaluator ("THROTTLE_EXCEPTIONS" in the example above)/li>
 *         <li><<b>logger</b> - the name of the logger, reported the event/li>
 *         <li><<b>ex</b> - the fully qualified class of the exception associated with the log event (or "(none)")
 *         <li><<b>skipped</b> - "y" if event was throttled, "n" if event was reported
 *     </ul>
 * </p>
 * @author Sergey Steinvil
 */
public class ThrottlingEvaluator extends EventEvaluatorBase<ILoggingEvent> {

    private static final String NONE = "(none)";
    protected final Clock clock;
    protected MeterRegistry metrics;
    protected final AtomicLong globalPeriodEndTime = new AtomicLong();
    protected final AtomicLong globalCount = new AtomicLong();
    protected final AtomicLong perLoggerPeriodEndTime = new AtomicLong();
    protected final Map<String, Long> perLoggerCounters = new ConcurrentHashMap<>();
    @Getter @Setter
    private long globalPeriod = 60_000; // one minute
    @Getter @Setter
    private long globalLimit = -1; // not more than <limit> stack traces in <period> msec. Negative means for "no limit"
    @Getter @Setter
    private long perLoggerPeriod = 3_600_000; // one hour
    @Getter @Setter
    private long perLoggerLimit = -1;
    @Getter @Setter
    private boolean collectMetrics = true;

    public ThrottlingEvaluator() {
        this(Clock.systemUTC(), Metrics.globalRegistry);
    }

    public ThrottlingEvaluator(Clock clock, MeterRegistry metrics) {
        this.clock = clock;
        this.metrics = metrics;
    }

    @Override
    public boolean evaluate(ILoggingEvent event) throws NullPointerException, EvaluationException {
        boolean throttle = checkShouldThrottle(event);
        if (this.collectMetrics) {
            reportEventToMetrics(event, throttle);
        }
        return throttle;
    }

    protected boolean checkShouldThrottle(ILoggingEvent event) {
        return incrementAndCheckGlobalThrottleLimit() || incrementAndCheckPerLoggerThrottleLimit(event);
    }

    protected boolean incrementAndCheckGlobalThrottleLimit() {
        if (this.globalLimit < 0) {
            return false; // No limit - print all stack traces to logs
        }
        if (this.globalLimit == 0) {
            return true; // Disable printing stack traces
        }

        long currentTime = this.clock.millis();
        if (this.globalPeriodEndTime.get() < currentTime) {
            // Start a new time window
            this.globalPeriodEndTime.set(currentTime + this.globalPeriod);
            this.globalCount.set(1);
            return false;
        }

        // We are still within the previously started time window
        // Increment the counter and throttle if limit reached.
        return this.globalCount.incrementAndGet() > this.globalLimit;
    }

    protected boolean incrementAndCheckPerLoggerThrottleLimit(ILoggingEvent event) {
        if (this.perLoggerLimit < 0) {
            return false; // No limit - allow everything
        }
        if (this.perLoggerLimit == 0) {
            return true; // Deny everything
        }

        String loggerName = computeLoggerName(event);

        long currentTime = this.clock.millis();
        if (this.perLoggerPeriodEndTime.get() < currentTime) {
            // Start a new time window
            this.perLoggerPeriodEndTime.set(currentTime + this.perLoggerPeriod);
            this.perLoggerCounters.clear();
            this.perLoggerCounters.put(loggerName, 1L);
            return false;
        }

        // We are still within the previously started time window
        // Increment the counter and throttle if limit reached.
        long newCounter = this.perLoggerCounters.compute(loggerName, (key, old) -> old == null ? 1 : old + 1);
        return newCounter > this.perLoggerLimit;
    }

    protected void reportEventToMetrics(ILoggingEvent event, boolean throttled) {
        Counter.builder("logback.throttle")
                .description("Number of log events that were processed by the rate limiting evaluator")
                .tags(
                        "name", valueOrNone(this.getName()),
                        "logger", valueOrNone(event.getLoggerName()),
                        "ex", valueOrNone(
                                event.getThrowableProxy() == null ? null : event.getThrowableProxy().getClassName()
                        ),
                        "skipped", throttled ? "y" : "n"
                )
                .register(this.metrics)
                .increment();
    }

    private String computeLoggerName(ILoggingEvent event) {
        String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
        String exceptionClassName = exceptionClassName(event);
        return loggerName + "|" + exceptionClassName;
    }

    private String exceptionClassName(ILoggingEvent event) {
        return event.getThrowableProxy() == null ? NONE : event.getThrowableProxy().getClassName();
    }

    static String valueOrNone(String value) {
        return value == null || value.isEmpty() ? NONE : value;
    }
}
