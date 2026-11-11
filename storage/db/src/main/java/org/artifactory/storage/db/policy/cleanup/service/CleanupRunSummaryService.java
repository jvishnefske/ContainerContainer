package org.artifactory.storage.db.policy.cleanup.service;

import org.artifactory.storage.db.policy.RetentionRunSummary;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRun;
import org.artifactory.storage.db.policy.cleanup.CleanupPolicyRunRequest;
import org.artifactory.storage.db.policy.cleanup.CleanupRunPolicyKeyAndRunId;
import org.artifactory.storage.db.policy.cleanup.CleanupRunSummary;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author vovam
 */
public interface CleanupRunSummaryService {
    /**
     * Get completed non-dry runs for internal cleanup by timestamp
     *
     * @param timestamp timestamp when task was completed
     * @return - set of completed tasks
     */
    Set<CleanupRunPolicyKeyAndRunId> getRunsForInternalCleanup(long timestamp);

    /**
     * Mark run as cleaned internally, means cleaned nodes records were deleted for it
     */
    void markAsCleanedInternally(String policyKey, int policyRunId);

    /**
     * Insert new cleanup run summary record into db
     *
     * @param summary - new summary
     */
    void insert(CleanupRunSummary summary);

    /**
     * Updates existing cleanup run summary record in db
     *
     * @param summary - updated summary
     * @return number of updated rows
     */
    int update(CleanupRunSummary summary);

    /**
     * Get a map of policy key and run id and related sun summary
     *
     * @param ids - set of pairs of policy key and run id for search
     * @return map of the runs
     */
    Map<CleanupRunPolicyKeyAndRunId, Optional<CleanupRunSummary>> getRuns(Set<CleanupRunPolicyKeyAndRunId> ids);

    /**
     * Retrieves the cleanup run summary for the specified policy key and run ID.
     *
     * @param id A pair consisting of the policy key and run ID used for the search.
     * @return An Optional containing the cleanup run summary, if found.
     */
    Optional<CleanupRunSummary> getRun(CleanupRunPolicyKeyAndRunId id);

    /**
     * Get policy runs for defined page matching a defined time window, item types and partial match of policy key
     *
     * @param cleanupPolicyRunRequest request with search parameters
     * @return matching policy runs set
     */
    Set<CleanupPolicyRun> getPolicyRuns(CleanupPolicyRunRequest cleanupPolicyRunRequest);

    /**
     * Retrieves the retention run summary for the specified policy key and run ID.
     *
     * @param cleanupRunPolicyKeyAndRunId A pair consisting of the policy key and run ID used for the search.
     * @return An Optional containing the retention run summary, if found.
     */
    Optional<RetentionRunSummary> getPolicyRun(CleanupRunPolicyKeyAndRunId cleanupRunPolicyKeyAndRunId);

    /**
     * Get full run summary records for completed policy runs by policy type and within the specified time window
     *
     * @param itemTypes policy types
     * @param startTime fetch runs finished after this timestamp
     * @param endTime   fetch runs finished before this timestamp
     * @return a set of run summaries. Never <code>null</code>
     */
    Set<CleanupRunSummary> getFinishedRunSummaries(Set<String> itemTypes, long startTime, long endTime);

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
            String excludeBlankExecutions, Set<String> status);

    /*
     * get full run summary records for all the running(inProgress) policies
     */
    Set<CleanupRunSummary> getRunningPolicies();
}