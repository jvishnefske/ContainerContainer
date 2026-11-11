package org.artifactory.storage.db.policy.cleanup.service.packages;

import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;

import java.util.List;
import java.util.Optional;

public interface PackageCleanupPolicyStorageService {

    boolean insert(PackageCleanupPolicy policy);

    boolean update(PackageCleanupPolicy policy);

    void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig);

    Optional<PackageCleanupPolicy> getByKey(String policyKey);

    boolean deleteByKey(String policyKey);

    List<PackageCleanupPolicy> getAll();

    List<PackageCleanupPolicy> findByPolicyKeyFilter(String policyKeyFilter);

    boolean existsByKey(String key);

    long getTotalCount();
}
