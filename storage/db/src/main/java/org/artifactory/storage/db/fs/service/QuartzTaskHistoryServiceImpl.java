package org.artifactory.storage.db.fs.service;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.quartz.task.history.QuartzTaskHistoryInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryDao;
import org.artifactory.storage.db.fs.entity.QuartzTaskHistoryRecord;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.storage.fs.service.QuartzTaskHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.quartzTaskRunExpirationTimeoutSeconds;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.EXPIRED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.IN_PROGRESS;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.ORPHANED;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.PENDING_EXPIRED_CHECK;
import static org.artifactory.quartz.task.history.QuartzTaskHistoryInfo.Status.getRunningStatuses;


@Slf4j
@Service
public class QuartzTaskHistoryServiceImpl implements QuartzTaskHistoryService {

    private final QuartzTaskHistoryDao dao;
    private final ArtifactoryServersCommonService serversCommonService;

    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    private Supplier<Long> timeSupplier = System::currentTimeMillis;

    @Autowired
    public QuartzTaskHistoryServiceImpl(QuartzTaskHistoryDao dao,
            ArtifactoryServersCommonService serversCommonService) {
        this.dao = dao;
        this.serversCommonService = serversCommonService;
    }

    @Override
    public int insert(String externalId, String taskType, String taskId, boolean manualTrigger, String username) {
        if (StringUtils.isBlank(taskType)) {
            throw new IllegalArgumentException("taskType cannot be blank.");
        }
        if (StringUtils.isBlank(taskId)) {
            throw new IllegalArgumentException("taskId cannot be blank.");
        }
        if (StringUtils.isBlank(username)) {
            throw new IllegalArgumentException("username cannot be blank.");
        }

        externalId = externalId != null ? externalId : StringUtils.EMPTY;

        try {
            QuartzTaskHistoryRecord taskHistoryRecord = QuartzTaskHistoryRecord.builder()
                    .externalId(externalId)
                    .taskType(taskType)
                    .taskId(taskId)
                    .status(IN_PROGRESS)
                    .manualTrigger(manualTrigger)
                    .serverId(ContextHelper.get().getServerId())
                    .username(username)
                    .build();
            return dao.insert(taskHistoryRecord);
        } catch (SQLException | IllegalStateException e) {
            log.error("Failed to insert quartz task history entry with external ID: {} and task type: {}", externalId,
                    taskType, e);
            throw new StorageException(e);
        }
    }

    @Nullable
    @Override
    public QuartzTaskHistoryInfo get(int runId) {
        try {
            return toQuartzTaskHistoryInfo(dao.get(runId));
        } catch (SQLException e) {
            log.error("Failed to fetch quartz task history entry with run ID: {}", runId, e);
            throw new StorageException(e);
        }
    }

    @Override
    public Set<QuartzTaskHistoryInfo> getAllNonDeleted() {
        try {
            return dao.getAllNonDeleted().stream().map(this::toQuartzTaskHistoryInfo).collect(Collectors.toSet());
        } catch (SQLException e) {
            log.error("Failed to fetch all non-deleted quartz task history entries.", e);
            throw new StorageException(e);
        }
    }

    @Override
    public QuartzTaskHistoryInfo findRunningTaskByExternalId(String externalId) {
        if (StringUtils.isBlank(externalId)) {
            throw new IllegalArgumentException("externalId cannot be blank");
        }

        try {
            return toQuartzTaskHistoryInfo(
                    dao.findByExternalIdAndStatusIsOneOf(externalId, getRunningStatuses()));
        } catch (SQLException e) {
            log.error("Failed to find running task by external ID: {} and statuses: {}", externalId,
                    getRunningStatuses());
            throw new StorageException(e);
        }
    }

    @Override
    public boolean update(QuartzTaskHistoryInfo quartzTaskHistoryInfo) {
        try {
            return dao.update(toUpdateRecord(quartzTaskHistoryInfo));
        } catch (SQLException e) {
            log.error("Failed to update task history record to: {}", quartzTaskHistoryInfo, e);
            throw new StorageException(e);
        }
    }

    @Override
    public boolean updateStatus(int runId, Status status, String statusMessage) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null.");
        }
        if (StringUtils.isBlank(statusMessage)) {
            return updateStatus(runId, status);
        }

        try {
            return dao.updateStatus(runId, status, statusMessage);
        } catch (SQLException e) {
            log.error(
                    "Failed to update status of quartz task history entry with run ID: {}, status: {}, status " +
                            "message: {}",
                    runId, status, statusMessage, e);
            throw new StorageException(e);
        }
    }

    @Override
    public boolean updateStatus(int runId, Status status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null.");
        }

        try {
            return dao.updateStatus(runId, status);
        } catch (SQLException e) {
            log.error("Failed to update status of quartz task history entry with run ID: {}, status: {}",
                    runId, status, e);
            throw new StorageException(e);
        }
    }

    @Override
    public boolean hardDelete(int runId) {
        try {
            return dao.hardDelete(runId);
        } catch (SQLException e) {
            log.error("Failed to hard-delete quartz task history entry with run ID: {}", runId, e);
            throw new StorageException(e);
        }
    }

    @Override
    public boolean softDelete(int runId) {
        try {
            return dao.softDelete(runId);
        } catch (SQLException e) {
            log.error("Failed to soft-delete quartz task history entry with run ID: {}", runId, e);
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            dao.deleteAll();
        } catch (SQLException e) {
            log.error("Failed to truncate quartz_task_history table", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void markExpiredTasks() {
        log.debug("Starting to scan and mark expired tasks...");
        try {
            int numMarkedTasks = dao.updateToStatusWhereStatus(EXPIRED, PENDING_EXPIRED_CHECK);
            log.debug("Completed marking {} expired tasks", numMarkedTasks);
        } catch (SQLException e) {
            log.error("Failed to mark expired tasks", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void markPendingExpiredCheckTasks() {
        log.debug("Starting to scan and mark pending-expired-check tasks...");
        long expirationTimeoutMillis = TimeUnit.SECONDS.toMillis(quartzTaskRunExpirationTimeoutSeconds.getLong());
        long expirationTimeEpoch = timeSupplier.get() - expirationTimeoutMillis;
        try {
            int numMarked =
                    dao.updateToStatusWhereInStatusesAndLastModifiedBefore(PENDING_EXPIRED_CHECK, getRunningStatuses(),
                            expirationTimeEpoch);
            log.debug("Completed marking {} pending-expired-check tasks with expirationTimeEpoch: {}",
                    numMarked, expirationTimeEpoch);
        } catch (SQLException e) {
            log.error("Failed to mark pending-expired-check tasks", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void markOrphanedTasks() {
        log.debug("Starting to scan and mark orphaned tasks...");
        try {
            Set<QuartzTaskHistoryRecord> runningTaskRecords = dao.findByStatuses(Status.getRunningStatuses());
            if (!runningTaskRecords.isEmpty()) {
                log.debug("Found {} running tasks", runningTaskRecords.size());

                Set<Integer> orphanedRunIds = runningTaskRecords.stream()
                        .filter(this::isServerInactiveForRecord)
                        .map(QuartzTaskHistoryRecord::runId)
                        .collect(Collectors.toUnmodifiableSet());

                if (orphanedRunIds.isEmpty()) {
                    log.debug("Found no orphaned tasks");
                    return;
                }
                log.debug("Marking run ids: {} as orphaned", orphanedRunIds);
                int numMarked = dao.updateStatusWhereRunIdIn(ORPHANED, orphanedRunIds);
                log.debug("Completed marking {} orphaned tasks", numMarked);
            } else {
                log.debug("Found no running tasks");
            }
        } catch (SQLException e) {
            log.error("Failed to mark orphaned tasks", e);
            throw new StorageException(e);
        }
    }

    private boolean isServerInactiveForRecord(QuartzTaskHistoryRecord runningTaskRecord) {
        return serversCommonService.getAllArtifactoryServersInfo().stream()
                .anyMatch(serverInfo -> serverInfo.getServerId()
                        .equals(runningTaskRecord.serverId()) && !serverInfo.isActive());
    }

    @Override
    public Set<QuartzTaskHistoryInfo> getNotFinishedTasks() {
        try {
            Set<QuartzTaskHistoryRecord> infos = dao.findByStatuses(Set.of(ORPHANED, EXPIRED));
            return infos.stream().map(this::toQuartzTaskHistoryInfo).collect(Collectors.toSet());
        } catch (SQLException e) {
            log.error("Failed to get not finished tasks", e);
            throw new StorageException(e);
        }
    }

    private QuartzTaskHistoryInfo toQuartzTaskHistoryInfo(QuartzTaskHistoryRecord taskHistoryRecord) {
        if (taskHistoryRecord == null) {
            return null;
        }
        return QuartzTaskHistoryInfo.builder()
                .runId(taskHistoryRecord.runId())
                .externalId(taskHistoryRecord.externalId())
                .taskType(taskHistoryRecord.taskType())
                .startedDate(taskHistoryRecord.startedDate())
                .finishedDate(taskHistoryRecord.finishedDate())
                .lastModifiedDate(taskHistoryRecord.lastModifiedDate())
                .status(taskHistoryRecord.status())
                .statusMessage(taskHistoryRecord.statusMessage())
                .deleted(taskHistoryRecord.deleted())
                .taskId(taskHistoryRecord.taskId())
                .serverId(taskHistoryRecord.serverId())
                .manualTrigger(taskHistoryRecord.manualTrigger())
                .username(taskHistoryRecord.username())
                .build();
    }

    private QuartzTaskHistoryRecord toUpdateRecord(QuartzTaskHistoryInfo taskHistoryInfo) {
        return QuartzTaskHistoryRecord.builder()
                .runId(taskHistoryInfo.runId())
                .startedDate(taskHistoryInfo.startedDate())
                .finishedDate(taskHistoryInfo.finishedDate())
                .status(taskHistoryInfo.status())
                .statusMessage(taskHistoryInfo.statusMessage())
                .lastModifiedDate(timeSupplier.get())
                .build();
    }
}