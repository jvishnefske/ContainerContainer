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
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotNull;
import org.artifactory.async.AsyncCustomExecutorInfo;
import org.artifactory.async.AsyncExecutor;
import org.artifactory.async.CustomExecutor;
import org.artifactory.mbean.MBeanRegistrationService;
import org.artifactory.workqueue.mbean.WorkQueueWrapper;
import org.artifactory.workqueue.mbean.WorkQueueWrapperMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author gidis
 */

public class AsyncWorkQueueServiceImpl implements AsyncWorkQueueService {
    private static final Logger log = LoggerFactory.getLogger(AsyncWorkQueueServiceImpl.class);

    private final ConcurrentHashMap<String, WorkQueue<WorkItem>> workQueues = new ConcurrentHashMap<>();
    private final Map<String, WorkQueueInfo> workQueueInfos = new HashMap<>();
    private final MBeanRegistrationService mBeanRegistrationService;
    private final AsyncExecutor defaultExecutor;
    private final int maxExecTimeSec;
    private final int maxWaitForWorkItemSec;
    private final List<RemoveWorkQueuePredicate> removeWorkQueuePredicates;
    private final ScheduledExecutorService workQueueJobScheduler;
    private final int workQueueJobIntervalSec;
    private final WorkQueueSessionService workQueueSessionService;
    private final boolean startCleanupJob;
    private ScheduledFuture<?> workQueueJob;
    private List<WorkQueueCustomizer<?>> workQueueCustomizers;

    /**
     * @param workQueueSessionService   a service that manage session callbacks
     * @param workQueueInfos            list of work queue infos as configurations for the work queue
     * @param defaultExecutor           the default executor to use
     * @param removeWorkQueuePredicates a predicate to use at the work queue job to detect work queues to delete
     * @param mBeanRegistrationService  mbean registration service to register create work queues
     * @param maxExecTimeSec            the max time for each execution
     * @param maxWaitForWorkItemSec     the max time to wait for item execution completion
     * @param workQueueJobIntervalSec   the interval to run the work queue job
     */
    public AsyncWorkQueueServiceImpl(WorkQueueSessionService workQueueSessionService,
            @NotNull List<WorkQueueInfo> workQueueInfos,
            @Nonnull AsyncExecutor defaultExecutor,
            @Nullable List<RemoveWorkQueuePredicate> removeWorkQueuePredicates,
            @Nonnull MBeanRegistrationService mBeanRegistrationService,
            int maxExecTimeSec, int maxWaitForWorkItemSec, int workQueueJobIntervalSec,
            List<WorkQueueCustomizer<?>> workQueueCustomizers, boolean startCleanupJob) {

        this.workQueueSessionService = workQueueSessionService;
        this.startCleanupJob = startCleanupJob;
        workQueueInfos.forEach(workQueueInfo -> {
            if (this.workQueueInfos.containsKey(workQueueInfo.getName())) {
                throw new IllegalArgumentException("None unique work queue name: " + workQueueInfo.getName());
            }
            this.workQueueInfos.put(workQueueInfo.getName(), workQueueInfo);
        });
        this.defaultExecutor = defaultExecutor;
        this.removeWorkQueuePredicates = removeWorkQueuePredicates;
        this.mBeanRegistrationService = mBeanRegistrationService;
        this.maxExecTimeSec = maxExecTimeSec;
        this.maxWaitForWorkItemSec = maxWaitForWorkItemSec;
        this.workQueueJobIntervalSec = workQueueJobIntervalSec;

        if (workQueueJobIntervalSec > 0) {
            this.workQueueJobScheduler = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder()
                    .setDaemon(true)
                    .setNameFormat("WorkQueueJob").build());
        } else {
            this.workQueueJobScheduler = null;
        }
        this.workQueueCustomizers = workQueueCustomizers;
    }

    @Override
    public void scheduleWorkQueueCleanupJob(WorkQueueJobDecorator decorator) {
        synchronized (this) {
            if (workQueueJobScheduler == null) {
                throw new RuntimeException("WorkQueueJob was not configured");
            } else if (workQueueJob != null) {
                throw new RuntimeException("WorkQueueJob is already running");
            }
            if (this.startCleanupJob) {
                this.workQueueJob = this.workQueueJobScheduler.scheduleWithFixedDelay(decorator.decorate(this::clean),
                        workQueueJobIntervalSec, workQueueJobIntervalSec, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void execute(@NotNull String workQueueName, @NotNull WorkItem workItem, @NotNull Runnable runnable,
            Boolean wait4Done) {
        execute(workQueueName, workItem, runnable, wait4Done, false, false);
    }

    @Override
    public void execute(@NotNull String workQueueName, @NotNull WorkItem workItem, @NotNull Runnable runnable,
            boolean delayAfterCommit, boolean failIfNoSession) {
        execute(workQueueName, workItem, runnable, false, delayAfterCommit, failIfNoSession);
    }

    @Override
    public void execute(@NotNull String workQueueName, @NotNull WorkItem workItem, @NotNull Runnable runnable,
            boolean wait4Done, boolean delayAfterCommit, boolean failIfNoSession) {
        checkArgument(workQueueName != null, "workQueueName should not be null");
        checkArgument(workItem != null, "workItem should not be null");
        checkArgument(runnable != null, "runnable should not be null");

        WorkQueue<WorkItem> workQueue = getWorkQueue(workQueueName);
        if (workQueue == null) {
            throw new IllegalArgumentException("No work queue for name: " + workQueueName);
        }

        // if delay after commit, register callback to in the work queue session service.
        // if there is no session the registration will return false, we should try to execute immediately
        // unless we should fail if no session, in that case work queue exception will be thrown
        // if session exist, the callback will be called after commit, we execute the work queue after commit
        // if node delay after commit, we execute the work queue immediately
        if (!(delayAfterCommit && workQueueSessionService.register(
                () -> executeAndWaitIfNeeded(workItem, runnable, wait4Done, workQueue)))) {

            if (delayAfterCommit && failIfNoSession) {
                throw new WorkQueueException(
                        "Session is required but does not exist: " + workQueueName + ", " + workItem.getUniqueKey());
            }

            executeAndWaitIfNeeded(workItem, runnable, wait4Done, workQueue);
        }
    }

    private void executeAndWaitIfNeeded(@NotNull WorkItem workItem, @NotNull Runnable runnable, boolean wait4Done,
            WorkQueue<WorkItem> workQueue) {
        if (!workQueue.execute(workItem, runnable)) {
            return;
        }

        if (wait4Done) {
            workQueue.waitForItemDone(workItem);
        }
    }

    @Override
    public void closeAllQueues() {
        for (Map.Entry<String, WorkQueue<WorkItem>> queueEntry : this.workQueues.entrySet()) {
            WorkQueue<WorkItem> workQueue = queueEntry.getValue();
            try {
                workQueue.stopQueue();
            } catch (Exception e) {
                log.error("Failed to close queue " + queueEntry.getKey(), e);
            }
        }
    }

    @Override
    public int getEstimatedPendingTasksSize(String workQueueName) {
        WorkQueue<WorkItem> workQueue = this.workQueues.get(workQueueName);
        if (workQueue != null) {
            return workQueue.getQueueSize();
        }
        throw new IllegalArgumentException("No work queue matches name '" + workQueueName + "'");
    }

    @Override
    public @NotNull
    WorkQueue<WorkItem> getWorkQueue(String name) {
        WorkQueueInfo info = workQueueInfos.get(name);
        if (info == null) {
            throw new IllegalArgumentException("No info for work queue name: " + name);
        }

        String key = CustomExecutor.get() == null ? name : CustomExecutor.get().getCustomKey().apply(name);
        return this.workQueues.computeIfAbsent(key, k -> {
            WorkQueue<WorkItem> workQueue = createWorkQueue(info, k);
            try {
                WorkQueueWrapperMBean mBean = new WorkQueueWrapper(workQueue);
                mBeanRegistrationService.register(mBean, "Work Queue", workQueue.getKey());
            } catch (Exception e) {
                log.error("Failed to create/register mbean for work queue: {}", workQueue.getKey());
            }
            return workQueue;
        });
    }

    @Override
    public Map<String, WorkQueue<WorkItem>> getExistingWorkQueues() {
        return this.workQueues;
    }

    @PreDestroy
    public void destroy() {
        if (this.workQueueJobScheduler != null) {
            this.workQueueJobScheduler.shutdown();
            try {
                if (this.workQueueJobScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Work queue job await termination ended unsuccessfully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        closeAllQueues();
    }

    @Override
    public void clean() {
        try {
            removeWorkQueues();
            executeStuckItems();
        } catch (Exception e) {
            log.error("An error occurred while running WorkQueueJob: {}", e.getMessage(), e);
        }
    }

    @NotNull
    private WorkQueue<WorkItem> createWorkQueue(WorkQueueInfo info, String key) {
        AsyncExecutor executor =
                CustomExecutor.get() != null ? CustomExecutor.get().getExecutor() : defaultExecutor;

        WorkQueue workQueue = createWorkQueue(info, key, executor);
        for (WorkQueueCustomizer<?> workQueueCustomizer : workQueueCustomizers) {
            workQueue = workQueueCustomizer.customizeWorkQueue(workQueue);
        }
        return workQueue;
    }

    @NotNull
    @VisibleForTesting
    protected WorkQueueImpl<WorkItem> createWorkQueue(WorkQueueInfo info, String key, AsyncExecutor executor) {
        int queueMaxWait =
                info.getMaxWaitForWorkItemSec() == null ? maxWaitForWorkItemSec : info.getMaxWaitForWorkItemSec();
        return new WorkQueueImpl<>(key, getWorkers(CustomExecutor.get(), info), executor,
                info.getConflictsGuardProvider().get(key, info), maxExecTimeSec, queueMaxWait);
    }

    @VisibleForTesting
    void removeWorkQueues() {
        if (removeWorkQueuePredicates != null && !removeWorkQueuePredicates.isEmpty()) {
            //remove work queues using custom predicates
            workQueues.entrySet().removeIf(
                    entry -> {
                        for (RemoveWorkQueuePredicate removeWorkQueuePredicate : removeWorkQueuePredicates) {
                            if (removeWorkQueuePredicate.test(entry.getValue().getKey())) {
                                mBeanRegistrationService.unregister("Work Queue", entry.getValue().getKey());
                                log.info("Removing WorkQueue {}", entry.getValue().getKey());
                                return true;
                            }
                        }
                        return false;
                    });
        }
    }

    @VisibleForTesting
    void executeStuckItems() {
        workQueues.values().forEach(queue -> {
            //queue.availablePermits should never be bigger than getMaxNumberOfWorkers, unless if there is a
            //bug that we do release() more than we need. this is why >= and not ==
            if (queue.getQueueSize() > 0) {
                log.info("Executing {} stuck tasks: {}", queue.getKey(), queue.getQueueSize());
                queue.execute();
            }
        });
    }

    private int getWorkers(AsyncCustomExecutorInfo customExecutor, WorkQueueInfo info) {
        if (customExecutor != null && customExecutor.getWorkQueueWorkers().get() != null) {
            return customExecutor.getWorkQueueWorkers().get();
        }
        return info.getMaxNumberOfThreads();
    }

}
