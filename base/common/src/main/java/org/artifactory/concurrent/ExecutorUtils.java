package org.artifactory.concurrent;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@UtilityClass
public class ExecutorUtils {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    /**
     * Submits a task to the given executor, awaits its completion within the specified timeout, and then shuts down the
     * executor. If the task fails (timeout, interruption, or execution exception), the optional {@code onFailure}
     * callback is invoked before shutdown.
     *
     * @param executor  the executor service to submit to and shut down afterwards
     * @param task      the task to execute
     * @param timeout   the maximum time to wait for the task to complete
     * @param unit      the time unit for the timeout
     * @param onFailure optional callback invoked when the task does not complete successfully (may be {@code null})
     */
    public void submitAndAwait(ExecutorService executor, Runnable task, long timeout, TimeUnit unit,
            @Nullable Runnable onFailure) {
        Future<?> future = null;
        try {
            future = executor.submit(task);
            future.get(timeout, unit);
        } catch (TimeoutException e) {
            log.warn("Submitted task did not complete within {} {}", timeout, unit);
            cancelAndRunOnFailure(future, onFailure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while awaiting submitted task");
            cancelAndRunOnFailure(future, onFailure);
        } catch (ExecutionException e) {
            log.warn("Submitted task failed", e);
            runOnFailure(onFailure);
        } finally {
            shutdownWithin(executor, SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Gracefully shuts down an {@link ExecutorService} within the given timeout. If the executor does not terminate
     * within the timeout, it is forcibly shut down via {@link ExecutorService#shutdownNow()}.
     *
     * @param executor the executor service to shut down
     * @param timeout  the maximum time to wait for termination
     * @param unit     the time unit for the timeout
     */
    public void shutdownWithin(ExecutorService executor, long timeout, TimeUnit unit) {
        shutdownWithin(executor, timeout, unit, null);
    }

    /**
     * Gracefully shuts down an {@link ExecutorService} within the given timeout. If the executor does not terminate
     * within the timeout, it is forcibly shut down via {@link ExecutorService#shutdownNow()}.
     * <p>
     * An optional cleanup action can be provided to run after the shutdown attempt, regardless of outcome.
     *
     * @param executor the executor service to shut down
     * @param timeout  the maximum time to wait for termination
     * @param unit     the time unit for the timeout
     * @param cleanup  optional cleanup action to run after shutdown (may be {@code null})
     */
    public void shutdownWithin(ExecutorService executor, long timeout, TimeUnit unit,
            @Nullable Runnable cleanup) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(timeout, unit)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } finally {
            if (cleanup != null) {
                cleanup.run();
            }
        }
    }

    private void cancelAndRunOnFailure(@Nullable Future<?> future, @Nullable Runnable onFailure) {
        if (future != null) {
            future.cancel(true);
        }
        runOnFailure(onFailure);
    }

    private void runOnFailure(@Nullable Runnable onFailure) {
        if (onFailure != null) {
            onFailure.run();
        }
    }
}
