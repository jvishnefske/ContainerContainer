package com.jfrog.build.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.build.api.Build;

import java.util.StringJoiner;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.time.Duration.ofNanos;
import static java.util.Optional.ofNullable;

@Slf4j
@UtilityClass
public class BuildPerformanceMonitor {

    /**
     * Made non-inheritable intentionally - use separate contexts per a thread.
     */
    private static final ThreadLocal<StringJoiner> CONTEXT = new ThreadLocal<>();

    public static void initOperationStats(
            @NonNull Build build,
            @NonNull String buildRepo) {
        CONTEXT.set(new StringJoiner("\n").add(format("Build performance stats for %s/%s/%s in repo: %s:",
                build.getName(), build.getNumber(), build.getStarted(), buildRepo)));
    }

    public static void logOperationStats(
            @NonNull String operation,
            @NonNull Long opStart) {
        execute(stats -> {
            long opMillis = ofNanos(nanoTime() - opStart).toMillis();
            stats.add(format("* %s took: %d ms", operation, opMillis));
        });
    }

    public static void printOperationStats() {
        execute(stats -> {
            log.info(stats.toString());
            CONTEXT.remove();
        });
    }

    private static void execute(@NonNull Consumer<StringJoiner> consumer) {
        ofNullable(CONTEXT.get()).ifPresent(consumer);
    }
}
