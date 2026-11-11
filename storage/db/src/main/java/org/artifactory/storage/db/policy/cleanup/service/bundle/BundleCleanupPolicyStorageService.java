package org.artifactory.storage.db.policy.cleanup.service.bundle;

import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicy;

import java.util.List;
import java.util.Optional;

public interface BundleCleanupPolicyStorageService {

    boolean insert(BundleCleanupPolicy policy);

    boolean update(BundleCleanupPolicy policy);

    void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig);

    Optional<BundleCleanupPolicy> getByKey(String policyKey);

    boolean deleteByKey(String policyKey);

    List<BundleCleanupPolicy> getAll();

    List<BundleCleanupPolicy> findByPolicyKeyFilter(String policyKeyFilter);

    boolean existsByKey(String key);

    long getTotalCount();
}
