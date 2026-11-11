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

package org.artifactory.schedule;

import com.google.common.collect.Lists;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.request.GracefulShutdownAware;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.mbean.MBeanRegistrationServiceImpl;
import org.artifactory.schedule.mbean.ManagedExecutor;
import org.artifactory.schedule.quartz.QuartzTaskHistoryExpiredAndOrphanedMarkerJob;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.state.model.ArtifactoryStateManager;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.workqueue.AsyncWorkQueueService;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.jfrog.common.config.diff.DataDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;

import jakarta.annotation.Nonnull;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.artifactory.schedule.TaskUtils.DESC_MANUAL_TRIGGER;

/**
 * @author yoavl
 */
@Service
@Reloadable(beanClass = TaskService.class,
        initAfter = {DbService.class, InternalCentralConfigService.class, ArtifactoryStateManager.class},
        listenOn = CentralConfigKey.none)
public class TaskServiceImpl
        implements TaskService, ContextReadinessListener, TaskServiceCallBack, GracefulShutdownAware {
    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    private static final DateTimeFormatter BLOCKING_TASK_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Autowired
    private CachedThreadPoolTaskExecutor executor;
    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private MarkerDbLocksService markerDbLocksService;
    private QuartzConflictsGuard conflictsGuard;
    private final ConcurrentMap<String, InternalTask> activeTasksById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, InternalTask> inactiveTasksById = new ConcurrentHashMap<>();
    private long locksTimeoutSecs;

    @Override
    public void init() {
        java.util.concurrent.Executor concurrentExecutor = executor.getConcurrentExecutor();
        if (concurrentExecutor instanceof ArtifactoryConcurrentExecutor) {
            ContextHelper.get().beanForType(MBeanRegistrationServiceImpl.class).
                    register(new ManagedExecutor((ArtifactoryConcurrentExecutor) concurrentExecutor),
                            "Executor Pools", "Task Service");
        }

        conflictsGuard = new QuartzConflictsGuard(markerDbLocksService);

        try {
            long intervalMillis = TimeUnit.SECONDS.toMillis(
                    ConstantValues.quartzTaskHistoryExpiredAndOrphanedMarkerJobIntervalSecs.getLong());
            Task repeatingTask =
                    TaskUtils.createRepeatingTask(QuartzTaskHistoryExpiredAndOrphanedMarkerJob.class, intervalMillis,
                            0L);
            startTask(repeatingTask, false);
        } catch (Exception e) {
            log.error("Failed to schedule QuartzTaskHistoryExpiredAndOrphanedMarkerJob", e);
        }
    }

    @Override
    public void destroy() {
        ContextHelper.get().beanForType(AsyncWorkQueueService.class).closeAllQueues();
        cancelAllTasks(true);
        //Shut down the executor service to terminate any async operations not managed by the task service
        executor.destroy();
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor, List<DataDiff<?>> configDiff) {
    }

    @Override
    public void onContextReady() {
        locksTimeoutSecs = ConstantValues.locksTimeoutSecs.getLong();
        activateTasks();
    }

    @Override
    public Task startManualTask(Class<? extends QuartzCommand> quartzCommand, String externalId,
            MutableStatusHolder statusHolder, Map<String, Object> taskAttributesMap, int initialDelay,
            String description) {
        return startManualTask(quartzCommand, externalId, statusHolder, taskAttributesMap, initialDelay,
                description, false);
    }

    @Override
    public Task startManualTask(Class<? extends QuartzCommand> quartzCommand, String externalId,
            MutableStatusHolder statusHolder, Map<String, Object> taskAttributesMap, int initialDelay,
            String description, boolean propagateToAffinity) {
        description = description + DESC_MANUAL_TRIGGER;
        checkCanStartManualTask(quartzCommand, statusHolder, taskAttributesMap);

        Task task = null;
        if (!statusHolder.isError()) {
            task = TaskUtils.createManualTask(quartzCommand, initialDelay, externalId, description);
            taskAttributesMap.forEach(task::addAttribute);
            startTask(task, false, propagateToAffinity);
        }
        return task;
    }

    @Override
    @SuppressWarnings("java:S3516")
    public String startTask(Task task, boolean waitForRunning) {
        InternalTask internalTask = (InternalTask) task;
        String taskId = internalTask.getTaskId();
        ArtifactoryContext context = ContextHelper.get();
        if (context.isOffline()) {
            log.error("Artifactory is in offline state, task {} is not allowed to run during offline state!", taskId);
            return taskId;
        }
        // Verify can be started first
        canBeStarted(internalTask);
        boolean contextReady = context.isReady();
        ConcurrentMap<String, InternalTask> taskMap =
                contextReady ? activeTasksById : inactiveTasksById;
        if (internalTask.isSingleton()) {
            //Reject duplicate singleton tasks - by type + check automatically during insert that we are not
            //rescheduling the same task instance
            if (hasTaskOfType(internalTask.getType(), taskMap, false) ||
                    taskMap.putIfAbsent(taskId, internalTask) != null) {
                throw new SingletonTaskAlreadyScheduledException(internalTask);
            }
        } else if (taskMap.put(taskId, internalTask) != null) {
            log.warn("Overriding an active task with the same taskId: {}.", internalTask);
        }
        // Make sure to use the same ready state
        if (contextReady) {
            internalTask.schedule(waitForRunning);
        } else {
            // Recheck may be readiness was activated in between
            if (context.isReady()) {
                moveToActive(taskId);
            }
        }
        return taskId;
    }

    @Override
    public String startTask(Task task, boolean waitForRunning, boolean propagateToAffinity) {
        JobName jobName = (JobName) task.getAttribute(Task.JOB_NAME);
        boolean hardAffinity = (boolean) task.getAttribute(Task.HARD_AFFINITY);
        
        boolean isAllowedToRun = addonsManager.addonByType(HaAddon.class).isAllowedToRun(jobName, hardAffinity);
        log.debug("startTask: taskId={}, jobName={}, hardAffinity={}, propagate={}, allowed={}",
                task.getTaskId(), jobName, hardAffinity, propagateToAffinity, isAllowedToRun);

        if (propagateToAffinity && !isAllowedToRun) {
            log.debug("Propagating task {} to Affinity node", task.getTaskId());
            addonsManager.addonByType(HaAddon.class).propagateTaskToAvailableAffinityNode(task);
            return task.getTaskId();
        } else {
            log.debug("Running task {} locally (propagate={}, allowed={})",
                    task.getTaskId(), propagateToAffinity, isAllowedToRun);
            return startTask(task, waitForRunning);
        }
    }

    @Override
    public void stopTask(String taskId, boolean wait) {
        InternalTask task = getActiveInternalTask(activeTasksById.get(taskId));
        if (task != null) {
            task.stop(wait);
        } else {
            HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
            if (haAddon.isHaEnabled()) {
                stopHaTask(taskId, wait);
            } else {
                log.warn("Could not find task ID: {} to stop.", taskId);
            }
        }
    }

    @Override
    public void pauseTask(String taskId, boolean wait) {
        InternalTask task = getActiveInternalTask(taskId, false);
        if (task != null) {
            task.pause(wait);
        } else {
            HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
            if (haAddon.isHaEnabled()) {
                pauseHaTask(taskId, wait);
            } else {
                log.warn("Could not find task ID: {} to pause.", taskId);
            }
        }
    }

    /**
     * Checks if task is active, and if so delegates to {@link InternalTask#blockIfPausedAndShouldBreak()} to decide
     * whether pause or stop should be made
     *
     * @return true if task should stop, false otherwise
     */
    @Override
    public boolean pauseOrBreak() {
        String taskId = TaskCallback.currentTaskId();
        // If not in a task the taskId is null
        if (taskId == null) {
            //Since this is called by external clients, it may be called directly or in tests with no surrounding task
            log.debug("No current task is found on thread - nothing to block or pause.");
            return false;
        }
        InternalTask task = getActiveInternalTask(taskId, true);
        if (task == null) {
            log.warn("Could not find task {} to check block on.", taskId);
            return false;
        }
        return task.blockIfPausedAndShouldBreak();
    }

    @Override
    public List<String> stopTasks(Class<? extends TaskCallback> callbackType) {
        String currentTaskId = TaskCallback.currentTaskId();
        List<String> results = Lists.newArrayList();
        for (Task task : activeTasksById.values()) {
            // If callback type is null means all, cannot stop myself
            if ((callbackType == null || ClassUtils.isAssignable(callbackType, task.getType()))
                    && (currentTaskId == null || !task.getTaskId().equals(currentTaskId))) {
                //TODO: Don't wait on each job in a serial fashion
                stopTask(task.getTaskId(), true);
                results.add(task.getTaskId());
            }
        }
        return results;
    }

    @Override
    public boolean resumeTask(String taskId) {
        return resumeTask(taskId, null);
    }

    @Override
    public boolean resumeTask(String taskId, String resumingTaskId) {
        InternalTask task = getActiveInternalTask(taskId, false);
        if (task != null) {
            return task.resume(resumingTaskId);
        } else {
            HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
            if (haAddon.isHaEnabled()) {
                return resumeHaTask(taskId);
            } else {
                log.debug("Could not find task {} to resume.", taskId);
                return false;
            }
        }
    }


    @Override
    public void cancelTask(String taskId, boolean wait) {
        cancelTask(taskId, wait, locksTimeoutSecs);
    }

    @Override
    public void cancelTask(String taskId, boolean wait, long timeout) {
        InternalTask task = activeTasksById.get(taskId);
        if (task != null) {
            task.cancel(wait, timeout);
        } else {
            log.warn("Could not find task {} to cancel.", taskId);
        }
        activeTasksById.remove(taskId);
    }

    @Override
    public void cancelTasks(@Nonnull final Class<? extends TaskCallback> callbackType, boolean wait) {
        cancelTasks(TaskUtils.createPredicateForType(callbackType), wait);
    }

    @Override
    public void cancelAllTasks(boolean wait) {
        cancelTasks(input -> true, wait);
    }

    @Override
    public void rescheduleTask(String taskId) {
        InternalTask task = activeTasksById.get(taskId);
        if (task != null) {
            long waitBeforeExecute =
                    TimeUnit.SECONDS.toMillis(ConstantValues.taskRetryWaitBeforeRescheduleSecs.getInt());
            task.rescheduleTask(waitBeforeExecute);
        } else {
            log.warn("Could not find task {} to reschedule.", taskId);
        }
    }

    @Override
    public boolean waitForTaskCompletion(String taskId) {
        InternalTask task = getActiveInternalTask(taskId, false);
        //Check for null since task may have already been canceled
        return task == null || task.waitForCompletion(0);
    }

    @Override
    public Task getActiveTask(String taskId, boolean warnIfMissing) {
        if (taskId == null) {
            throw new IllegalArgumentException("Could not find task with null task ID");
        }

        Task task = activeTasksById.get(taskId);
        if (warnIfMissing && task == null) {
            log.warn("Could not locate active task with ID: {}. Task may have been canceled.", taskId);
        }
        return task;
    }

    InternalTask getActiveInternalTask(Task task) {
        if (task == null) {
            return null;
        }
        return activeTasksById.getOrDefault(task.getTaskId(), (InternalTask) task);
    }

    InternalTask getActiveInternalTask(String taskId, boolean warnIfMissing) {
        return (InternalTask) getActiveTask(taskId, warnIfMissing);
    }

    @Override
    public boolean hasTaskOfType(Class<? extends TaskCallback> callbackType) {
        return hasTaskOfType(callbackType, activeTasksById, true);
    }

    @Override
    public void cancelTasks(@Nonnull Predicate<Task> predicate, boolean wait) {
        List<Task> toCancel = getActiveTasks(predicate);
        for (Task task : toCancel) {
            //TODO: Don't wait on each job in a serial fashion
            cancelTask(task.getTaskId(), wait);
        }
    }

    @Override
    public List<Task> getActiveTasks(@Nonnull Predicate<Task> predicate) {
        return activeTasksById.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public boolean hasActiveTasks(@Nonnull Predicate<Task> predicate) {
        return activeTasksById.values().stream().anyMatch(predicate);
    }

    @Override
    public boolean canBeShutdown() {
        // The activeTasksById read below is weakly consistent, so a task inserted concurrently may be missed by a
        // single poll. This is tolerated because GracefulShutdownServiceImpl polls canBeShutdown() repeatedly during
        // the drain, and a later poll observes the task. Closing the residual window entirely (standing down the task
        // scheduler so no new job can start mid-drain) is tracked as a follow-up.
        List<Task> blockingTasks = getActiveTasks(this::isBlockingQuartzTask);
        if (!blockingTasks.isEmpty()) {
            log.warn("Graceful shutdown blocked by running Quartz task(s): {}",
                    blockingTasks.stream().map(this::formatBlockingTask).toList());
        }
        return blockingTasks.isEmpty();
    }

    private String formatBlockingTask(Task task) {
        return String.format("id=%s, name=%s, started=%s",
                task.getTaskId(),
                task.getTaskName(),
                BLOCKING_TASK_TIME_FMT.format(Instant.ofEpochMilli(task.getLastStarted())));
    }

    @Override
    public void checkCanStartManualTask(Task task, Class<? extends TaskCallback> typeToRun,
            MutableStatusHolder statusHolder, Object... keyValues) {
        JobCommand jobCommand = typeToRun.getAnnotation(JobCommand.class);
        if (!checkCanStartManualTask(typeToRun, statusHolder, jobCommand, keyValues))  {
            return;
        }

        InternalTask internalTask = getActiveInternalTask(task);
        if (internalTask.isClusterSingleton()) {
            final String lockKey =
                    internalTask.getTaskLockKeyIncludingKeyAttributes(Arrays.asList(jobCommand.keyAttributes()));
            ConflictGuard jobConflictGuard = getConflictsGuard().getLock(lockKey);
            if (jobConflictGuard.isLocked()) {
                statusHolder.error("Cannot start task (" + task + ") manually!" +
                        " The task lock-key (" + lockKey + ") is already locked.", HttpStatus.SC_CONFLICT, log);
            }
        }

        if (!statusHolder.isError()) {
            statusHolder.debug("Task " + typeToRun.getName() + " can run.", log);
        }
    }

    @Override
    public void checkCanStartManualTask(Class<? extends TaskCallback> typeToRun,
            MutableStatusHolder statusHolder, Map<String, Object> jobDataMap) {
        JobCommand jobCommand = typeToRun.getAnnotation(JobCommand.class);
        if (jobCommand == null) {
            statusHolder.error(
                    "Task type " + typeToRun.getName() + " does not have the " +
                            JobCommand.class.getName() + " annotation!", log);
            return;
        }
        String[] keyAttributes = jobCommand.keyAttributes();
        List<Object> attributeValues = TaskUtils.extractAttributeValues(jobDataMap, keyAttributes);
        if (!checkCanStartManualTask(typeToRun, statusHolder, jobCommand, attributeValues.toArray()))  {
            return;
        }

        if (jobCommand.clusterSingleton()) {
            final String lockKey =
                    TaskUtils.getTaskLockKeyIncludingKeyAttributes(jobCommand.jobName().asText(), jobDataMap,
                            Arrays.asList(keyAttributes));
            ConflictGuard jobConflictGuard = getConflictsGuard().getLock(lockKey);
            if (jobConflictGuard.isLocked()) {
                statusHolder.error("Cannot start task type (" + typeToRun + ") manually!" +
                        " The task lock-key (" + lockKey + ") is already locked.", HttpStatus.SC_CONFLICT, log);
            }
        }

        if (!statusHolder.isError()) {
            statusHolder.debug("Task " + typeToRun.getName() + " can run.", log);
        }
    }

    @Override
    public void stopRelatedTasks(Class<? extends TaskCallback> typeToRun, List<String> stoppedTasksIds,
            Object... keyValues) {
        // Extract task filters for each stop strategy
        EnumMap<StopStrategy, Predicate<Task>> predicatePerStrategy = getPredicatePerStrategy(typeToRun, keyValues);

        // Each predicates should not count myself :)
        Predicate<Task> notMyself = getNotMySelfPredicate();

        String currentTaskId = TaskCallback.currentTaskId();

        // Stop early if impossible to run
        Predicate<Task> impossibleFilter = predicatePerStrategy.get(StopStrategy.IMPOSSIBLE);
        if (impossibleFilter != null) {
            getActiveTasks(notMyself.and(impossibleFilter))
                    .forEach(activeTask -> {
                        InternalTask internalTask = (InternalTask) activeTask;
                        log.debug("Found IMPOSSIBLE task for {}. Task is: {}", typeToRun.getSimpleName(),
                                activeTask.getType().getSimpleName());
                        if (internalTask.processActiveOnCluster()) {
                            log.info("IMPOSSIBLE task process for {} is active: {}", typeToRun.getSimpleName(),
                                    activeTask.getType().getSimpleName());
                            throw new TaskImpossibleToStartException(
                                    "Job " + typeToRun.getSimpleName() + " cannot stop related job " +
                                            activeTask.getType().getSimpleName() + " while it's running!");
                        }
                        // Immediate stop fails if task in running mode
                        stoppedTasksIds.add(activeTask.getTaskId());
                        internalTask.stop(false, currentTaskId);
                    });
        }

        // Then stop what's needed
        Predicate<Task> stopFilter = predicatePerStrategy.get(StopStrategy.STOP);
        if (stopFilter != null) {
            getActiveInternalTasks(notMyself.and(stopFilter))
                    .forEach(activeTask -> {
                        // Just stop and keep for resume
                        log.debug("Found task to STOP for {}. Task ID to stop is: {}", typeToRun.getSimpleName(),
                                activeTask.getTaskId());
                        stoppedTasksIds.add(activeTask.getTaskId());
                        activeTask.stop(true, currentTaskId);
                    });
        }

        // Then pause what's needed
        Predicate<Task> pauseFilter = predicatePerStrategy.get(StopStrategy.PAUSE);
        if (pauseFilter != null) {
            getActiveTasks(notMyself.and(pauseFilter)).stream().map(InternalTask.class::cast)
                    .forEach(activeTask -> {
                        // Just stop and always keep for resume, since even single execution paused should be resumed
                        log.debug("Found task to PAUSE for {}. Task ID to pause is: {}", typeToRun.getSimpleName(),
                                activeTask.getTaskId());
                        stoppedTasksIds.add(activeTask.getTaskId());
                        activeTask.pause(true, currentTaskId);
                    });
        }
    }

    @Override
    public boolean waitForTaskCompletion(String taskId, long timeout) {
        InternalTask task = getActiveInternalTask(taskId, false);
        //Check for null since task may have already been canceled
        return task == null || task.waitForCompletion(timeout);
    }

    @Override
    public ConflictsGuard<String> getConflictsGuard() {
        return conflictsGuard;
    }

    @Override
    public void canBeStarted(Task task) {
        InternalTask internalTask = getActiveInternalTask(task);
        Class<? extends TaskCallback> callbackType = internalTask.getType();
        JobCommand jobCommand = callbackType.getAnnotation(JobCommand.class);
        if (jobCommand == null) {
            throw new IllegalArgumentException(
                    "Callback type " + callbackType.getName() + " does not have the " +
                            JobCommand.class.getName() + " annotation!");
        }
        if (internalTask.isManuallyActivated() && jobCommand.manualUser() == TaskUser.INVALID) {
            throw new IllegalStateException("Cannot start task (" + internalTask + ") manually!" +
                    " Manual user not defined!");
        }
        boolean hasKeyAttributes = jobCommand.keyAttributes().length > 0;
        if (!jobCommand.singleton() && !hasKeyAttributes) {
            return;
        }
        EnumMap<StopStrategy, Predicate<Task>> predicatePerStrategy = getPredicatePerStrategy(callbackType,
                internalTask.getKeyValues());
        // Should not count myself :)
        Predicate<Task> notMyself = getNotMySelfPredicate();

        // Only impossible are blocker here
        Predicate<Task> impossibleFilter = predicatePerStrategy.get(StopStrategy.IMPOSSIBLE);
        if (impossibleFilter != null) {
            for (InternalTask activeTask : getActiveTasks(notMyself.and(impossibleFilter)).stream()
                    .map(InternalTask.class::cast).collect(Collectors.toSet())) {
                if (internalTask.isManuallyActivated()) {
                    if (activeTask.processActiveOnCluster()) {
                        throw new IllegalStateException("Cannot start task (" + internalTask.getType() + ") manually!" +
                                " Task " + activeTask.getType() + " is active!");
                    }
                    if (activeTask.isManuallyActivated() && !activeTask.wasCompleted()) {
                        throw new IllegalStateException("Cannot start task (" + internalTask.getType() + ") manually!" +
                                " Another manual task " + activeTask.getType() + " exists and is not completed!");
                    }
                } else {
                    // Starting impossible filter of same type means singleton or key based singleton match
                    if (activeTask.getType().equals(callbackType)) {
                        if (activeTask.isManuallyActivated()) {
                            if (!activeTask.wasCompleted()) {
                                throw new IllegalStateException(
                                        "Cannot schedule task (" + internalTask.getType() + ")!" +
                                                " Another manual task of same type and " +
                                                "key exists and is not completed! Task key attributes: " +
                                                Arrays.toString(internalTask.getKeyValues()));
                            }
                        } else {
                            throw new IllegalStateException("Cannot schedule task (" + internalTask.getType() + ")!" +
                                    " Another task of same type and key is already scheduled! Task key attributes: " +
                                    Arrays.toString(internalTask.getKeyValues()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Used only by the task in case that the task needs to cancel itself
     */
    @Override
    public void cancelMyselfCallback(Task task) {
        activeTasksById.remove(task.getTaskId());
        ((InternalTask) task).cancelTask();
    }

    private void activateTasks() {
        Iterator<String> taskIds = inactiveTasksById.keySet().iterator();
        while (taskIds.hasNext()) {
            String taskId = taskIds.next();
            //Check the readiness and task status again since it can change
            if (ContextHelper.get().isReady()) {
                moveToActive(taskId);
                taskIds.remove();
            }
        }
    }

    private void moveToActive(String taskKey) {
        InternalTask task = inactiveTasksById.get(taskKey);
        if (task != null) {
            activeTasksById.put(taskKey, task);
            task.schedule(false);
        }
    }

    private Predicate<Task> getNotMySelfPredicate() {
        return input -> {
            // null task always false
            if (input == null) {
                return false;
            }
            String currentTaskId = TaskCallback.currentTaskId();
            return currentTaskId == null || !currentTaskId.equals(input.getTaskId());
        };
    }

    private EnumMap<StopStrategy, Predicate<Task>> getPredicatePerStrategy(
            Class<? extends TaskCallback> typeToRun,
            final Object... keyValues) {
        EnumMap<StopStrategy, Predicate<Task>> result = new EnumMap<>(StopStrategy.class);
        JobCommand toRunJobCommand = typeToRun.getAnnotation(JobCommand.class);
        final String[] keys = toRunJobCommand.keyAttributes();
        if (keys.length != keyValues.length) {
            throw new IllegalArgumentException(
                    "Cannot check if task " + typeToRun + " can start without key values!\n" +
                            "Received " + Arrays.toString(keyValues) + " and expected values for " + Arrays.toString(
                            keys));
        }
        for (StopCommand stopCommand : toRunJobCommand.commandsToStop()) {
            Class<? extends TaskCallback> callbackType;
            // get callback type from command or command name
            callbackType = getCommand(typeToRun, stopCommand);
            if ((callbackType.getModifiers() & Modifier.ABSTRACT) != 0) {
                throw new IllegalArgumentException(
                        "Job command definition for " + typeToRun.getName() + " contain an abstract class to stop!");
            }
            StopStrategy stopStrategy = stopCommand.strategy();
            TaskTypePredicate taskPredicate = (TaskTypePredicate) result.get(stopStrategy);
            if (stopCommand.useKey()) {
                taskPredicate = getTaskPredicateForKey(callbackType, taskPredicate, keyValues);
            } else {
                taskPredicate = getTaskPredicateForType(callbackType, taskPredicate);
            }
            result.put(stopStrategy, taskPredicate);
        }

        if (toRunJobCommand.singleton()) {
            // If singleton and currently running => Impossible to stop
            TaskTypePredicate taskPredicate = (TaskTypePredicate) result.get(StopStrategy.IMPOSSIBLE);
            taskPredicate = getTaskPredicateForType(typeToRun, taskPredicate);
            result.put(StopStrategy.IMPOSSIBLE, taskPredicate);
        }

        if (keys.length != 0) {
            // If key based and currently running => Impossible to stop
            TaskTypePredicate taskPredicate = (TaskTypePredicate) result.get(StopStrategy.IMPOSSIBLE);
            taskPredicate = getTaskPredicateForKey(typeToRun, taskPredicate, keyValues);
            result.put(StopStrategy.IMPOSSIBLE, taskPredicate);
        }

        return result;
    }

    /**
     * get task to do stop command on it
     *
     * @param stopCommand - stop command to execute on job
     * @return callback task class
     */
    private Class<? extends TaskCallback> getCommand(Class<? extends TaskCallback> typeToRun, StopCommand stopCommand) {

        Class<? extends TaskCallback> callbackType = null;
        ScheduleJobEnum commandName = stopCommand.commandName();
        // is command name define bu no command type
        if (!commandName.equals(ScheduleJobEnum.DUMMY_JOB) && stopCommand.command() == DummyJob.class) {
            try {
                callbackType = (Class<? extends TaskCallback>) Class.forName(commandName.jobName);
            } catch (ClassNotFoundException e) {
                log.error("Class commandName not found", e);
            }
        }
        // no command name or command type has been define
        else if (commandName.equals(ScheduleJobEnum.DUMMY_JOB) && stopCommand.command() == DummyJob.class) {
            log.error("No job command type or name was defined for job class: {}", typeToRun.getSimpleName());
        } else {//command type has been define
            callbackType = stopCommand.command();
        }
        return callbackType;
    }

    private TaskTypePredicate getTaskPredicateForType(Class<? extends TaskCallback> callbackType,
            TaskTypePredicate taskPredicate) {
        if (taskPredicate == null) {
            taskPredicate = new TaskTypePredicate(callbackType);
        } else {
            taskPredicate = new TaskTypePredicate(taskPredicate, callbackType);
        }
        return taskPredicate;
    }

    private TaskTypePredicate getTaskPredicateForKey(final Class<? extends TaskCallback> callbackType,
            TaskTypePredicate taskPredicate, final Object[] keyValues) {
        if (taskPredicate == null) {
            taskPredicate = new TaskTypePredicate();
        }
        taskPredicate.addSubPredicate(input -> (input != null) &&
                ClassUtils.isAssignable(callbackType, input.getType()) &&
                input.keyEquals(keyValues));
        return taskPredicate;
    }

    private boolean hasTaskOfType(Class<? extends TaskCallback> callbackType,
            ConcurrentMap<String, InternalTask> taskMap,
            boolean withManual) {
        if (callbackType == null) {
            return false;
        }
        for (InternalTask task : taskMap.values().stream().map(InternalTask.class::cast).collect(Collectors.toSet())) {
            if (ClassUtils.isAssignable(callbackType, task.getType()) &&
                    !(withManual && task.isManuallyActivated())) {
                return true;
            }
        }
        return false;
    }

    private List<InternalTask> getActiveInternalTasks(@Nonnull Predicate<Task> predicate) {
        return getActiveTasks(predicate).stream().map(InternalTask.class::cast).collect(Collectors.toList());
    }

    private boolean isBlockingQuartzTask(Task task) {
        // Only Quartz jobs are gated: this ticket targets the classloader-destruction race that occurs when a Quartz
        // job's user classes are torn down mid-execution during shutdown. Other TaskCallback types are out of scope
        // and intentionally not awaited here.
        Class<? extends TaskCallback> type = task.getType();
        if (type == null || !QuartzCommand.class.isAssignableFrom(type)) {
            return false;
        }
        JobCommand jobCommand = type.getAnnotation(JobCommand.class);
        boolean shutdownTransparent = jobCommand != null && jobCommand.shutdownTransparent();
        return !shutdownTransparent && task.processActive();
    }

    private void stopHaTask(String taskId, boolean wait) {
        long timeout = wait ? locksTimeoutSecs : 0;
        String stopTaskIdLockMarker = HaQuartzTask.STOP_MARKER_PREFIX + taskId;
        try {
            getConflictsGuard().tryToLock(stopTaskIdLockMarker, timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logFailedToStopTask(taskId, stopTaskIdLockMarker, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logFailedToStopTask(taskId, stopTaskIdLockMarker, e);
        }
    }

    private static void logFailedToStopTask(String taskId, String stopTaskIdLockMarker, Exception e) {
        log.error("Failed to stop task with ID: {} by locking stop marker: {}", taskId, stopTaskIdLockMarker, e);
    }

    private void pauseHaTask(String taskId, boolean wait) {
        long timeout = wait ? locksTimeoutSecs : 0;
        String pauseTaskIdLockMarker = HaQuartzTask.PAUSE_MARKER_PREFIX + taskId;
        try {
            getConflictsGuard().tryToLock(pauseTaskIdLockMarker, timeout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logFailedToPauseTask(taskId, pauseTaskIdLockMarker, e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logFailedToPauseTask(taskId, pauseTaskIdLockMarker, e);
        }
    }

    private static void logFailedToPauseTask(String taskId, String pauseTaskIdLockMarker, Exception e) {
        log.error("Failed to pause task with ID: {} by locking stop marker: {}", taskId, pauseTaskIdLockMarker, e);
    }

    private boolean resumeHaTask(String taskId) {
        String pauseTaskIdLockMarker = HaQuartzTask.PAUSE_MARKER_PREFIX + taskId;
        try {
            getConflictsGuard().forceUnlock(pauseTaskIdLockMarker);
            return true;
        } catch (Exception e) {
            log.error("Failed to resume task ID: {} by unlocking lock marker: {}", taskId, pauseTaskIdLockMarker);
            return false;
        }
    }

    private boolean checkCanStartManualTask(Class<? extends TaskCallback> typeToRun, MutableStatusHolder statusHolder,
            JobCommand jobCommand, Object[] keyValues) {
        if (ContextHelper.get().isOffline()) {
            statusHolder.error("Artifactory is in offline state, task are not allowed to run during offline state!",
                    log);
            return false;
        }
        if (jobCommand == null) {
            statusHolder.error(
                    "Task type " + typeToRun.getName() + " does not have the " +
                            JobCommand.class.getName() + " annotation!", log);
            return false;
        }
        if (jobCommand.manualUser() == TaskUser.INVALID) {
            statusHolder.error("Task type " + typeToRun.getName() + " is not defined to run manually!", log);
            return false;
        }
        String currentTaskId = TaskCallback.currentTaskId();
        if (currentTaskId != null) {
            statusHolder
                    .error("Cannot check for manual run with status from inside a running task: " + currentTaskId, log);
            return false;
        }

        // Extract task filters for each stop strategy
        EnumMap<StopStrategy, Predicate<Task>> predicatePerStrategy = getPredicatePerStrategy(typeToRun, keyValues);

        // Check first for impossible to run
        Predicate<Task> impossibleFilter = predicatePerStrategy.get(StopStrategy.IMPOSSIBLE);
        if (impossibleFilter != null) {
            for (InternalTask activeTask : getActiveTasks(impossibleFilter).stream().map(InternalTask.class::cast)
                    .collect(Collectors.toSet())) {
                if (activeTask.processActiveOnCluster()) {
                    statusHolder.error("Task " + typeToRun.getName() + " cannot stop a mandatory related job " +
                            activeTask.getType().getName() + " while it's running!", HttpStatus.SC_CONFLICT, log);
                    return false;
                }
                if (activeTask.getType().equals(
                        typeToRun) && activeTask.isManuallyActivated() && !activeTask.wasCompleted()) {
                    statusHolder.error("Another manual task " + typeToRun.getName() + " is still active!",
                            HttpStatus.SC_CONFLICT, log);
                    return false;
                }
            }
        }

        // Then check for stopped
        Predicate<Task> stopFilter = predicatePerStrategy.get(StopStrategy.STOP);
        if (stopFilter != null) {
            for (Task activeTask : getActiveTasks(stopFilter)) {
                if (activeTask.processActiveOnCluster()) {
                    statusHolder.warn(
                            "Task " + activeTask.getType().getName() + " will be stopped by running "
                                    + typeToRun.getName() + " !", log);
                }
            }
        }

        // Then pause what's needed
        Predicate<Task> pauseFilter = predicatePerStrategy.get(StopStrategy.PAUSE);
        if (pauseFilter != null) {
            for (Task activeTask : getActiveTasks(pauseFilter)) {
                if (activeTask.processActiveOnCluster()) {
                    statusHolder.warn(
                            "Task " + activeTask.getType().getName() + " will be paused by running "
                                    + typeToRun.getName() + " !", log);
                }
            }
        }
        return true;
    }
}
