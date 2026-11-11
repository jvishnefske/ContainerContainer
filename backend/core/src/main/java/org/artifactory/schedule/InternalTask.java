package org.artifactory.schedule;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;

/**
 * @author Mor Merhav
 */
public interface InternalTask extends Task {

    boolean isClusterSingleton();

    void setClusterSingleton(boolean clusterSingleton);

    void schedule(boolean waitForRunning);

    void cancel(boolean wait, long timeout);

    void pause(boolean wait);

    void pause(boolean wait, String stoppingTaskToken);

    /**
     * Stops but does not unschedule the task (can transition back to running state)
     */
    void stop(boolean wait);

    void stop(boolean wait, String stoppingTaskToken);

    boolean resume();

    void start(InternalQuartzCommand command) throws JobExecutionException;

    boolean resume(String stoppingTaskToken);

    void completed();

    /**
     * Wait for the task to stop running
     */
    boolean waitForCompletion(long timeout);

    boolean canStart();

    boolean started();

    void lockState();

    void unlockState();

    boolean isSingleton();

    void setSingleton(boolean singleton);

    /**
     *
     * @return true if this task should rescheduled or false otherwise
     */
    boolean shouldRescheduleTask();

    /**
     * Configures the task if needed to be rescheduled. Default to false
     * @param shouldReScheduleTask - The value if the task should be rescheduled
     */
    void setShouldRescheduleTask(boolean shouldReScheduleTask);

    /**
     * Relevant only for jobs where {@link JobCommand#monitored()} is {@code true}
     * Set a custom completion status to be written during {@link #completed()} instead of the standard "COMPLETED"
     * on the corresponding quartz_task_history table entry
     * @param completionStatus the custom completion status
     */
    default void setCompletionStatus(Status completionStatus) {

    }

    /**
     * Relevant only for jobs where {@link JobCommand#monitored()} is {@code true}
     * Set a custom completion status and message to be written during {@link #completed()} instead of the standard
     * "COMPLETED" on the corresponding quartz_task_history table entry
     * @param completionStatus the custom completion status
     * @param statusMessage the status message
     */
    default void setCompletionStatus(Status completionStatus, String statusMessage) {

    }

    /**
     * Relevant only for jobs where {@link JobCommand#monitored()} is {@code true}
     * Set a custom current status on the corresponding quartz_task_history table entry
     * @param status the custom current status
     */
    default void setCurrentStatus(Status status) {
        setCurrentStatus(status, null);
    }

    /**
     * Relevant only for jobs where {@link JobCommand#monitored()} is {@code true}
     * Set a custom current status and message on the corresponding quartz_task_history table entry
     * @param status the custom current status
     * @param statusMessage the status message
     */
    default void setCurrentStatus(Status status, String statusMessage) {

    }

    /**
     * Rescheduled the given task to re-run.
     * @param waitBeforeExecute the time to wait before executing the task again.
     */
    void rescheduleTask(long waitBeforeExecute);

    boolean isManuallyActivated();

    void setManuallyActivated(boolean manuallyActivated);

    boolean wasCompleted();

    String getTaskLockKeyIncludingKeyAttributes(List<String> keyAttributes);

    boolean waitingForProcess();

    /**
     * Starts or schedules the task
     */
    void scheduleTask();

    /**
     * Stops or unschedules the task
     */
    void cancelTask();

    /**
     * Called when an exception is thrown from within the task's concrete job run
     * @param errorMsg the detailed error message
     */
    default void fail(String errorMsg) {

    }

    /**
     * Called when {@link TaskCallback#beforeExecute(JobExecutionContext)} returns {@code false}.
     * @param skipMsg the detailed skip message
     */
    default void skip(String skipMsg) {

    }

    /**
     * @return {@code true} if a task is about to pause, {@code false} otherwise.
     */
    boolean isAboutToPause();

    default Authentication getAuthentication() {
        return (Authentication) getAttribute(Task.TASK_AUTHENTICATION);
    }

    default String getUsername() {
        return isManuallyActivated() ? getAuthentication().getPrincipal().toString() : SCHEDULER_USERNAME;
    }
}
