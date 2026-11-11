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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.LockingException;
import org.artifactory.concurrent.State;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Yoav Landman
 */
@Slf4j
public class QuartzTask implements InternalTask {
    //TODO: [by fsi] should use StateManager
    private final ReentrantLock stateSync;
    private final Condition stateChanged;
    private final Condition completed;
    private final Class<? extends TaskCallback> callbackType;
    private TaskState state;
    private final JobDataMap jobDataMap;
    private boolean executed;
    // Initialized from JobCommand annotation
    private boolean singleton;
    private boolean clusterSingleton;
    private boolean manuallyActivated;
    private boolean shouldReRunTask;
    /**
     * Prevents resuming a task until all stoppers/pausers have resumed it
     */
    private int resumeBarriersCount;
    // last time in millis this task was started. set whenever this task start running
    private long lastStarted;
    private Trigger trigger;
    private final JobDetail jobDetail;

    @SuppressWarnings({"unchecked"})
    QuartzTask(Class<? extends TaskCallback> callbackType, Trigger trigger, JobDetail jobDetail) {
        state = TaskState.VIRGIN;
        stateSync = new ReentrantLock();
        stateChanged = stateSync.newCondition();
        completed = stateSync.newCondition();
        executed = false;
        resumeBarriersCount = 0;
        this.jobDataMap = jobDetail.getJobDataMap();
        this.callbackType = callbackType;
        this.trigger = trigger;
        this.jobDetail = jobDetail;
    }

    private static void catchInterrupt(TaskState state) {
        log.warn("Interrupted during state wait from '{}'.", state);
    }

    @VisibleForTesting
    Scheduler getScheduler() {
        Object jobName = getAttribute(Task.JOB_NAME);
        if (isHeartbeatJob(jobName)) {
            log.debug("Using hbScheduler because the jobName is: {}", jobName);
            return ContextHelper.get().beanForType("hbScheduler", Scheduler.class);
        }

        if (isRetentionPolicyJob(jobName)) {
            log.debug("Using policyScheduler because the jobName is: {}", jobName);
            return ContextHelper.get().beanForType("policyScheduler", Scheduler.class);
        }

        if (isSystemLoadMonitorJob(jobName) || isReadinessJob(jobName)) {
            log.debug("Using monitoringScheduler because the jobName is: {}", jobName);
            return ContextHelper.get().beanForType("monitoringScheduler", Scheduler.class);
        }

        log.debug("Using regular scheduler for: '{}'", jobName);
        return ContextHelper.get().beanForType(Scheduler.class);
    }

    public String getDescription() {
        return jobDetail.getDescription();
    }


    @Override
    public boolean processActiveOnCluster() {
        return processActive();
    }

    @Override
    public boolean isRunningOnCluster() {
        return isRunning();
    }

    @Override
    public void start(InternalQuartzCommand command) throws JobExecutionException {
        command.runCommand();
    }

    @Override
    public boolean resume(String stoppingTaskId) {
        return resume();
    }

    @Override
    public String getTaskId() {
        return jobDetail.getKey().toString();
    }

    @Override
    public void cancelTask() {
        Scheduler scheduler = getScheduler();
        try {
            JobKey jobKey = jobDetail.getKey();
            if (!scheduler.deleteJob(jobKey)) {
                log.info("Task " + jobKey + " already deleted from scheduler");
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("Failed to unschedule previous job: " + trigger.getKey(), e);
        }
    }

    @Override
    public boolean isAboutToPause() {
        return state == TaskState.PAUSING;
    }

    @Override
    public void scheduleTask() {
        Scheduler scheduler = getScheduler();
        try {
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            log.warn("Scheduling job \"" + jobDetail.getKey().getName() + "\" has failed, {}", e.getMessage());
            log.debug("Cause: {}", e);
        }
    }

    @Override
    public boolean isSingleExecution() {
        if (trigger instanceof SimpleTrigger) {
            return ((SimpleTrigger) trigger).getRepeatCount() == 0;
        }
        return !trigger.mayFireAgain();
    }

    @Override
    public Object getAttribute(String key) {
        return jobDetail.getJobDataMap().get(key);
    }

    @Override
    public void addAttribute(String key, Object value) {
        jobDetail.getJobDataMap().put(key, value);
    }

    @Override
    public State getInitialState() {
        return TaskState.VIRGIN;
    }

    @Override
    public boolean isClusterSingleton() {
        return clusterSingleton;
    }

    @Override
    public void setClusterSingleton(boolean clusterSingleton) {
        this.clusterSingleton = clusterSingleton;
    }

    @Override
    public boolean waitingForProcess() {
        return state == TaskState.PAUSING || state == TaskState.STOPPING;
    }

    @Override
    public boolean isRunning() {
        return state == TaskState.RUNNING || waitingForProcess();
    }

    @Override
    public boolean processActive() {
        return isRunning() || state == TaskState.PAUSED;
    }

    @Override
    public boolean blockIfPausedAndShouldBreak() {
        lockState();
        //if running continue, if pausing transition to pause else exit
        try {
            if (state == TaskState.PAUSING) {
                guardedSetState(TaskState.PAUSED);
            }
            try {
                log.trace("Entering wait for out of paused on: {}", this);
                int tries = ConstantValues.taskCompletionLockTimeoutRetries.getInt();
                long timeout = ConstantValues.taskPauseTimeoutSecs.getLong();
                while (state == TaskState.PAUSED) {
                    stateChanged.await(timeout, TimeUnit.SECONDS);
                    tries--;
                    if (tries <= 0) {
                        throw new LockingException("Task " + this + " paused for more than " +
                                ConstantValues.taskCompletionLockTimeoutRetries.getInt() + " times.");
                    }
                    log.trace("One wait for out of paused from on {}", this);
                }
            } catch (InterruptedException e) {
                catchInterrupt(TaskState.PAUSED);
            }
            return state != TaskState.RUNNING;
        } finally {
            unlockState();
        }
    }

    /**
     * Weather the task with this callback type should be unique on the task service.
     * i.e., not other task with the same type should ever be running.
     *
     * @return True if this task should be unique
     */
    @Override
    public boolean isSingleton() {
        return singleton;
    }

    @Override
    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    @Override
    public boolean shouldRescheduleTask() {
        return shouldReRunTask;
    }

    @Override
    public void setShouldRescheduleTask(boolean shouldReRunTask) {
        this.shouldReRunTask = shouldReRunTask;
    }

    @Override
    public void rescheduleTask(long waitBeforeExecute) {
        trigger = TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(trigger.getKey())
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .startAt(new Date(System.currentTimeMillis() + waitBeforeExecute)).build();
        log.debug("Canceling current task {} before re-scheduling", trigger.getKey().getName());
        cancelTask();
        log.debug("Scheduling task {} to rerun", trigger.getKey().getName());
        scheduleTask();
        setShouldRescheduleTask(false);
    }

    @Override
    public boolean isManuallyActivated() {
        return manuallyActivated;
    }

    @Override
    public void setManuallyActivated(boolean manuallyActivated) {
        this.manuallyActivated = manuallyActivated;
    }

    @Override
    public boolean wasCompleted() {
        if (!isSingleExecution()) {
            throw new UnsupportedOperationException("Does not support waitForCompletion on cyclic tasks.");
        }
        return executed && (state == TaskState.STOPPED || state == TaskState.CANCELED);
    }

    @Override
    public String getTaskLockKeyIncludingKeyAttributes(List<String> keyAttributes) {
        String jobName = ((JobName) jobDataMap.get(Task.JOB_NAME)).asText();
        return TaskUtils.getTaskLockKeyIncludingKeyAttributes(jobName, jobDataMap, keyAttributes);
    }

    @Override
    public boolean keyEquals(Object... keyValues) {
        JobCommand jobCommand = this.getType().getAnnotation(JobCommand.class);
        String[] keys = jobCommand.keyAttributes();
        if (keyValues.length != keys.length) {
            throw new IllegalArgumentException("Cannot compare key values for task " + getType() + "\n" +
                    "Received " + Arrays.toString(keyValues) + " and expected values for " + Arrays.toString(keys));
        }
        for (int i = 0; i < keys.length; i++) {
            Object attribute = getAttribute(keys[i]);
            if (attribute == null) {
                log.warn("Task attribute is NULL: {}, given keyValues: {}", keys[i], keyValues);
                return false;
            }
            if (!attribute.equals(keyValues[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean keyEquals(Task task) {
        JobCommand otherJobCommand = task.getType().getAnnotation(JobCommand.class);
        JobCommand myJobCommand = this.getType().getAnnotation(JobCommand.class);
        String[] myKeys = myJobCommand.keyAttributes();
        String[] otherKeys = otherJobCommand.keyAttributes();
        if (!Arrays.equals(myKeys, otherKeys)) {
            throw new IllegalArgumentException(
                    "Cannot compare key values between task " + this + " and task " + task + "\n" +
                            "Keys " + Arrays.toString(myKeys) + " not equals to " + Arrays.toString(otherKeys)
            );
        }
        for (String key : myKeys) {
            if (!getAttribute(key).equals(task.getAttribute(key))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object[] getKeyValues() {
        JobCommand myJobCommand = this.getType().getAnnotation(JobCommand.class);
        String[] myKeys = myJobCommand.keyAttributes();
        Object[] result = new Object[myKeys.length];
        for (int i = 0; i < myKeys.length; i++) {
            result[i] = getAttribute(myKeys[i]);
        }
        return result;
    }

    @Override
    public long getLastStarted() {
        return lastStarted;
    }

    @Override
    public ImmutableMap getAttributeMap() {
        return ImmutableMap.copyOf(jobDataMap);
    }

    @Override
    public TaskState getCurrentState() {
        return state;
    }

    @Override
    public Class<? extends TaskCallback> getType() {
        return callbackType;
    }

    @Override
    public String getTaskName() {
        return getType().getName();
    }

    @Override
    public int hashCode() {
        return getTaskId().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Task)) {
            return false;
        }
        Task base = (Task) o;
        return getTaskId().equals(base.getTaskId());
    }

    @Override
    public String toString() {
        return getTaskId();
    }

    @Override
    public void schedule(boolean waitForRunning) {
        lockState();
        try {
            scheduleTask();
            guardedTransitionToState(TaskState.SCHEDULED, waitForRunning);
        } finally {
            unlockState();
        }
    }

    @Override
    public void cancel(boolean wait, long timeout) {
        lockState();
        try {
            log.trace("Entering cancel with state {} on {}", state, this);
            if (processActive() && !wait) {
                throw new IllegalStateException("Cannot cancel immediately an active task " + this);
            }

            if (processActive()) {
                log.trace("Waiting for active task: {} to finish.", this);
                if (waitingForProcess()) {
                    guardedWaitForNextStep();
                }
                if (processActive()) {
                    guardedTransitionToState(TaskState.STOPPING, wait, timeout);
                }
            }
            if (state == TaskState.CANCELED) {
                log.info("Task {} already canceled.", this);
            } else {
                log.debug("Canceling task: {}.", this);
                guardedSetState(TaskState.CANCELED);
            }
            cancelTask();
        } finally {
            unlockState();
        }
    }

    @Override
    public void pause(boolean wait) {
        lockState();
        try {
            log.trace("Entering pause with state {} on {}", state, this);
            if (state == TaskState.VIRGIN) {
                throw new IllegalStateException("Cannot stop a virgin task.");
            } else if (state == TaskState.RUNNING) {
                if (!wait) {
                    throw new IllegalStateException("Cannot pause immediately a running task " + this);
                }
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after pause while running on {}", resumeBarriersCount, this);
                guardedTransitionToState(TaskState.PAUSING, wait);
            } else if (state == TaskState.STOPPING) {
                // Already stopping, waiting for stop
                if (!wait) {
                    throw new IllegalStateException("Cannot pause immediately a stopping task " + this);
                }
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after pause while stopping on {}", resumeBarriersCount, this);
                guardedWaitForNextStep();
            } else if (state == TaskState.PAUSED || state == TaskState.PAUSING) {
                // Already paused, just count the barrier
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after pause already paused on {}", resumeBarriersCount, this);
            } else if (state == TaskState.CANCELED) {
                // Task canceled, forget it => do nothing
            } else {
                // Not running, just count the barrier, and set to stop
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after pause on {}", resumeBarriersCount, this);
                if (state != TaskState.STOPPED) {
                    guardedSetState(TaskState.STOPPED);
                }
            }
        } finally {
            unlockState();
        }
    }

    @Override
    public void pause(boolean wait, String stoppingTaskId) {
        pause(wait);
    }

    /**
     * Stops but does not unschedule the task (can transition back to running state)
     */
    @Override
    public void stop(boolean wait) {
        lockState();
        try {
            log.trace("Entering stop with state {} on {}", state, this);
            if (state == TaskState.VIRGIN) {
                throw new IllegalStateException("Cannot stop a virgin task.");
            } else if (state == TaskState.RUNNING || state == TaskState.PAUSING) {
                if (!wait) {
                    throw new IllegalStateException("Cannot stop immediately a running task " + this);
                }
                //Stop
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after stop on {}", resumeBarriersCount, this);
                guardedTransitionToState(TaskState.STOPPING, wait);
            } else if (state == TaskState.CANCELED) {
                // Task canceled, forget it => do nothing
            } else if (state == TaskState.STOPPED || state == TaskState.STOPPING) {
                // Already stopped, just count the barrier
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after stop already stopped on {}", resumeBarriersCount, this);
                if (state == TaskState.STOPPING) {
                    guardedWaitForNextStep();
                }
            } else {
                //For both stop and pause
                resumeBarriersCount++;
                log.trace("resumeBarriersCount++ to {} after stop on {}", resumeBarriersCount, this);
                guardedSetState(TaskState.STOPPED);
            }
        } finally {
            unlockState();
        }
    }

    @Override
    public void stop(boolean wait, String stoppingTaskId) {
        stop(wait);
    }

    @Override
    public boolean resume() {
        lockState();
        try {
            if (state == TaskState.CANCELED) {
                throw new IllegalStateException("Cannot resume a canceled task.");
            }
            if (resumeBarriersCount > 0) {
                resumeBarriersCount--;
            } else {
                log.info("Skipping resume since there are no active resume barriers " +
                        "(probably invoked resume() more than needed).");
                return true;
            }
            log.trace("resumeBarriersCount-- to {} after resume on {}", resumeBarriersCount, this);
            if (resumeBarriersCount > 0) {
                log.debug("Cannot resume while there are still {} resume barriers.", resumeBarriersCount);
                return false;
            }
            if (state == TaskState.PAUSED || state == TaskState.PAUSING) {
                guardedSetState(TaskState.RUNNING);
            } else if (state == TaskState.STOPPED || state == TaskState.STOPPING) {
                //Nothing to do for single execution - either resume from pause or reached stopped
                //if resume by a different thread
                if (!isSingleExecution()) {
                    guardedSetState(TaskState.SCHEDULED);
                }
            }
            return true;
        } finally {
            unlockState();
        }
    }

    /**
     * Wait for the task to stop running
     */
    @Override
    public boolean waitForCompletion(long timeout) {
        if (!isSingleExecution()) {
            throw new UnsupportedOperationException("Does not support waitForCompletion on cyclic tasks.");
        }
        if (!isRunning()) {
            // The task may have already complete
            return true;
        }
        boolean completed = false;
        lockState();
        try {
            try {
                //Wait forever (tries * lock timeout) until it finished the current execution
                if (timeout == 0) {
                    timeout = ConstantValues.locksTimeoutSecs.getLong() *
                            ConstantValues.taskCompletionLockTimeoutRetries.getInt();
                }
                long start = System.currentTimeMillis();
                while (true) {
                    // If already executed (passed to running state) and now stooped or canceled
                    //   => It means already completed
                    if (executed) {
                        if (state == TaskState.STOPPED || state == TaskState.CANCELED) {
                            completed = true;
                            break;
                        }
                    }
                    // Waiting on the completed condition
                    boolean success = this.completed.await(ConstantValues.locksTimeoutSecs.getLong(), TimeUnit.SECONDS);
                    if (success) {
                        completed = true;
                        break;
                    }
                    if (start + timeout >= System.currentTimeMillis()) {
                        throw new LockingException("Waited for task " + this + " more than " + timeout + "ms.");
                    }
                }
            } catch (InterruptedException e) {
                catchInterrupt(state);
            }
        } finally {
            unlockState();
        }
        return completed;
    }

    @Override
    public boolean canStart() {
        boolean canStart = false;
        lockState();
        if (state == TaskState.SCHEDULED) {
            boolean validNewState = state.canTransitionTo(TaskState.RUNNING);
            if (!validNewState) {
                log.warn("Cannot transition from " + this.state + " to " + TaskState.RUNNING + ".");
                canStart = false;
            } else {
                canStart = true;
            }
        }
        unlockState();
        return canStart;
    }

    @Override
    public boolean started() {
        boolean shouldExecute = false;
        lockState();
        //Check if should run
        try {
            if (state == TaskState.SCHEDULED) {
                guardedSetState(TaskState.RUNNING);
                shouldExecute = true;
            }
        } finally {
            unlockState();
        }
        return shouldExecute;
    }

    @Override
    public void completed() {
        lockState();
        try {
            if (state == TaskState.STOPPED) {
                //Do nothing
                return;
            }
            if (state == TaskState.PAUSED || state == TaskState.STOPPING || state == TaskState.PAUSING) {
                guardedSetState(TaskState.STOPPED);
            } else if (state != TaskState.CANCELED) {
                if (isSingleExecution()) {
                    guardedSetState(TaskState.STOPPED);
                } else if (state != TaskState.SCHEDULED) {
                    //Could be on SCHEDULED if resumed after stopped
                    guardedSetState(TaskState.SCHEDULED);
                }
            }
            completed.signal();
        } finally {
            unlockState();
        }
    }

    @Override
    public void lockState() {
        try {
            int holdCount = stateSync.getHoldCount();
            log.trace("Thread {} trying lock (activeLocks={}) on {}",
                    Thread.currentThread(), holdCount, this);
            if (holdCount > 0) {
                //Clean all and throw
                while (holdCount > 0) {
                    stateSync.unlock();
                    holdCount--;
                }
                throw new LockingException("Locking an already locked task state: " +
                        this + " active lock(s) already active!");
            }
            boolean success = stateSync.tryLock() ||
                    stateSync.tryLock(getStateLockTimeOut(), TimeUnit.SECONDS);
            if (!success) {
                throw new LockingException(
                        "Could not acquire state lock in " + getStateLockTimeOut() + " secs");
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while trying to lock {}.", this);
        }
    }

    @Override
    public void unlockState() {
        log.trace("Unlocking {}", this);
        stateSync.unlock();
    }

    private void guardedTransitionToState(TaskState newState, boolean waitForNextStep) {
        long timeout = ConstantValues.locksTimeoutSecs.getLong();
        guardedTransitionToState(newState, waitForNextStep, timeout);
    }

    private void guardedTransitionToState(TaskState newState, boolean waitForNextStep, long timeout) {
        if (state == newState) {
            return;
        }
        guardedSetState(newState);
        if (waitForNextStep) {
            guardedWaitForNextStep(timeout);
        }
    }

    private TaskState guardedWaitForNextStep() {
        long timeout = ConstantValues.locksTimeoutSecs.getLong();
        return guardedWaitForNextStep(timeout);
    }

    private TaskState guardedWaitForNextStep(long timeout) {
        TaskState oldState = state;
        TaskState newState = oldState;
        try {
            log.trace("Entering wait for next step from {} on: {}", oldState, this);
            while (state == oldState) {
                boolean success = stateChanged.await(timeout, TimeUnit.SECONDS);
                if (!success) {
                    throw new LockingException(
                            "Timeout after " + timeout + " seconds when trying to wait for next state in '" + oldState +
                                    "'." + " For task: " + getTaskId()
                    );
                }
                newState = state;
                log.trace("Exiting wait for next step from {} to {} on {}", oldState, newState, this);
            }
        } catch (InterruptedException e) {
            catchInterrupt(oldState);
        }
        return newState;
    }

    private void guardedSetState(TaskState newState) {
        boolean validNewState = state.canTransitionTo(newState);
        if (!validNewState) {
            throw new IllegalArgumentException("Cannot transition from " + this.state + " to " + newState + ".");
        }
        log.trace("Changing state: {}: {}-->{}", this.toString(), this.state, newState);
        state = newState;
        if (state == TaskState.RUNNING) {
            executed = true;
            lastStarted = System.currentTimeMillis();
        } else if (state == TaskState.SCHEDULED) {
            executed = false;
        }
        stateChanged.signal();
    }

    private long getStateLockTimeOut() {
        return ConstantValues.locksTimeoutSecs.getLong();
    }

    public Trigger getTrigger() {
        return trigger;
    }

    private static boolean isHeartbeatJob(Object jobName) {
        return jobName.equals(JobName.HEARTBEAT) ||
                jobName.equals(JobName.TEMP_MIRROR_HEARTBEAT) ||
                jobName.equals(JobName.MIRROR_HEARTBEAT);
    }

    private boolean isSystemLoadMonitorJob(Object jobName) {
        return jobName.equals(JobName.SYSTEM_LOAD_MONITOR);
    }

    private boolean isReadinessJob(Object jobName) {
        return jobName.equals(JobName.READINESS);
    }



    private boolean isRetentionPolicyJob(Object jobName) {
        return jobName.equals(JobName.PACKAGE_CLEANUP) || jobName.equals(JobName.PACKAGE_ARCHIVE) ||
                jobName.equals(JobName.RELEASE_BUNDLE_V2_CLEANUP) || jobName.equals(JobName.BUILD_CLEANUP);
    }
}