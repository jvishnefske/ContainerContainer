/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.workqueue;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.schedule.FullExecutorQueueException;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.core.task.TaskRejectedException;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A work queue that eliminates jobs based on a passed criteria.<p>
 * The thread offering work for this queue might start working on the queue, and may work on all the queued items.
 *
 * @author Yossi Shaul
 */
public class WorkQueueImpl<T extends WorkItem> implements WorkQueue<T> {
    private static final Logger log = LoggerFactory.getLogger(WorkQueueImpl.class);

    private final NonBlockingOnWriteQueue<T> queue;

    private final String key;
    private volatile int workers;
    private final Semaphore semaphore;
    private final int maxExecTimeSec;
    private final int maxWaitForWorkItemSec;
    private final AsyncExecutor executor;


    /**
     * Creates a new work queue with the given max workers.<p>
     * If the max workers is greater than one, the provider work executor must be thread safe.
     *
     * @param key                   The key of the work queue
     * @param workers               Maximum workers allowed to work on this queue
     * @param executor              The executor
     * @param conflictsGuard        custom conflicts guard
     * @param maxExecTimeSec        max sec of iterating over work items before execute in a new task
     * @param maxWaitForWorkItemSec the max sec to wait for blocking execution
     */
    public WorkQueueImpl(String key, int workers,
            @Nonnull AsyncExecutor executor,
            @Nonnull ConflictsGuard<String> conflictsGuard,
            int maxExecTimeSec, int maxWaitForWorkItemSec) {
        this(key, workers, executor, new NonBlockingOnWriteQueue<>(conflictsGuard),
                new Semaphore(workers), maxExecTimeSec, maxWaitForWorkItemSec);
    }


    @VisibleForTesting
    protected WorkQueueImpl(WorkQueueImpl workQueue) {
        this(workQueue.getKey(), workQueue.getMaxNumberOfWorkers(),
                workQueue.executor(), workQueue.queue, workQueue.semaphore, workQueue.maxExecTimeSec,
                workQueue.maxWaitForWorkItemSec);
    }

    private WorkQueueImpl(String key, int workers, AsyncExecutor executor,
            NonBlockingOnWriteQueue queue, Semaphore semaphore, int maxExecTimeSec, int maxWaitForWorkItemSec) {
        this.key = key;
        this.workers = workers;
        this.semaphore = semaphore;
        this.queue = queue;
        this.executor = executor;
        this.maxExecTimeSec = maxExecTimeSec;
        this.maxWaitForWorkItemSec = maxWaitForWorkItemSec;
    }

    @Override
    public boolean offerWork(T workItem, Runnable runnable) {
        log.trace("adding {}: to '{}'", workItem, key);
        boolean added = queue.addToPending(workItem, runnable);
        if (!added) {
            log.trace("{}: already contains '{}'", key, workItem);
        } else {
            workItem.setAddedToQueueTimestamp(System.currentTimeMillis());
            log.trace("{}: successfully added to '{}'", workItem, key);
        }
        return added;
    }

    @Override
    public int availablePermits() {
        return semaphore.availablePermits();
    }


    @Override
    public boolean execute(@NotNull T workItem, @NotNull Runnable runnable) {
        checkArgument(workItem != null, "workItem should not be null");
        checkArgument(runnable != null, "runnable should not be null");

        if (!offerWork(workItem, runnable)) {
            return false;
        }

        execute();
        return true;
    }

    @Override
    public void execute() {
        // Limit number of workers by semaphore
        // Submit only can acquire, to avoid executor overflow
        if (tryAcquire()) {
            try {
                executor.execute(this::doJobs);
            }
            catch (TaskRejectedException e) {
                release();
                Throwable cause = e.getCause();
                while (cause != null && !(cause instanceof FullExecutorQueueException)) {
                    cause = cause.getCause();
                }
                if (cause != null) {
                    log.debug("Work queue {} is full, cannot execute task", getKey());
                } else {
                    log.error("Failed to execute work queue {} task", getKey(), e);
                }
            }
            catch (RejectedExecutionException e) {
                release();
                log.error("Failed to execute work queue {} task", getKey(), e);
            }
        } else {
            log.debug("{}: max workers already processing ({})", getKey(), getMaxNumberOfWorkers());
        }
    }

    @VisibleForTesting
    protected boolean tryAcquire() {
        log.trace("Acquiring semaphore: {}", getKey());
        return semaphore.tryAcquire();
    }

    @VisibleForTesting
    protected void release() {
        log.trace("Releasing semaphore: {}", getKey());
        semaphore.release();
    }

    @VisibleForTesting
    protected void doJobs() {
        boolean shouldPromote = true;
        try {
            debugStep("start processing");
            WorkQueuePromotedItem<T> promotedWorkItem;
            Instant start = Instant.now();

            while ((shouldPromote = shouldPromote(start)) && (promotedWorkItem = queue.promote()) != null) {
                invoke(promotedWorkItem);
            }
        } catch (Exception e) {
            throw error(e);
        } finally {
            release();
            //if promotion stopped and has more items in the queue than re-submit a task
            if (!shouldPromote && getQueueSize() > 0) {
                log.debug("Work queue {} is running more than {} sec, stop and re-submit", key, maxExecTimeSec);
                execute();
            }
            debugStep("end processing");
        }
    }

    /**
     * We don't want to let work queue run for long time if there are pending tasks to execution.
     * Should promote if duration is less than ConstantValues.workQueueMaxExecutionSec or NO pending tasks
     */
    @VisibleForTesting
    boolean shouldPromote(Instant start) {
        return Duration.between(start, Instant.now()).toSeconds() <= maxExecTimeSec ||
                executor.getPendingTasks() <= 0;
    }

    private RuntimeException error(Exception exception) {
        String exErr = "Error occurred while promoting queue items";
        String logErr = exErr + ": {}";
        log.warn(logErr, exception.getMessage());
        log.debug(logErr, exception.getMessage(), exception);
        throw new WorkQueueException(exErr, exception);
    }

    private void invoke(WorkQueuePromotedItem<T> promotedWorkItem) {
        T workItem = promotedWorkItem.getWorkItem();
        traceStep("started working", workItem);
        try {
            promotedWorkItem.getRunnable().run();
            traceStep("finished working", workItem);
        } catch (Exception e) {
            String rootCause = e.getMessage();
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                rootCause = e.getCause().getMessage();
            }
            String msg = "Failed to call work queue: {}, work item: {}, callback: {}, error: {}";
            log.warn(msg, key, workItem, promotedWorkItem.getRunnable().getClass(), rootCause, e);
        } finally {
            boolean removed = queue.remove(promotedWorkItem);
            if (removed) {
                traceStep("successfully removed", workItem);
            } else {
                log.error("unexpected state: failed to remove {} from {}. Queue size pending={} running={}",
                        workItem, key, queue.getQueueSize(), queue.getRunningSize());
            }
        }
    }

    private void debugStep(String stepName) {
        // queue size is expensive, and returns current running tasks from other nodes as well in HA.
        if (log.isDebugEnabled()) {
            log.debug("{}: {}. Queue size pending={} running={}", key, stepName, queue.getQueueSize(),
                    queue.getRunningSize());
        }
    }

    private void traceStep(String stepName, T workItem) {
        // queue size is expensive, and returns current running tasks from other nodes as well in HA.
        if (log.isTraceEnabled()) {
            log.trace("{}: {} on {}. Queue size pending={} running={}", key, stepName, workItem, queue.getQueueSize(),
                    queue.getRunningSize());
        }
    }

    @Override
    public void stopQueue() {
        queue.stop();
    }

    @Override
    public int getQueueSize() {
        return queue.getQueueSize();
    }

    @Override
    public int getMaxNumberOfWorkers() {
        return workers;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void waitForItemDone(T workItem) {
        if (queue.contains(workItem)) {
            // Pending or running => Wait on work item
            synchronized (workItem) {
                if (queue.contains(workItem)) {
                    try {
                        workItem.wait(TimeUnit.SECONDS.toMillis(maxWaitForWorkItemSec));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new WorkQueueException(
                                "Work Item " + workItem + " did not finished completion in " + maxWaitForWorkItemSec +
                                        " sec", e);
                    }
                }
            }
        }
    }

    @Override
    public void updateWorkers(int newWorkers) throws InterruptedException {
        if (newWorkers != workers) {
            log.info("Updating {} workers: {} -> {}", key, workers, newWorkers);
            if (newWorkers > workers) {
                semaphore.release(newWorkers - workers);
            } else {
                // if newWorkers < workers
                // reduce permits one by one, so we will not have to wait for all if the reduction count is used.
                for (int index = 1; index <= workers - newWorkers; index++) {
                    semaphore.acquire();
                }
            }
            workers = newWorkers;
        }
    }

    @Override
    @Nonnull
    public AsyncExecutor executor() {
        return executor;
    }
}
