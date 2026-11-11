package org.artifactory.storage.db.policy.cleanup.service;

import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.RunSummaryConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRunRequest;
import org.artifactory.storage.db.policy.cleanup.CleanupRunConfig;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;
import org.artifactory.storage.db.policy.cleanup.dao.CleanupRunSummaryDao;
import org.artifactory.storage.db.policy.dao.RetentionRunSummaryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.storage.db.policy.RetentionRunSummary.RetentionType.CLEANUP;
import static org.artifactory.storage.db.policy.RetentionRunSummary.fromCleanupSummaryType;

/**
 * @author vovam
 */
@Slf4j
@Service
public class CleanupRunSummaryServiceImpl implements CleanupRunSummaryService {

    private final CleanupRunSummaryDao dao;
    private final RetentionRunSummaryDao retentionRunSummaryDao;

    @Autowired
    public CleanupRunSummaryServiceImpl(CleanupRunSummaryDao dao, RetentionRunSummaryDao retentionRunSummaryDao) {
        this.dao = dao;
        this.retentionRunSummaryDao = retentionRunSummaryDao;
    }

    @Override
    public Set<CleanupRunPolicyKeyAndRunId> getRunsForInternalCleanup(long timestamp) {
        try {
            return dao.getRunsForInternalCleanup(timestamp);
        } catch (SQLException e) {
            log.error("Failed to get runs for internal cleanup by timestamp: {}", timestamp, e);
            throw new StorageException(e);
        }
    }

    @Override
    public void markAsCleanedInternally(String policyKey, int policyRunId) {
        try {
            dao.markAsCleanedInternally(policyKey, policyRunId);
        } catch (SQLException e) {
            log.error("Failed to mark as cleaned internally. Policy key: {}, policy run: {}", policyKey, policyRunId,
                    e);
            throw new StorageException(e);
        }
    }

    @Override
    public void insert(CleanupRunSummary summary) {
        try {
            dao.insert(summary);
            retentionRunSummaryDao.insert(mapToRetentionRunSummary(summary));
        } catch (SQLException e) {
            log.error("Failed to insert cleanup run summary record. Policy key: {}, policy run: {}",
                    summary.policyKey(), summary.policyRunId(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public int update(CleanupRunSummary summary) {
        try {
            int rowsAffected = dao.update(summary);
            retentionRunSummaryDao.update(mapToRetentionRunSummary(summary));
            return rowsAffected;
        } catch (SQLException e) {
            log.error("Failed to update cleanup run summary record. Policy key: {}, policy run: {}",
                    summary.policyKey(), summary.policyRunId(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public Map<CleanupRunPolicyKeyAndRunId, Optional<CleanupRunSummary>> getRuns(Set<CleanupRunPolicyKeyAndRunId> ids) {
        try {
            Map<CleanupRunPolicyKeyAndRunId, Optional<CleanupRunSummary>> result = new HashMap<>();
            for (CleanupRunPolicyKeyAndRunId id : ids) {
                Optional<CleanupRunSummary> summary = getRun(id);
                result.put(id, summary);
            }
            return result;
        } catch (StorageException e) {
            log.error("Failed to get policy runs", e);
            throw new StorageException(e);
        }
    }

    @Override
    public Optional<CleanupRunSummary> getRun(CleanupRunPolicyKeyAndRunId id) {
        try {
            return Optional.ofNullable(dao.get(id.policyKey(), id.policyRunId()));
        } catch (SQLException e) {
            log.error("Failed to get policy run with id: {}",id.policyRunId(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public Optional<RetentionRunSummary> getPolicyRun(CleanupRunPolicyKeyAndRunId cleanupRunPolicyKeyAndRunId) {
        try {
            return Optional.ofNullable(retentionRunSummaryDao.get(cleanupRunPolicyKeyAndRunId.policyKey(),
                    cleanupRunPolicyKeyAndRunId.policyRunId()));
        } catch (SQLException e) {
            log.error("Failed to get policy run with key {} and triggerId {}", cleanupRunPolicyKeyAndRunId.policyKey(),
                    cleanupRunPolicyKeyAndRunId.policyRunId(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public Set<CleanupPolicyRun> getPolicyRuns(CleanupPolicyRunRequest cleanupPolicyRunRequest) {
        try {
            if (cleanupPolicyRunRequest.from() == null) {
                return dao.getPolicyRuns(cleanupPolicyRunRequest.itemTypes(), cleanupPolicyRunRequest.policyKeyFilter(),
                        cleanupPolicyRunRequest.policyKeys(), cleanupPolicyRunRequest.page(),
                        cleanupPolicyRunRequest.pageSize(), cleanupPolicyRunRequest.sortBy(),
                        cleanupPolicyRunRequest.sortOrder(), cleanupPolicyRunRequest.excludeBlankExecutions(),
                        cleanupPolicyRunRequest.status());
            }
            return dao.getPolicyRuns(cleanupPolicyRunRequest.itemTypes(), cleanupPolicyRunRequest.from(),
                    cleanupPolicyRunRequest.to(), cleanupPolicyRunRequest.policyKeyFilter(),
                    cleanupPolicyRunRequest.policyKeys(), cleanupPolicyRunRequest.page(),
                    cleanupPolicyRunRequest.pageSize(), cleanupPolicyRunRequest.sortBy(),
                    cleanupPolicyRunRequest.sortOrder(), cleanupPolicyRunRequest.excludeBlankExecutions(),
                    cleanupPolicyRunRequest.status());
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException(getErrMsg(cleanupPolicyRunRequest.itemTypes(), cleanupPolicyRunRequest.to(),
                    cleanupPolicyRunRequest.from(), cleanupPolicyRunRequest.policyKeyFilter(),
                    cleanupPolicyRunRequest.page(), cleanupPolicyRunRequest.pageSize()), e);
        }
    }

    @Override
    public long getPolicyRunsCount(Set<String> itemTypes, Long from, Long to, String policyKeyFilter,
            Set<String> policyKeys, String excludeBlankExecutions, Set<String> status) {
        try {
            if(from == null) {
                return dao.getPolicyRunsCount(itemTypes, policyKeyFilter, policyKeys, excludeBlankExecutions,
                        status);
            }
            return dao.getPolicyRunsCount(itemTypes, from, to, policyKeyFilter, policyKeys, excludeBlankExecutions,
                    status);
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException("Failed to get policy runs count", e);
        }
    }

    @Override
    @Nonnull
    public Set<CleanupRunSummary> getFinishedRunSummaries(@NonNull Set<String> itemTypes, long startTime,
            long endTime) {
        try {
            return dao.getFinishedRunSummaries(itemTypes, startTime, endTime);
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException("Failed to get policy run summaries", e);
        }
    }

    @Override
    @Nonnull
    public Set<CleanupRunSummary> getRunningPolicies() {
        try {
            return dao.findByStatuses(
                    Set.of(CleanupRunSummary.Status.IN_PROGRESS.getName(),
                            CleanupRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException("Failed to get run summaries for running policies ", e);
        }
    }

    @Nonnull
    private static String getErrMsg(Set<String> itemTypes, Long to, Long from, String policyKey, Long page,
            Long pageSize) {
        String errMsg = "Failed to get cleanup policy runs by parameters. item types: %s, from: %d, to: %d";
        String pageMsg = ", page: %d, pageSize: %d";
        if (policyKey != null) {
            errMsg += ", policyKey: %s" + pageMsg;
            return String.format(errMsg, itemTypes, from, to, policyKey, page, pageSize);
        }
        errMsg += pageMsg;
        return String.format(errMsg, itemTypes, from, to, page, pageSize);
    }

    private RetentionRunSummary mapToRetentionRunSummary(CleanupRunSummary cleanupSummary) {

        RetentionRunSummary.Status status = RetentionRunSummary.Status.fromString(cleanupSummary.status().getName());

        long processedItemsCount = cleanupSummary.deletedPackagesCount();
        long failedItemsCount = cleanupSummary.failedPackagesCount();
        long totalItemsCount = processedItemsCount + failedItemsCount;
        long rejectedItemsCount = cleanupSummary.rejectedPackagesCount();
        long processedPhysicalBinarySize = cleanupSummary.estimatedDeletedBinarySize();
        long processedVirtualBinarySize = cleanupSummary.totalBinarySize();
        long totalBinarySize = cleanupSummary.originalBinarySize();
        String triggeredBy = cleanupSummary.triggeredBy();
        boolean tempNodesCleaned = cleanupSummary.tempNodesCleaned();

        return RetentionRunSummary.builder()
                .policyKey(cleanupSummary.policyKey())
                .runId(cleanupSummary.policyRunId())
                .dryRun(cleanupSummary.dryRun())
                .startedDate(cleanupSummary.startedDate())
                .finishedDate(cleanupSummary.finishedDate())
                .retentionType(CLEANUP)
                .status(status)
                .itemType(fromCleanupSummaryType(cleanupSummary.type()))
                .processedItemsCount(processedItemsCount)
                .failedItemsCount(failedItemsCount)
                .totalItemsCount(totalItemsCount)
                .rejectedItemsCount(rejectedItemsCount)
                .processedPhysicalBinarySize(processedPhysicalBinarySize)
                .processedVirtualBinarySize(processedVirtualBinarySize)
                .totalBinarySize(totalBinarySize)
                .triggeredBy(triggeredBy)
                .tempNodesCleaned(tempNodesCleaned)
                .runConfig(mapToRetentionRunSummaryConfig(cleanupSummary.runConfig()))
                .build();
    }

    private RunSummaryConfig mapToRetentionRunSummaryConfig(CleanupRunConfig cleanupRunConfig) {
        return RunSummaryConfig.builder()
                .created(cleanupRunConfig.created())
                .createdBy(cleanupRunConfig.createdBy())
                .modified(cleanupRunConfig.modified())
                .modifiedBy(cleanupRunConfig.modifiedBy())
                .cronExp(cleanupRunConfig.cronExp())
                .description(cleanupRunConfig.description())
                .skipTrashCan(cleanupRunConfig.skipTrashCan())
                .durationInMinutes(cleanupRunConfig.durationInMinutes())
                .packageTypes(cleanupRunConfig.packageTypes())
                .repos(cleanupRunConfig.repos())
                .excludedRepos(cleanupRunConfig.excludedRepos())
                .includedPackages(cleanupRunConfig.includedPackages())
                .excludedPackages(cleanupRunConfig.excludedPackages())
                .includeAllProjects(cleanupRunConfig.includeAllProjects())
                .includedProjects(cleanupRunConfig.includedProjects())
                .createdBeforeInMonths(cleanupRunConfig.createdBeforeInMonths())
                .lastDownloadedBeforeInMonths(cleanupRunConfig.lastDownloadedBeforeInMonths())
                .createdBeforeInDays(cleanupRunConfig.createdBeforeInDays())
                .lastDownloadedBeforeInDays(cleanupRunConfig.lastDownloadedBeforeInDays())
                .keepLastNVersions(cleanupRunConfig.keepLastNVersions())
                .includedProperties(cleanupRunConfig.includedProperties())
                .excludedProperties(cleanupRunConfig.excludedProperties())
                .releaseBundles(cleanupRunConfig.releaseBundles())
                .excludePromotedEnvironments(cleanupRunConfig.excludePromotedEnvironments())
                .buildPatterns(cleanupRunConfig.buildPatterns())
                .deleteBuildArtifacts(cleanupRunConfig.deleteBuildArtifacts())
                .build();
    }
}