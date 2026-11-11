package org.artifactory.storage.fs.service;

import jakarta.annotation.Nullable;
import org.artifactory.common.ConstantValues;
import org.artifactory.quartz.task.history.QuartzTaskHistoryInfo;

import java.util.Set;

import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;


/**
 * A business service to interact with the quartz_task_history table
 * @author Mor Merhav
 */
public interface QuartzTaskHistoryService {

    /**
     * Insert a quartz task history record, with provided external ID and task type,
     * including: scheduled date = last modified date = current time, status = PENDING and serverId = current serverId
     *
     * @param externalId the quartz task history external ID.
     * @param taskType   the quartz task history task type.
     * @param taskId     the unique run task ID.
     * @return the inserted record's generated run ID.
     * @throws org.artifactory.storage.StorageException in case of failure
     * @throws IllegalArgumentException if either <b>taskType</b> or <b>taskId</b> are null
     */
    int insert(String externalId, String taskType, String taskId, boolean manualTrigger, String username);

    /**
     * Fetch quartz task history record by its run ID
     * @param runId the runId of the record to return
     * @return the quartz task history record if found by the run ID, null otherwise.
     * @throws org.artifactory.storage.StorageException in case of failure
     */
    @Nullable
    QuartzTaskHistoryInfo get(int runId);

    /**
     * Get all records which were not soft deleted
     * @return all non-soft-deleted records
     * @throws org.artifactory.storage.StorageException in case of failure
     */
    Set<QuartzTaskHistoryInfo> getAllNonDeleted();

    /**
     * Find a non-deleted record which is in a running status (defined in
     * {@link Status#getRunningStatuses()}) by its external ID.
     *
     * @param externalId the external ID to find the running record by
     * @return the running record if found, {@code null} otherwise.
     * @throws IllegalArgumentException if {@code externalId} is blank
     */
    QuartzTaskHistoryInfo findRunningTaskByExternalId(String externalId);

    /**
     * Partial update of task history record by the runId, ignoring -1 and <code>null</code> fields
     * @param quartzTaskHistoryInfo the record to update
     * @return true if update was successful, false otherwise
     */
    boolean update(QuartzTaskHistoryInfo quartzTaskHistoryInfo);

    /**
     * Update a record status by run ID
     * @param runId the run ID by which to update
     * @param status      the custom status to update to
     * @return true if record was updated, false otherwise.
     * @throws org.artifactory.storage.StorageException in case of failure
     * @throws IllegalArgumentException in case <b>status</b> is null
     */
    boolean updateStatus(int runId, Status status);

    /**
     * Update record status and status message by run ID
     * @param runId the run ID by which to update
     * @param status      the custom status to update to
     * @param statusMessage the status message to update to
     * @return true if record was updated, false otherwise.
     * @throws org.artifactory.storage.StorageException in case of failure
     * @throws IllegalArgumentException in case <b>status</b> is blank.
     */
    boolean updateStatus(int runId, Status status, String statusMessage);

    /**
     * Permanently delete a quartz task history record by its run ID.
     *
     * @param runId the run ID to hard-delete by
     * @return true if a record has been hard-deleted, false otherwise.
     * @throws org.artifactory.storage.StorageException in case of failure
     */
    boolean hardDelete(int runId);

    /**
     * Mark a quartz task history record as deleted by setting the deleted column to {@code true}.
     *
     * @param runId the run ID to soft-delete by.
     * @return true if a record has been soft-deleted, false otherwise.
     * @throws org.artifactory.storage.StorageException in case of failure
     */
    boolean softDelete(int runId);

    /**
     * Truncate the table
     * @throws org.artifactory.storage.StorageException in case of failure
     */
    void deleteAll();

    /**
     * Marks any records that are marked as: {@link Status#PENDING_EXPIRED_CHECK} as: {@link Status#EXPIRED}
     */
    void markExpiredTasks();

    /**
     * Marks any records that are in any of: {@link Status#getRunningStatuses()} for longer than:
     * {@link ConstantValues#quartzTaskRunExpirationTimeoutSeconds} as: {@link Status#PENDING_EXPIRED_CHECK}
     */
    void markPendingExpiredCheckTasks();

    /**
     * Marks any records that their server is considered stale as {@link Status#ORPHANED}
     */
    void markOrphanedTasks();

    /**
     * Returns a set of not finished (due to node restart, for ex) tasks.
     *
     * @return set of the tasks
     */
    Set<QuartzTaskHistoryInfo> getNotFinishedTasks();
}