package org.artifactory.storage.db.policy.retention.service.packages;

import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.storage.db.policy.entity.DbPolicy;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Chaitanya Vootla
 */
public interface PackagePolicyStorageService {
    List<DbPolicy> getAll(PolicyType policyType, PolicyItemType policyItemType);
    boolean insert(Policy<?> policy, PolicyType policyType, PolicyItemType policyItemType);
    List<DbPolicy> findByPartialPolicyKey(String partialPolicyKey, PolicyType policyType,
            PolicyItemType policyItemType);
    List<DbPolicy> findByPartialPolicyKeyAndItemTypes(String partialPolicyKey, PolicyType policyType,
            Set<PolicyItemType> itemTypes);
    Optional<DbPolicy> getByKey(String policyKey, PolicyType policyType, PolicyItemType policyItemType);
    boolean deleteByKey(String policyKey);
    boolean update(Policy<?> policy, PolicyType policyType, PolicyItemType policyItemType);
    void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig);
    boolean existsByKey(String key);
    long getTotalCount(PolicyType policyType, PolicyItemType policyItemType);
}