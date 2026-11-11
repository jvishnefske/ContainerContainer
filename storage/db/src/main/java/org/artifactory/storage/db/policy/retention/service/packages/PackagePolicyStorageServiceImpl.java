package org.artifactory.storage.db.policy.retention.service.packages;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.PolicyMapper;
import org.artifactory.storage.db.policy.PolicyValidator;
import org.artifactory.storage.db.policy.dao.PolicyDao;
import org.artifactory.storage.db.policy.entity.DbPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class PackagePolicyStorageServiceImpl implements PackagePolicyStorageService {
    private final PolicyDao dao;

    @Autowired
    public PackagePolicyStorageServiceImpl(PolicyDao dao) {
        this.dao = dao;
    }

    @Override
    public List<DbPolicy> getAll(PolicyType policyType, PolicyItemType policyItemType) {
        try {
            return dao.findByPolicyTypeAndItemType(policyType.getCode(), policyItemType.getCode());
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by type and item type";
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public boolean insert(Policy<?> policy, PolicyType policyType, PolicyItemType policyItemType) {
        try {
            PolicyValidator.validateBeforeInsert(policy);
            assertPolicyAndItemType(policy, policyType, policyItemType);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            assertPolicyAndItemType(dbPolicy, policyType, policyItemType);
            return dao.insert(dbPolicy);
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by type and item type";
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public List<DbPolicy> findByPartialPolicyKey(String partialPolicyKey, PolicyType policyType,
            PolicyItemType policyItemType) {
        try {
            return dao.findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(),
                            Set.of(policyItemType.getCode()), partialPolicyKey);
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by policy key filter: " + partialPolicyKey;
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public List<DbPolicy> findByPartialPolicyKeyAndItemTypes(String partialPolicyKey, PolicyType policyType,
            Set<PolicyItemType> itemTypes) {
        try {
            return dao.findByTypeAndItemTypesAndPolicyKeyFilter(policyType.getCode(),
                    Set.of(itemTypes.stream().map(PolicyItemType::getCode).toArray(String[]::new)), partialPolicyKey);
        } catch (SQLException e) {
            String errMsg = "Unable to find policies by policy key filter: " + partialPolicyKey;
            log.error(errMsg, e);
            throw new StorageException(errMsg, e);
        }
    }

    @Override
    public Optional<DbPolicy> getByKey(String policyKey, PolicyType policyType, PolicyItemType policyItemType) {
        try {
            Optional<DbPolicy> dbPolicyOptional = dao.getByKey(policyKey);
            if (dbPolicyOptional.isEmpty()) {
                return Optional.empty();
            }
            DbPolicy dbPolicy = dbPolicyOptional.get();
            assertPolicyAndItemType(dbPolicy, policyType, policyItemType);
            return Optional.of(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException("Unable to get package policy by key: " + policyKey, e);
        }
    }

    @Override
    public boolean deleteByKey(String policyKey) {
        try {
            return dao.deleteByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to delete package policy by key: " + policyKey, e);
        }
    }

    @Override
    public boolean update(Policy<?> policy, PolicyType policyType, PolicyItemType policyItemType) {
        try {
            PolicyValidator.validateBeforeUpdate(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            assertPolicyAndItemType(dbPolicy, policyType, policyItemType);
            return dao.update(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException("Unable to update package policy by key: " + policy.getKey(), e);
        }
    }

    @Override
    public void disableAll(List<String> policyKeys, UpdateAllPoliciesConfig updateAllPoliciesConfig) {
        try {
            dao.updateAll(policyKeys, updateAllPoliciesConfig);
        } catch (SQLException e) {
            throw new StorageException("Unable to update package policies: ", e);
        }
    }

    @Override
    public boolean existsByKey(String policyKey) {
        try {
            return dao.existsByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to check if package policy exists by key: " + policyKey, e);
        }
    }

    @Override
    public long getTotalCount(PolicyType policyType, PolicyItemType policyItemType) {
        try {
            return dao.countByPolicyTypeAndItemType(policyType.getCode(), policyItemType.getCode());
        } catch (SQLException e) {
            throw new StorageException("Unable to count total amount of package policies", e);
        }
    }

    private void assertPolicyAndItemType(DbPolicy dbPolicy, PolicyType policyType, PolicyItemType policyItemType) {
        if (!policyType.getCode().equals(dbPolicy.type())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() + " can't be mapped since `type` is not " +
                            policyType.getCode());
        }
        if (!policyItemType.getCode().equals(dbPolicy.itemType())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() + " can't be mapped since `item type` is not " +
                            policyItemType.getCode());
        }
    }

    private void assertPolicyAndItemType(Policy<?> policy, PolicyType policyType, PolicyItemType policyItemType) {
        if (!policyType.getCode().equals(policy.getType().getCode())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + policy.getDescription() + " can't be mapped since `type` is not " +
                            policyType.getCode());
        }
        if (!policyItemType.getCode().equals(policy.getItemType().getCode())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + policy.getKey() + " can't be mapped since `item type` is not " +
                            policyItemType.getCode());
        }
    }
}