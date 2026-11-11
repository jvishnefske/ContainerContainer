package org.artifactory.storage.db.policy.cleanup.service.build;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicyConfig;
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

import static org.artifactory.storage.db.policy.PolicyMapper.dbPolicyToPolicy;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuildCleanupPolicyStorageServiceImpl implements BuildCleanupPolicyStorageService {
    private final PolicyDao dao;

    @Override
    public boolean insert(BuildCleanupPolicy policy) {
        try {
            PolicyValidator.validateBeforeInsert(policy);
            CleanupPolicyStorageSharedUtils.assertBuildCleanupPolicy(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            CleanupPolicyStorageSharedUtils.assertBuildCleanupPolicy(dbPolicy);
            return dao.insert(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException(
                    "Unable to insert new build cleanup policy with key: " + policy.getKey(), e);
        }
    }

    @Override
    public boolean update(BuildCleanupPolicy policy) {
        return CleanupPolicyStorageSharedUtils.update(dao, policy);
    }

    @Override
    public void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig) {
        CleanupPolicyStorageSharedUtils.disableAll(dao, policyKeys, updateAllPoliciesConfig);
    }

    @Override
    public Optional<BuildCleanupPolicy> getByKey(String policyKey) {
        try {
            Optional<DbPolicy> dbPolicyOptional = dao.getByKey(policyKey);
            if (dbPolicyOptional.isEmpty()) {
                return Optional.empty();
            }
            DbPolicy dbPolicy = dbPolicyOptional.get();
            CleanupPolicyStorageSharedUtils.assertBuildCleanupPolicy(dbPolicy);
            return Optional.of(dbPolicyToPolicy(dbPolicy, new BuildCleanupPolicy(),
                    BuildCleanupPolicyConfig.class));
        } catch (SQLException e) {
            throw new StorageException("Unable to get build cleanup policy by key: " + policyKey, e);
        }
    }

    @Override
    public boolean deleteByKey(String policyKey) {
        try {
            return dao.deleteByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to delete build cleanup policy by key: " + policyKey, e);
        }
    }

    @Override
    public List<BuildCleanupPolicy> getAll() {
        try {
            return dao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(), PolicyItemType.BUILD.getCode())
                    .stream().map(dbPolicy -> dbPolicyToPolicy(dbPolicy, new BuildCleanupPolicy(),
                            BuildCleanupPolicyConfig.class)).toList();
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by type and item type";
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
            return dao.countByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(), PolicyItemType.BUILD.getCode());
        } catch (SQLException e) {
            throw new StorageException("Unable to count total amount of build cleanup policies", e);
        }
    }
}
