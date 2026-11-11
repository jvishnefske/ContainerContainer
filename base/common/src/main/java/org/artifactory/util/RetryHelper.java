package org.artifactory.util;

import lombok.Builder;
import lombok.NonNull;
import org.slf4j.Logger;

import java.util.function.BooleanSupplier;

/**
 * A generic retry executor that repeatedly runs an operation until a stop condition is met
 * or the maximum number of retries is exhausted.
 * <p>
 * Exceptions thrown by the operation or the stop condition are caught and logged,
 * and the retry loop continues. This makes the helper resilient to transient failures.
 * <p>
 * Usage:
 * <pre>
 * boolean success = RetryHelper.builder()
 *         .maxRetries(10)
 *         .retryIntervalMillis(1000)
 *         .operation(() -> service.cleanup(key))
 *         .stopCondition(() -> service.isEmpty(key))
 *         .logger(log)
 *         .description("Cleanup for '" + key + "'")
 *         .build()
 *         .execute();
 * </pre>
 */
@Builder
public class RetryHelper {

    private final int maxRetries;
    private final long retryIntervalMillis;
    @NonNull
    private final Runnable operation;
    @NonNull
    private final BooleanSupplier stopCondition;
    private final Logger logger;
    private final String description;

    /**
     * Executes the operation with retries. Exceptions thrown by the operation or the stop condition
     * are caught and logged, allowing subsequent retry attempts to proceed.
     *
     * @return true if the stop condition was met within the allowed retries, false otherwise
     */
    public boolean execute() {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            runOperation(attempt);
            if (isStopConditionMet(attempt)) {
                return true;
            }
            if (attempt < maxRetries && !sleepBeforeNextAttempt()) {
                return false;
            }
        }
        warn("{} failed after {} attempts.", description, maxRetries);
        return false;
    }

    private boolean isStopConditionMet(int attempt) {
        try {
            if (stopCondition.getAsBoolean()) {
                log("{} completed successfully.", description);
                return true;
            }
        } catch (Exception e) {
            warn("{} attempt {}/{} stop condition threw an exception: {}",
                    description, attempt, maxRetries, e.getMessage(), e);
        }
        return false;
    }

    private void runOperation(int attempt) {
        try {
            operation.run();
            log("{} attempt {}/{}.", description, attempt, maxRetries);
        } catch (Exception e) {
            warn("{} attempt {}/{} operation threw an exception: {}",
                    description, attempt, maxRetries, e.getMessage(), e);
        }
    }

    private void log(String format, Object... args) {
        if (logger != null && description != null) {
            logger.info(format, args);
        }
    }

    private void warn(String format, Object... args) {
        if (logger != null && description != null) {
            logger.warn(format, args);
        }
    }

    /**
     * @return true if sleep completed normally, false if interrupted
     */
    private boolean sleepBeforeNextAttempt() {
        try {
            Thread.sleep(retryIntervalMillis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warn("{} interrupted, aborting retries.", description);
            return false;
        }
    }

    @SuppressWarnings("unused")
    /*
     * Customizes the Lombok-generated builder to add fail-fast validation for numeric fields.
     * Lombok detects this partial class and merges its generated setter methods into it.
     */
    public static class RetryHelperBuilder {
        public RetryHelper build() {
            if (maxRetries <= 0) {
                throw new IllegalArgumentException("maxRetries must be positive");
            }
            if (retryIntervalMillis < 0) {
                throw new IllegalArgumentException(
                        "retryIntervalMillis must be non-negative");
            }
            return new RetryHelper(
                    maxRetries, retryIntervalMillis, operation,
                    stopCondition, logger, description);
        }
    }
}
