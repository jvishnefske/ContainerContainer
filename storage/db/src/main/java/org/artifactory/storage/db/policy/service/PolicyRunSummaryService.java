package org.artifactory.storage.db.policy.service;

import org.artifactory.policy.PolicyType;
import org.artifactory.storage.db.policy.PolicyImpactRow;
import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRunRequest;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PolicyRunSummaryService {
    /**
     * Get completed non-dry runs for internal cleanup by timestamp
     *
     * @param timestamp  timestamp when task was completed
     * @param policyType policy type
     * @return - set of completed tasks
     */
    Set<CleanupRunPolicyKeyAndRunId> getRunsForInternalCleanup(long timestamp, PolicyType policyType);

    /**
     * Mark run as cleaned internally, means cleaned nodes records were deleted for it
     */
    void markAsCleanedInternally(String policyKey, int policyRunId, RetentionRunSummary.RetentionType retentionType);

    /**
     * Insert new retention run summary record into db
     *
     * @param summary - new summary
     */
    int insert(RetentionRunSummary summary);

    /**
     * Updates existing cleanup run summary record in db
     *
     * @param summary - updated summary
     * @return number of updated rows
     */
    int update(RetentionRunSummary summary);

    /**
     * Updates existing cleanup run summary record in db by Id
     *
     * @param summary - updated summary
     * @return number of updated rows
     */
    int updateById(RetentionRunSummary summary);

    /**
     * Get a map of policy key and run id and related sun summary
     *
     * @param ids - set of pairs of policy key and run id for search
     * @return map of the runs
     */
    Set<RetentionRunSummary> getRuns(Set<CleanupRunPolicyKeyAndRunId> ids);

    Optional<RetentionRunSummary> getRunById(int id);

    /**
     * Retrieves the cleanup run summary for the specified policy key and run ID.
     *
     * @param id A pair consisting of the policy key and run ID used for the search.
     * @return An Optional containing the cleanup run summary, if found.
     */
    Optional<RetentionRunSummary> getRun(CleanupRunPolicyKeyAndRunId id);

    /**
     * Get policy runs for defined page matching a defined time window, item types and partial match of policy key
     *
     * @param policyRunRequest request with search parameters
     * @return matching policy runs set
     */
    Set<CleanupPolicyRun> getPolicyRuns(CleanupPolicyRunRequest policyRunRequest);

    /**
     * Get full run summary records for completed policy runs by policy type and within the specified time window
     *
     * @param itemTypes policy types
     * @param startTime fetch runs finished after this timestamp
     * @param endTime   fetch runs finished before this timestamp
     * @return a set of run summaries. Never <code>null</code>
     */
    Set<RetentionRunSummary> getFinishedRunSummaries(Set<String> itemTypes, long startTime, long endTime,
            PolicyType policyType);

    /**
     * Get policy runs count matching a defined time window, item types and partial match of policy key
     *
     * @param itemTypes       the item types to get policy runs by
     * @param from            start of the time window
     * @param to              end of the time window
     * @param policyKeyFilter filter to get policy runs matching partially or exactly
     * @return matching policy runs count
     */
    long getPolicyRunsCount(Set<String> itemTypes, Long from, Long to, String policyKeyFilter, Set<String> policyKeys,
            String excludeBlankExecutions, Set<String> status, PolicyType policyType);

    /*
     * get full run summary records for all the running(inProgress) policies
     */
    Set<RetentionRunSummary> getRunningPolicies(PolicyType policyType);

    /**
     * Archive and restore runs in the date range for smart archiving consumption (started_date window).
     */
    List<RetentionRunSummary> getConsumptionRunsForDateRange(long fromMillis, long toMillis);

    /**
     * Returns per-policy aggregated storage impact for all non-dry completed/stopped runs
     * whose {@code started_date} falls on or after {@code fromMillis}.
     *
     * @param fromMillis     epoch-millis lookback boundary (inclusive)
     * @param retentionTypes optional set of retention type codes ("cleanup", "archive"); null/empty = all types
     * @param policyKeys     optional set of policy keys to restrict results; null/empty = all policies
     * @param minBytes       optional inclusive lower bound on per-policy bytes; null = no bound
     * @param maxBytes       optional inclusive upper bound on per-policy bytes; null = no bound;
     *                       pass {@code 0L} to surface zero-yield policies
     * @param sortAsc        {@code true} for ascending byte order, {@code false} for descending
     * @param limit          optional cap on number of returned entries; null = no limit
     * @return ordered list of {@link PolicyImpactRow}
     */
    List<PolicyImpactRow> getStorageImpact(long fromMillis, @Nullable Set<String> retentionTypes,
            @Nullable Set<String> policyKeys, @Nullable Long minBytes, @Nullable Long maxBytes,
            boolean sortAsc, @Nullable Integer limit);
}