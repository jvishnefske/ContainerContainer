package org.artifactory.storage.db.policy.cleanup.service.bundle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicy;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicyConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.PolicyMapper;
import org.artifactory.storage.db.policy.PolicyValidator;
import org.artifactory.storage.db.policy.cleanup.service.CleanupPolicyStorageSharedUtils;
import org.artifactory.storage.db.policy.dao.PolicyDao;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.artifactory.storage.db.policy.PolicyMapper.dbPolicyToPolicy;

@Slf4j
@Service
@RequiredArgsConstructor
public class BundleCleanupPolicyStorageServiceImpl implements BundleCleanupPolicyStorageService {
    private final PolicyDao dao;

    @Override
    public boolean insert(BundleCleanupPolicy policy) {
        try {
            PolicyValidator.validateBeforeInsert(policy);
            CleanupPolicyStorageSharedUtils.assertReleaseBundleCleanupPolicy(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            CleanupPolicyStorageSharedUtils.assertReleaseBundleCleanupPolicy(dbPolicy);
            return dao.insert(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException(
                    "Unable to insert new release bundle cleanup policy with key: " + policy.getKey(), e);
        }
    }

    @Override
    public boolean update(BundleCleanupPolicy policy) {
        return CleanupPolicyStorageSharedUtils.update(dao, policy);
    }

    @Override
    public void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig) {
        CleanupPolicyStorageSharedUtils.disableAll(dao, policyKeys, updateAllPoliciesConfig);
    }

    @Override
    public Optional<BundleCleanupPolicy> getByKey(String policyKey) {
        try {
            Optional<DbPolicy> dbPolicyOptional = dao.getByKey(policyKey);
            if (dbPolicyOptional.isEmpty()) {
                return Optional.empty();
            }
            DbPolicy dbPolicy = dbPolicyOptional.get();
            CleanupPolicyStorageSharedUtils.assertReleaseBundleCleanupPolicy(dbPolicy);
            return Optional.of(dbPolicyToPolicy(dbPolicy, new BundleCleanupPolicy(),
                    BundleCleanupPolicyConfig.class));
        } catch (SQLException e) {
            throw new StorageException("Unable to get release bundle cleanup policy by key: " + policyKey, e);
        }
    }

    @Override
    public boolean deleteByKey(String policyKey) {
        try {
            return dao.deleteByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to delete release bundle cleanup policy by key: " + policyKey, e);
        }
    }

    @Override
    public List<BundleCleanupPolicy> getAll() {
        try {
            return dao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(),
                            PolicyItemType.RELEASE_BUNDLE.getCode())
                    .stream().map(dbPolicy -> dbPolicyToPolicy(dbPolicy, new BundleCleanupPolicy(),
                            BundleCleanupPolicyConfig.class)).toList();
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by type and item type";
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public List<BundleCleanupPolicy> findByPolicyKeyFilter(String policyKeyFilter) {
        try {
            return dao.findByTypeAndItemTypesAndPolicyKeyFilter(PolicyType.CLEANUP.getCode(),
                            Set.of(PolicyItemType.RELEASE_BUNDLE.getCode()), policyKeyFilter).stream()
                    .map(dbPolicy -> dbPolicyToPolicy(dbPolicy, new BundleCleanupPolicy(),
                            BundleCleanupPolicyConfig.class)).toList();
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by policy key filter: " + policyKeyFilter;
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public boolean existsByKey(String policyKey) {
        return CleanupPolicyStorageSharedUtils.existsByKey(dao, policyKey);
    }

    @Override
    public long getTotalCount() {
        try {
            return dao.countByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(),
                    PolicyItemType.RELEASE_BUNDLE.getCode());
        } catch (SQLException e) {
            throw new StorageException("Unable to count total amount of release bundle cleanup policies", e);
        }
    }
}
