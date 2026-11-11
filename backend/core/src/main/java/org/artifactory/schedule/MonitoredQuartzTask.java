/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2023 JFrog Ltd.
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

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.quartz.task.history.QuartzTaskHistoryInfo;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.quartz.JobExecutionException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.COMPLETED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.FAILED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PAUSED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.SKIPPED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.STOPPED;

/**
 * @author Mor Merhav
 */
@Slf4j
public class MonitoredQuartzTask implements InternalTask {

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
    private final QuartzTaskHistoryService taskHistoryService;
    @Getter
    private final String externalId;
    private final AtomicBoolean completed = new AtomicBoolean(true);
    @Setter
    private Status completionStatus;
    private int runId;
    private String statusMessage;

    MonitoredQuartzTask(InternalTask quartzTask, QuartzTaskHistoryService taskHistoryService,
            @Nullable String externalId) {
        Objects.requireNonNull(quartzTask, "quartzTask must be non-null");
        Objects.requireNonNull(taskHistoryService, "taskHistoryService must be non-null");
        this.quartzTask = quartzTask;
        this.taskHistoryService = taskHistoryService;
        this.externalId = externalId;
        if (isManuallyActivated()) {
            runId = createHistoryRecord();
        }
    }

    @Override
    public void start(InternalQuartzCommand command) throws JobExecutionException {
        if (!isManuallyActivated()) {
            log.trace("Overriding run ID: {}", runId);
            runId = createHistoryRecord();
            log.trace("New run ID: {}", runId);
        }
        quartzTask.start(command);
    }

    @Override
    public boolean resume() {
        boolean resumed = quartzTask.resume();
        if (resumed && isRunning()) {
            taskHistoryService.updateStatus(runId, IN_PROGRESS);
        }
        return resumed;
    }

    @Override
    public boolean resume(String stoppingTaskToken) {
        boolean resumed = quartzTask.resume(stoppingTaskToken);
        if (resumed && isRunning()) {
            taskHistoryService.updateStatus(runId, IN_PROGRESS);
        }
        return resumed;
    }

    @Override
    public void fail(String errorMsg) {
        completed.set(false);
        boolean updated = updateTaskFinished(FAILED, errorMsg);
        if (!updated) {
            log.warn("Failed to update failure status for task with run ID: {}", runId);
        }
    }

    @Override
    public void skip(String skipMsg) {
        completed.set(false);
        updateTaskFinished(SKIPPED, skipMsg);
    }

    @Override
    public void completed() {
        quartzTask.completed();
        if (completed.get()) {
            Status successStatus = completionStatus == null ? COMPLETED : completionStatus;
            updateTaskFinished(successStatus, statusMessage);
        }
    }

    @Override
    public void setCompletionStatus(Status completionStatus, String statusMessage) {
        this.completionStatus = completionStatus;
        this.statusMessage = statusMessage;
    }

    @Override
    public void setCurrentStatus(Status currentStatus) {
        setCurrentStatus(currentStatus, null);
    }

    @Override
    public void setCurrentStatus(Status currentStatus, String statusMessage) {
        taskHistoryService.updateStatus(runId, currentStatus, statusMessage);
    }

    /**
     * See the general documentation on the interface level: {@link Task#blockIfPausedAndShouldBreak()}
     *
     * Since in the case of pause, {@link HaQuartzTask#blockIfPausedAndShouldBreak()} and
     * {@link QuartzTask#blockIfPausedAndShouldBreak()} actually pause the thread, this implementation uses
     * {@link InternalTask#isAboutToPause()} in order to change the task history status to {@code PAUSED} before
     * entering the actual wait loop. Finally, sets either {@link Status#STOPPED} or {@link Status#IN_PROGRESS}
     * accordingly.
     *
     * @return {@code true} if should stop, {@code false} otherwise.
     */
    @Override
    public boolean blockIfPausedAndShouldBreak() {
        boolean pausedAndShouldBreak;
        if (quartzTask.isAboutToPause()) {
            taskHistoryService.updateStatus(runId, PAUSED);
            pausedAndShouldBreak = quartzTask.blockIfPausedAndShouldBreak();
        } else {
            pausedAndShouldBreak = quartzTask.blockIfPausedAndShouldBreak();
        }
        if (pausedAndShouldBreak) {
            completed.set(false);
            updateTaskFinished(STOPPED, null);
        } else {
            taskHistoryService.updateStatus(runId, IN_PROGRESS);
        }
        return pausedAndShouldBreak;
    }

    @Override
    public int getRunId() {
        return runId;
    }

    @Override
    public String toString() {
        return getTaskId();
    }

    private int createHistoryRecord() {
        return taskHistoryService.insert(externalId, getType().getSimpleName(), getTaskId(), isManuallyActivated(),
                getUsername());
    }

    private boolean updateTaskFinished(Status status, String statusMsg) {
        QuartzTaskHistoryInfo taskHistoryInfo = QuartzTaskHistoryInfo.builder()
                .runId(runId)
                .status(status)
                .statusMessage(statusMsg)
                .finishedDate(System.currentTimeMillis())
                .build();
        return taskHistoryService.update(taskHistoryInfo);
    }

    /**
     * This interface is used over Lombok {@link Delegate}'s annotation.
     * Each method definition here signifies to the {@link Delegate} not to call the delegated instance method, but
     * rather to expect a concrete implementation in this class instead. All other methods are implicitly called from
     * this class instance to the delegated class instance
     */
    @SuppressWarnings("unused")
    private interface ExcludedMethods {

        int getRunId();

        void start(InternalQuartzCommand command);

        boolean resume();

        boolean resume(String stoppingTaskToken);

        void fail(String errorMsg);

        void skip(String skipMsg);

        void completed();

        void setCompletionStatus(Status completionStatus);

        void setCompletionStatus(Status completionStatus, String statusMessage);

        void setCurrentStatus(Status status);

        void setCurrentStatus(Status status, String statusMessage);

        boolean blockIfPausedAndShouldBreak();
    }
}