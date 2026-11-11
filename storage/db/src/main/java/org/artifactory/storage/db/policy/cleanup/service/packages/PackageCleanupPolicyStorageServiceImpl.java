package org.artifactory.storage.db.policy.cleanup.service.packages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicy;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.PolicyMapper;
import org.artifactory.storage.db.policy.PolicyValidator;
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
public class PackageCleanupPolicyStorageServiceImpl implements PackageCleanupPolicyStorageService {
    private final PolicyDao dao;

    @Override
    public boolean insert(PackageCleanupPolicy policy) {
        try {
            PolicyValidator.validateBeforeInsert(policy);
            assertPolicyAndItemType(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            assertPolicyAndItemType(dbPolicy);
            return dao.insert(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException("Unable to insert new package cleanup policy with key: " + policy.getKey(), e);
        }
    }

    @Override
    public boolean update(PackageCleanupPolicy policy) {
        try {
            PolicyValidator.validateBeforeUpdate(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            assertPolicyAndItemType(dbPolicy);
            return dao.update(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException("Unable to update package cleanup policy by key: " + policy.getKey(), e);
        }
    }

    @Override
    public void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig) {
        try {
            dao.updateAll(policyKeys, updateAllPoliciesConfig);
        } catch (SQLException e) {
            throw new StorageException("Unable to update package cleanup policies: ", e);
        }
    }

    @Override
    public Optional<PackageCleanupPolicy> getByKey(String policyKey) {
        try {
            Optional<DbPolicy> dbPolicyOptional = dao.getByKey(policyKey);
            if (dbPolicyOptional.isEmpty()) {
                return Optional.empty();
            }
            DbPolicy dbPolicy = dbPolicyOptional.get();
            assertPolicyAndItemType(dbPolicy);
            return Optional.of(dbPolicyToPolicy(dbPolicy, new PackageCleanupPolicy(),
                    PackageCleanupPolicyConfig.class));
        } catch (SQLException e) {
            throw new StorageException("Unable to get package cleanup policy by key: " + policyKey, e);
        }
    }

    @Override
    public boolean deleteByKey(String policyKey) {
        try {
            return dao.deleteByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to delete package cleanup policy by key: " + policyKey, e);
        }
    }

    @Override
    public List<PackageCleanupPolicy> getAll() {
        try {
            return dao.findByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(), PolicyItemType.PACKAGE.getCode())
                    .stream().map(dbPolicy -> dbPolicyToPolicy(dbPolicy, new PackageCleanupPolicy(),
                            PackageCleanupPolicyConfig.class)).toList();
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by type and item type";
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public List<PackageCleanupPolicy> findByPolicyKeyFilter(String policyKeyFilter) {
        try {
            return dao.findByTypeAndItemTypesAndPolicyKeyFilter(PolicyType.CLEANUP.getCode(),
                            Set.of(PolicyItemType.PACKAGE.getCode()), policyKeyFilter).stream()
                    .map(dbPolicy -> dbPolicyToPolicy(dbPolicy, new PackageCleanupPolicy(),
                            PackageCleanupPolicyConfig.class)).toList();
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by policy key filter: " + policyKeyFilter;
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public boolean existsByKey(String policyKey) {
        try {
            return dao.existsByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to check if policy exists by key: " + policyKey, e);
        }
    }

    @Override
    public long getTotalCount() {
        try {
            return dao.countByPolicyTypeAndItemType(PolicyType.CLEANUP.getCode(), PolicyItemType.PACKAGE.getCode());
        } catch (SQLException e) {
            throw new StorageException("Unable to count total amount of package cleanup policies", e);
        }
    }

    private void assertPolicyAndItemType(PackageCleanupPolicy policy) {
        if (!PolicyType.CLEANUP.equals(policy.getType())) {
            throw new IllegalArgumentException(
                    "Can't perform policy storage operation for policy " + policy.getKey() +
                            " with non-cleanup type");
        }
        if (!PolicyItemType.PACKAGE.equals(policy.getItemType())) {
            throw new IllegalArgumentException(
                    "Can't perform policy storage operation for policy " + policy.getKey() + " with non-package type");
        }
    }

    private void assertPolicyAndItemType(DbPolicy dbPolicy) {
        if (!PolicyType.CLEANUP.getCode().equals(dbPolicy.type())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() + " can't be mapped since `type` is not `cleanup`");
        }
        if (!PolicyItemType.PACKAGE.getCode().equals(dbPolicy.itemType())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() + " can't be mapped since `item type` is not `package`");
        }
    }
}
