package org.artifactory.util;

import lombok.SneakyThrows;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.concurrent.Callable;

/**
 * Utility class allows to execute given code only once every {@link #interval} times
 * @author Mor Merhav
 */
@NotThreadSafe
public final class CounterBasedRateLimitUtil {

    private final long interval;
    private long iteration;

    public CounterBasedRateLimitUtil(long interval) {
        this.interval = interval;
        this.iteration = interval; // First execution should not be skipped
    }

    @SneakyThrows
    public void executeWithRateLimit(Runnable runnable) {
        iteration++;
        if (iteration > interval) {
            iteration = 0;
            runnable.run();
        }
    }

    @SneakyThrows
    public <T> T callWithRateLimit(Callable<T> callable, T defaultValue) {
        iteration++;
        if (iteration > interval) {
            iteration = 0;
            return callable.call();
        }
        return defaultValue;
    }
}
