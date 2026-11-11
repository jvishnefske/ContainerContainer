package org.artifactory.storage.db.policy.service;

import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.policy.PolicyType;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.PolicyImpactRow;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRunRequest;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.dao.PolicyRunInput;
import org.artifactory.storage.db.policy.dao.RetentionRunSummaryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PolicyRunSummaryServiceImpl implements PolicyRunSummaryService {

    private final RetentionRunSummaryDao dao;

    @Autowired
    public PolicyRunSummaryServiceImpl(RetentionRunSummaryDao dao) {
        this.dao = dao;
    }

    @Override
    public Set<CleanupRunPolicyKeyAndRunId> getRunsForInternalCleanup(long timestamp, PolicyType policyType) {
        try {
            return dao.getRunsForInternalCleanup(timestamp, policyType);
        } catch (SQLException e) {
            log.error("Failed to get runs for internal cleanup by timestamp: {}", timestamp, e);
            throw new StorageException(e);
        }
    }

    @Override
    public void markAsCleanedInternally(String policyKey, int policyRunId,
            RetentionRunSummary.RetentionType retentionType) {
        try {
            dao.markAsCleanedInternally(policyKey, policyRunId, retentionType);
        } catch (SQLException e) {
            log.error("Failed to mark as cleaned internally. run summary key: {}, run id: {}", policyKey, policyRunId,
                    e);
            throw new StorageException(e);
        }
    }

    @Override
    public int insert(RetentionRunSummary summary) {
        try {
            return dao.insert(summary);
        } catch (SQLException e) {
            log.error("Failed to insert retention run summary record. Policy key: {}, policy run: {}, policy type: {}",
                    summary.policyKey(), summary.runId(), summary.retentionType().getName(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public int update(RetentionRunSummary summary) {
        try {
            return dao.update(summary);
        } catch (SQLException e) {
            log.error("Failed to update cleanup run summary record. Policy key: {}, policy run: {}",
                    summary.policyKey(), summary.runId(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public int updateById(RetentionRunSummary summary) {
        try {
            return dao.updateById(summary);
        } catch (SQLException e) {
            log.error("Failed to update run summary record. Policy key: {}, policy run: {}",
                    summary.policyKey(), summary.id(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public Set<RetentionRunSummary> getRuns(
            Set<CleanupRunPolicyKeyAndRunId> cleanupRunPolicyKeyAndRunIds) {
        try {
            Set<RetentionRunSummary> retentionRunSummaries = dao.findByStatuses(
                    PolicyType.ARCHIVE,
                    Set.of(RetentionRunSummary.Status.IN_PROGRESS.getName(),
                            RetentionRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName())
            );
            removeTerminatedRuns(cleanupRunPolicyKeyAndRunIds, retentionRunSummaries);
            return retentionRunSummaries;
        } catch (StorageException e) {
            log.error("Failed to get policy runs", e);
            throw new StorageException(e);
        } catch (SQLException e) {
            log.error("Failed to get policy runs with ids: {}", cleanupRunPolicyKeyAndRunIds, e);
            throw new StorageException(e);
        }
    }

    @Override
    public Optional<RetentionRunSummary> getRunById(int id) {
        try {
            return Optional.ofNullable(dao.get(id));
        } catch (SQLException e) {
            log.error("Failed to get policy run with id: {}", id, e);
            throw new StorageException(e);
        }
    }

    @Override
    public Optional<RetentionRunSummary> getRun(CleanupRunPolicyKeyAndRunId id) {
        try {
            return Optional.ofNullable(dao.get(id.policyKey(), id.policyRunId()));
        } catch (SQLException e) {
            log.error("Failed to get policy run with id: {}", id.policyRunId(), e);
            throw new StorageException(e);
        }
    }

    @Override
    public Set<CleanupPolicyRun> getPolicyRuns(CleanupPolicyRunRequest policyRunRequest) {
        boolean withDate = false;
        try {
            PolicyRunInput.PolicyRunInputBuilder builder = PolicyRunInput.builder()
                    .itemTypes(policyRunRequest.itemTypes())
                    .policyKey(policyRunRequest.policyKeyFilter())
                    .policyKeys(policyRunRequest.policyKeys())
                    .page(policyRunRequest.page())
                    .pageSize(policyRunRequest.pageSize())
                    .sortBy(policyRunRequest.sortBy())
                    .sortOrder(policyRunRequest.sortOrder())
                    .excludeBlankExecutions(policyRunRequest.excludeBlankExecutions())
                    .status(policyRunRequest.status())
                    .runIds(policyRunRequest.runIds())
                    .policyType(policyRunRequest.policyType());

            // Only add 'from' and 'to' if 'from' is not null
            if (policyRunRequest.from() != null) {
                withDate = true;
                builder.from(policyRunRequest.from());
                builder.to(policyRunRequest.to());
            }

            PolicyRunInput policyRunInput = builder.build();
            return dao.getPolicyRuns(policyRunInput, withDate);
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException(getErrMsg(
                    policyRunRequest.itemTypes(),
                    policyRunRequest.to(),
                    policyRunRequest.from(),
                    policyRunRequest.policyKeyFilter(),
                    policyRunRequest.page(),
                    policyRunRequest.pageSize()), e);
        }
    }


    @Override
    public long getPolicyRunsCount(Set<String> itemTypes, Long from, Long to, String policyKeyFilter,
            Set<String> policyKeys, String excludeBlankExecutions, Set<String> status, PolicyType policyType) {
        try {
            if (from == null) {
                return dao.getPolicyRunsCount(itemTypes, policyKeyFilter, policyKeys, excludeBlankExecutions, status,
                        policyType);
            }
            return dao.getPolicyRunsCount(itemTypes, from, to, policyKeyFilter, policyKeys, excludeBlankExecutions,
                    status, policyType);
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException("Failed to get policy runs count", e);
        }
    }

    @Override
    @Nonnull
    public Set<RetentionRunSummary> getFinishedRunSummaries(@NonNull Set<String> itemTypes, long startTime,
            long endTime, PolicyType policyType) {
        try {
            return dao.getFinishedRunSummaries(itemTypes, startTime, endTime, policyType);
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException("Failed to get policy run summaries", e);
        }
    }

    @Override
    @Nonnull
    public Set<RetentionRunSummary> getRunningPolicies(PolicyType policyType) {
        try {
            return dao.findByStatuses(
                    policyType,
                    Set.of(RetentionRunSummary.Status.IN_PROGRESS.getName(),
                            RetentionRunSummary.Status.IN_PROGRESS_WITH_ERRORS.getName()));
        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new StorageException("Failed to get run summaries for running policies ", e);
        }
    }

    @Override
    public List<RetentionRunSummary> getConsumptionRunsForDateRange(long fromMillis, long toMillis) {
        try {
            return dao.getConsumptionRunsForDateRange(fromMillis, toMillis);
        } catch (SQLException e) {
            log.error("Failed to get consumption runs for range {} - {}", fromMillis, toMillis, e);
            throw new StorageException("Failed to get consumption runs for date range", e);
        }
    }

    @Override
    public List<PolicyImpactRow> getStorageImpact(long fromMillis, @Nullable Set<String> retentionTypes,
            @Nullable Set<String> policyKeys, @Nullable Long minBytes, @Nullable Long maxBytes,
            boolean sortAsc, @Nullable Integer limit) {
        try {
            return dao.getStorageImpact(fromMillis, retentionTypes, policyKeys, minBytes, maxBytes, sortAsc, limit);
        } catch (SQLException e) {
            log.error("Failed to get retention storage impact. fromMillis={}, retentionTypes={}, "
                            + "policyKeys={}, minBytes={}, maxBytes={}, sortAsc={}, limit={}",
                    fromMillis, retentionTypes, policyKeys, minBytes, maxBytes, sortAsc, limit, e);
            throw new StorageException("Failed to get retention storage impact from " + fromMillis, e);
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

    private static void removeTerminatedRuns(@Nonnull Set<CleanupRunPolicyKeyAndRunId> cleanupRunPolicyKeyAndRunIds,
            @Nonnull Set<RetentionRunSummary> retentionRunSummaries) {
        Set<String> ids = cleanupRunPolicyKeyAndRunIds.stream()
                .map(runId -> runId.policyKey() + "-" + runId.policyRunId())
                .collect(Collectors.toSet());
        retentionRunSummaries.removeIf(
                retentionRunSummary ->
                        !ids.contains(retentionRunSummary.policyKey() + "-" + retentionRunSummary.runId()));
    }
}