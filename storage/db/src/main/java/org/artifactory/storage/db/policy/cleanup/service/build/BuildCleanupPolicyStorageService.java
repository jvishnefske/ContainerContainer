package org.artifactory.storage.db.policy.cleanup.service.build;

import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;

import java.util.List;
import java.util.Optional;

/**
 * Storage service for build cleanup policies
 *
 * @author kunalm
 */
public interface BuildCleanupPolicyStorageService {

    /**
     * Inserts a new build cleanup policy
     *
     * @param policy the policy definition
     * @return true if the policy was inserted successfully
     */
    boolean insert(BuildCleanupPolicy policy);

    /**
     * Updates an existing build cleanup policy
     *
     * @param policy the policy definition
     * @return true if the policy was updated successfully
     */
    boolean update(BuildCleanupPolicy policy);

    /**
     * Disables all policies
     *
     * @param policyKeys              list of policy keys to disable
     * @param updateAllPoliciesConfig generic configuration model with enabled flag set to false
     */
    void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig);

    /**
     * Get a build cleanup policy by its key
     *
     * @param policyKey the policy key
     * @return the policy definition
     */
    Optional<BuildCleanupPolicy> getByKey(String policyKey);

    /**
     * Deletes a build cleanup policy by its key
     *
     * @param policyKey the policy key
     * @return true if the policy was deleted successfully
     */
    boolean deleteByKey(String policyKey);

    /**
     * Get all build cleanup policies
     *
     * @return a list of all policies
     */
    List<BuildCleanupPolicy> getAll();

    /**
     * Check if a build cleanup policy exists by its key
     *
     * @param key the policy key
     * @return true if the policy exists
     */
    boolean existsByKey(String key);

    /**
     * Get the total number of build cleanup policies
     *
     * @return the total number of policies
     */
    long getTotalCount();
}
