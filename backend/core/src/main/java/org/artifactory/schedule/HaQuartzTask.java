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

import com.google.common.collect.Sets;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.LockingException;
import org.artifactory.storage.db.locks.service.MarkerDbLocksService;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.LockingUtilImpl;
import org.quartz.JobExecutionException;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Mor Merhav
 */
@Slf4j
public class HaQuartzTask implements InternalTask {

    /**
     * Lombok {@link Delegate}  is a special feature which generates delegation methods calling the internal
     * {@code quartzTask} field methods.
     * For example:
     * <pre>{@code
     * @Override
     * public boolean isClusterSingleton() {
     *    return quartzTask.isClusterSingleton();
     * }
     * }</pre>
     * This reduces the boilerplate code involved with implementing
     * <a href="https://en.wikipedia.org/wiki/Composition_over_inheritance">composition</a>.
     */
    @Delegate(excludes = ExcludedMethods.class)
    private final InternalTask quartzTask;
    public static final String STOP_MARKER_PREFIX = "stop_marker_";
    public static final String PAUSE_MARKER_PREFIX = "pause_marker_";

    /**
     * Used by other tasks to stop the task
     */
    private final String stopMarkerLockPrefix;

    /**
     * Used by other tasks to pause the task
     */
    private final String pauseMarkerLockPrefix;

    /**
     * Used by a user to externally stop the task
     */
    private final String taskIdStopMarkerLock;

    /**
     * Used by a user to pause the task
     */
    private final String taskIdPauseMarkerLock;
    private final Set<String> markerLocks;
    private final QuartzConflictsGuard conflictsGuard;

    HaQuartzTask(InternalTask quartzTask, MarkerDbLocksService dbLocksService) {
        this.quartzTask = quartzTask;
        this.stopMarkerLockPrefix = STOP_MARKER_PREFIX + getMyJobCommand().jobName() + "_";
        this.pauseMarkerLockPrefix = PAUSE_MARKER_PREFIX + getMyJobCommand().jobName() + "_";
        this.taskIdStopMarkerLock = STOP_MARKER_PREFIX + getTaskId();
        this.taskIdPauseMarkerLock = PAUSE_MARKER_PREFIX + getTaskId();
        this.markerLocks = Sets.newConcurrentHashSet();
        this.conflictsGuard = new QuartzConflictsGuard(dbLocksService);
    }

    @Override
    public void pause(boolean wait, String pausingTaskId) {
        pause(wait);
        if (pausingTaskId != null) {
            acquireMarkerLock(pauseMarkerLockPrefix + pausingTaskId);
        }
    }

    @Override
    public void stop(boolean wait, String stoppingTaskId) {
        stop(wait);
        if (stoppingTaskId != null) {
            acquireMarkerLock(stopMarkerLockPrefix + stoppingTaskId);
        }
    }

    @Override
    public void start(InternalQuartzCommand command) throws JobExecutionException {
        log.debug("Executing task: {} with task ID: {}.", getTaskName(), this.getTaskId());
        if (isClusterSingleton()) {
            doExecuteInLock(command);
        } else {
            quartzTask.start(command);
        }
    }

    @Override
    public boolean resume(String stoppingTaskId) {
        boolean isScheduled = resume();
        if (stoppingTaskId != null && !markerLocks.isEmpty()) {
            log.debug("Removing stop/pause marker locks acquired by task ID: {}", stoppingTaskId);
            Set<String> markerLocksByStoppingTask =
                    markerLocks.stream()
                            .filter(markerLock -> markerLock.contains(stoppingTaskId))
                            .collect(Collectors.toUnmodifiableSet());
            markerLocksByStoppingTask.forEach(conflictsGuard::unlock);
            markerLocks.removeAll(markerLocksByStoppingTask);
        }
        return isScheduled;
    }

    @Override
    public boolean canStart() {
        return quartzTask.canStart() && (!isMyClusterSingletonLockClaimed() && !isStoppedByMarkerLock());
    }

    @Override
    public boolean isAboutToPause() {
        return quartzTask.isAboutToPause() || isPausedByMarkerLock();
    }

    @Override
    public boolean isRunning() {
        return isRunningOnCluster();
    }

    @Override
    public boolean isRunningOnCluster() {
        return quartzTask.isRunning() || isMyClusterSingletonLockClaimed();
    }

    @Override
    public boolean processActiveOnCluster() {
        return processActive() || isRunningOnCluster();
    }

    @Override
    public boolean blockIfPausedAndShouldBreak() {
        boolean shouldStop = quartzTask.blockIfPausedAndShouldBreak();
        if (shouldStop) {
            return true;
        }

        int tries = ConstantValues.taskCompletionLockTimeoutRetries.getInt();
        long timeToPauseMillis = TimeUnit.SECONDS.toMillis(ConstantValues.taskPauseTimeoutSecs.getLong());

        while (isPausedByMarkerLock()) {
            log.debug("Entering paused wait for: {} milliseconds on: {}", timeToPauseMillis, this);
            try {
                //noinspection BusyWait
                Thread.sleep(timeToPauseMillis);
            } catch (InterruptedException e) {
                log.warn("Interrupted while trying to pause task: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
            tries--;
            if (tries <= 0) {
                throw new LockingException("Task: " + getTaskId() + " paused for more than: " +
                        ConstantValues.taskCompletionLockTimeoutRetries.getInt() + " times.");
            }
            log.trace("Job: {} waited on pause by marker for: {} seconds", this, timeToPauseMillis);
        }
        return isStoppedByMarkerLock();
    }

    private void doExecuteInLock(InternalQuartzCommand command) {
        String taskLockKey = getTaskLockKey();
        String taskName = getTaskName();
        String taskId = getTaskId();
        log.debug("Trying to execute task: {} with lock key: {} for task ID: {}.", taskName, taskLockKey, taskId);
        new LockingUtilImpl().clusterExclusive(conflictsGuard, taskLockKey, () -> {
            try {
                log.debug("Acquired lock on task: {} with lock key: {} for task ID: {}.", taskName, taskLockKey,
                        taskId);
                command.runCommand();
            } catch (JobExecutionException e) {
                log.error("Unable to execute job", e);
                throw new IllegalStateException("Unable to execute job", e);
            }
        });
        log.debug("Finished execution of task: {} with lock key: {} for task ID: {}.", taskName, taskLockKey, taskId);
    }

    private boolean isMyClusterSingletonLockClaimed() {
        String taskLockKey = getTaskLockKey();
        boolean locked = conflictsGuard.isLocked(taskLockKey);
        if (locked) {
            log.debug("Found task lock key: '{}' is taken. Task is already running", taskLockKey);
            return true;
        }
        return false;
    }

    private String getTaskLockKey() {
        return getTaskLockKeyIncludingKeyAttributes(Arrays.asList(getType().getAnnotation(JobCommand.class)
                .keyAttributes()));
    }

    private void acquireMarkerLock(String uniqueMarkerLock) {
        try {
            ConflictGuard conflictGuard = conflictsGuard.getLock(uniqueMarkerLock);
            if (!conflictGuard.isLocked()) {
                log.debug("Attempting stop task by acquiring stop marker lock: {}", uniqueMarkerLock);
                conflictGuard.tryToLock(0L, SECONDS);
                log.debug("Successfully stopped task by acquiring stop marker lock: {}", uniqueMarkerLock);
                markerLocks.add(uniqueMarkerLock);
            }
        } catch (InterruptedException e) {
            log.error("Interrupting while trying stop task by acquiring marker lock: {}", uniqueMarkerLock, e);
            Thread.currentThread().interrupt();
        }
    }

    private boolean isStoppedByMarkerLock() {
        if (isStoppedByTaskIdMarkerLock()) {
            return true;
        }

        boolean isStoppedByMarkerLock;
        if (isUniqueKeyAttributesTask()) {
            isStoppedByMarkerLock =
                    conflictsGuard.isLockedByPrefixExcluding(stopMarkerLockPrefix, getType().getSimpleName(), true);
        } else {
            isStoppedByMarkerLock = conflictsGuard.isLockedByPrefixExcluding(stopMarkerLockPrefix,
                    stopMarkerLockPrefix + quartzTask.getTaskId(), false);
        }
        if (isStoppedByMarkerLock) {
            log.debug("Stop marker lock found for job with task ID: {}", getTaskId());
        }
        return isStoppedByMarkerLock;
    }

    private boolean isPausedByMarkerLock() {
        return isPausedByTaskIdMarkerLock() || isPausedByPrefixMarkerLock();
    }

    private boolean isPausedByPrefixMarkerLock() {
        boolean isPausedByPrefixMarkerLock = conflictsGuard.isLockedByPrefixExcluding(pauseMarkerLockPrefix,
                pauseMarkerLockPrefix + quartzTask.getTaskId(), false);
        if (isPausedByPrefixMarkerLock) {
            log.debug("Pause marker lock found for job with task ID: {}", getTaskId());
            return true;
        }
        return false;
    }

    private boolean isStoppedByTaskIdMarkerLock() {
        boolean locked = conflictsGuard.isLocked(taskIdStopMarkerLock);
        if (locked) {
            log.debug("Stop task ID marker lock found for job with task ID: {}. Removing the lock and stopping",
                    getTaskId());
            try {
                conflictsGuard.forceUnlock(taskIdPauseMarkerLock);
            } catch (Exception e) {
                log.warn("Failed to unlock: {}. Task will stop but lock will remain in DB", taskIdPauseMarkerLock, e);
                return true;
            }
        }
        return locked;
    }

    private boolean isPausedByTaskIdMarkerLock() {
        boolean locked = conflictsGuard.isLocked(taskIdPauseMarkerLock);
        if (locked) {
            log.debug("Pause task ID marker lock found for job with task ID: {}", getTaskId());
            return true;
        }
        return false;
    }

    private boolean isUniqueKeyAttributesTask() {
        return Arrays.stream(getMyJobCommand().keyAttributes())
                .anyMatch(keyAttribute -> quartzTask.getAttributeMap().containsKey(keyAttribute));
    }

    private JobCommand getMyJobCommand() {
        return this.getType().getAnnotation(JobCommand.class);
    }

    /**
     * This interface is used over Lombok {@link Delegate}'s annotation.
     * Each method definition here signifies to the {@link Delegate} not to call the delegated instance method, but
     * rather to expect a concrete implementation in this class instead. All other methods are implicitly called from
     * this class instance to the delegated class instance
     */
    @SuppressWarnings("unused")
    private interface ExcludedMethods {

        boolean isRunning();

        int getRunId();

        void start(InternalQuartzCommand command);

        void stop(boolean wait, String stoppingTaskId);

        boolean resume(String stoppingTaskId);

        void pause(boolean wait, String pausingTaskId);

        boolean isRunningOnCluster();

        boolean processActiveOnCluster();

        boolean canStart();

        boolean blockIfPausedAndShouldBreak();

        boolean isAboutToPause();
    }
}