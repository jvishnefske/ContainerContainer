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

import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.spring.ReloadableBean;
import org.jfrog.common.concurrency.ConflictsGuard;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author yoavl
 */
public interface TaskService extends ReloadableBean {

    /**
     * Schedules a tasks and returns its unique ID
     *
     * @param task           The task to add to the scheduler
     * @param waitForRunning If true, the method will return only after the task has started executing
     * @return The unique ID of this task
     */
    String startTask(Task task, boolean waitForRunning);

    /**
     * Starts a task and returns its ID
     *
     * @param propagateToAffinity propagate to an Affinity node on an HA environment when
     * {@link JobCommand#clusterSingleton()} is set to true, and current member is not allowed to run the task
     * @return the ID of this task
     */
    String startTask(Task task, boolean waitForRunning, boolean propagateToAffinity);

    /**
     * Cancels and stops the task
     *
     * @param taskId The task ID
     * @param wait  Whether to return immediately or wait for the tesk to be stopped
     */
    void stopTask(String taskId, boolean wait);

    /**
     * Pause a task
     */
    void pauseTask(String taskId, boolean wait);

    /**
     * If the task has been canceled (unscheduled) returns true to signal the caller that it needs to break from the
     * task's execution loop. If the task has been paused will simply pause the caller (and thus, the caller's execution
     * loop).
     *
     * @return true if the caller needs break from the task's callback execution loop
     */
    boolean pauseOrBreak();

    /**
     * Cancels and stops all active tasks of the specified type
     *
     * @return IDs of stopped tasks
     */
    List<String> stopTasks(Class<? extends TaskCallback> callbackType);

    /**
     * Resumes a paused task
     *
     * @param taskId             the task to resume's taskId
     * @param resumingTaskId the taskId of the task which caused the pause
     */
    boolean resumeTask(String taskId, String resumingTaskId);

    /**
     * Resumes a paused task
     *
     * @param taskId the task to resume's ID
     */
    boolean resumeTask(String taskId);

    /**
     * Cancels (unschedules) a task
     */
    void cancelTask(String taskId, boolean wait);

    /**
     * Cancels (unschedules) a task with given timeout
     */
    void cancelTask(String taskId, boolean wait, long timeout);

    /**
     * Cancels all tasks of a certain type
     */
    void cancelTasks(@Nonnull Class<? extends TaskCallback> callbackType, boolean wait);

    /**
     * Cancels all tasks
     */
    void cancelAllTasks(boolean wait);

    /**
     * Reschedule the task
     * @param taskId - the task ID
     */
    void rescheduleTask(String taskId);
    /**
     * Awaits task execution to be finished
     *
     * @return true if execution completed, otherwise false if completed outside the execution loop
     */
    boolean waitForTaskCompletion(String taskId);

    /**
     * Returns the actual task object.
     * <p/>
     * THIS SHOULD BE CALLED BY PRIVATE API ONLY!
     * <p/>
     * Only exists since spring jdk-proxies with which the internal api works are interfaces.
     */
    Task getActiveTask(String taskId, boolean warnIfMissing);

    /**
     * @param callbackType Type of callback to check if exists
     * @return True if a task with the callback type already exists
     */
    boolean hasTaskOfType(Class<? extends TaskCallback> callbackType);

    void cancelTasks(@Nullable Predicate<Task> predicate, boolean wait);

    List<Task> getActiveTasks(@Nonnull Predicate<Task> predicate);

    /**
     * Checks if there are any active tasks for the provided predicate.
     * @param predicate specific criterion to check the type of the {@link Task}.
     * @return true in case there is at least one active task.
     */
    boolean hasActiveTasks(@Nonnull Predicate<Task> predicate);

    void checkCanStartManualTask(Task task, Class<? extends TaskCallback> typeToRun, MutableStatusHolder statusHolder,
            Object... keyValues);

    void checkCanStartManualTask(Class<? extends TaskCallback> typeToRun, MutableStatusHolder statusHolder,
            Map<String, Object> keyValues);

    void stopRelatedTasks(Class<? extends TaskCallback> typeToRun, List<String> stoppedTasksIds,
            Object... keyValues);

    boolean waitForTaskCompletion(String taskId, long timeout);

    ConflictsGuard<String> getConflictsGuard();

    void canBeStarted(Task task);

    /**
     * Check if a task can be manually run and start it
     *
     * @param quartzCommand the task's quartz command to run
     * @param externalId optional external ID
     * @param statusHolder status holder providing data about the validations
     * @param taskAttributesMap attributes to add to the task
     * @param initialDelay delay for scheduler
     * @param description the task description
     * @return the task if started, {@code null} otherwise.
     */
    Task startManualTask(Class<? extends QuartzCommand> quartzCommand, @Nullable String externalId,
            MutableStatusHolder statusHolder, Map<String, Object> taskAttributesMap, int initialDelay,
            String description);

    /**
     * Check if a task can be manually run and start it, with optional propagation to affinity node
     *
     * @param quartzCommand the task's quartz command to run
     * @param externalId optional external ID
     * @param statusHolder status holder providing data about the validations
     * @param taskAttributesMap attributes to add to the task
     * @param initialDelay delay for scheduler
     * @param description the task description
     * @param propagateToAffinity if true, propagate to an Affinity node on an HA environment when
     *        current member is not allowed to run the task
     * @return the task if started, {@code null} otherwise.
     */
    Task startManualTask(Class<? extends QuartzCommand> quartzCommand, @Nullable String externalId,
            MutableStatusHolder statusHolder, Map<String, Object> taskAttributesMap, int initialDelay,
            String description, boolean propagateToAffinity);
}