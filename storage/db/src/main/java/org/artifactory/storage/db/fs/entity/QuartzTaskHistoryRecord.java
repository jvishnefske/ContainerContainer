package org.artifactory.storage.db.fs.entity;

import lombok.Builder;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;

/**
 * Represents a record in the quartz_task_history table.
 *
 * @author Mor Merhav
 */
@Builder(toBuilder = true)
public record QuartzTaskHistoryRecord(int runId, String externalId, String taskType,
                                      long startedDate, long finishedDate, long lastModifiedDate, Status status,
                                      String statusMessage, boolean deleted, String taskId, String serverId,
                                      boolean manualTrigger, String username) {

    public QuartzTaskHistoryRecord(String externalId, String taskType, Status status,
            String taskId, String serverId, boolean manualTrigger, String username) {
        this(0, externalId, taskType, System.currentTimeMillis(),
                -1, System.currentTimeMillis(), status, null, false, taskId, serverId, manualTrigger, username);
    }

    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    public static class QuartzTaskHistoryRecordBuilder {
        private long lastModifiedDate = System.currentTimeMillis();
        private long startedDate = lastModifiedDate;
        private long finishedDate = -1L;
    }
}