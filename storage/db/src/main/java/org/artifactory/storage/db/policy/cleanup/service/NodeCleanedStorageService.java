package org.artifactory.storage.db.policy.cleanup.service;

import org.artifactory.storage.db.policy.cleanup.CleanedNode;
import org.artifactory.storage.db.policy.cleanup.CleanedPackage;

import jakarta.annotation.Nonnull;
import org.artifactory.storage.db.policy.cleanup.DeleteArtifactStatus;

import java.util.List;
import java.util.Map;

/**
 * @author vovam
 */
public interface NodeCleanedStorageService {

    /**
     * Insert a record that node was cleaned by cleanup policy.
     */
    void insertCleanedNode(CleanedNode cleanedNode);

    /**
     * Update a record that according to sha list.
     */
    void updateCleanedNodeBySha(String policyKey, int policyRunId, int status, List<String> sha1List);

    /**
     * Estimate actual cleaned binaries size by a cleanup policy run.
     */
    long estimateCleanedBinLengthByPolicyKeyAndRunId(String policyKey, int policyRunId);

    /**
     * Estimate actual processed binaries size by a policy run.
     */
    long estimateProcessedBinLengthByPolicyKeyAndRunId(String policyKey, int policyRunId,
            DeleteArtifactStatus processedStatus);

    /**
     * Estimate actual processed binaries size by a policy run for restore run.
     */
    long estimateProcessedBinLengthByPolicyKeyAndRunIdByStatus(String policyKey, int policyRunId,
            DeleteArtifactStatus processedStatus);

    /**
     * Calculates the total size of all nodes processed by the specified cleanup policy run. This is NOT the actual
     * cleaned binaries size, because same binaries may be referenced by multiple nodes.
     *
     * @param policyKey   policy key
     * @param policyRunId id of the policy run
     * @return a sum of all binaries size (in bytes) that correspond to the nodes processed by the specified run
     */
    long calculateCleanupRunTotalBinSize(String policyKey, long policyRunId);

    /**
     * Calculates the virtual size of all nodes processed by the specified retention policy run. This is NOT the actual
     * cleaned binaries size, because same binaries may be referenced by multiple nodes.
     *
     * @param policyKey       policy key
     * @param policyRunId     id of the policy run
     * @param processedStatus status of the processed nodes
     * @return a sum of all binaries size (in bytes) that correspond to the nodes processed by the specified run
     */
    long calculateVirtualBinarySize(String policyKey, long policyRunId, DeleteArtifactStatus processedStatus);

    /**
     * Clean outdated records.
     */
    void cleanNodesCleanedRecordsByPolicyKeyAndRunId(String policyKey, int policyRunId);

    /**
     * Retrieves the cleaned records for a specific policy and run.
     *
     * @param policyKey   The key identifying the policy.
     * @param policyRunId The unique identifier for the policy run.
     * @return A list of CleanedPackage objects representing cleaned records.
     */
    @Nonnull
    List<CleanedPackage> getNodesCleanedRecordsByPolicyKeyAndRunId(@Nonnull String policyKey, int policyRunId);

    @Nonnull
    List<CleanedNode> getCleanedNodesByRunIdAndPolicyKey(@Nonnull String policyKey, int policyRunId);

    List<CleanedNode> getCleanedNodeByStatus(List<Integer> statuses, long limit, long offset);

    @Nonnull
    Map<String, CleanedNode> getLeadMetadataMap(@Nonnull String policyKey, int policyRunId);

    /**
     * Returns the error message from the first ERROR-status item recorded for the given policy run,
     * or {@code null} if no error items exist (e.g. records were already purged by the retention cleanup job).
     *
     * @param policyKey   the policy key
     * @param policyRunId the run id (from retention_run_summary.run_id)
     * @return error message string, or null
     */
    String getFirstErrorMessageForRun(@Nonnull String policyKey, int policyRunId);
}