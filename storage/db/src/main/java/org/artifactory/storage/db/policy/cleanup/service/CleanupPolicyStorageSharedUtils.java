package org.artifactory.storage.db.policy.cleanup.service;

import lombok.experimental.UtilityClass;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.UpdateAllPoliciesConfig;
import org.artifactory.policy.cleanup.builds.BuildCleanupPolicy;
import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicy;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.PolicyMapper;
import org.artifactory.storage.db.policy.PolicyValidator;
import org.artifactory.storage.db.policy.dao.PolicyDao;
import org.artifactory.storage.db.policy.entity.DbPolicy;

import java.sql.SQLException;
import java.util.List;

@UtilityClass
public class CleanupPolicyStorageSharedUtils {
    public static boolean update(PolicyDao dao, BundleCleanupPolicy policy) {
        try {
            PolicyValidator.validateBeforeUpdate(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            assertReleaseBundleCleanupPolicy(dbPolicy);
            return dao.update(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException("Unable to update cleanup policy by key: " + policy.getKey(), e);
        }
    }

    public static boolean update(PolicyDao dao, BuildCleanupPolicy policy) {
        try {
            PolicyValidator.validateBeforeUpdate(policy);
            DbPolicy dbPolicy = PolicyMapper.policyToDbPolicy(policy);
            assertBuildCleanupPolicy(dbPolicy);
            return dao.update(dbPolicy);
        } catch (SQLException e) {
            throw new StorageException("Unable to update cleanup policy by key: " + policy.getKey(), e);
        }
    }

    public static void disableAll(PolicyDao dao, List<String> policyKeys,
            UpdateAllPoliciesConfig updateAllPoliciesConfig) {
        try {
            dao.updateAll(policyKeys, updateAllPoliciesConfig);
        } catch (SQLException e) {
            throw new StorageException("Unable to update release bundle cleanup policies: ", e);
        }
    }

    public static boolean existsByKey(PolicyDao dao, String policyKey) {
        try {
            return dao.existsByKey(policyKey);
        } catch (SQLException e) {
            throw new StorageException("Unable to check if release bundle cleanup policy exists by key: " +
                    policyKey, e);
        }
    }

    public static void assertReleaseBundleCleanupPolicy(BundleCleanupPolicy policy) {
        if (!PolicyType.CLEANUP.equals(policy.getType())) {
            throw new IllegalArgumentException(
                    "Can't perform policy storage operation for policy " + policy.getKey() +
                            " with non-cleanup type");
        }
        if (!PolicyItemType.RELEASE_BUNDLE.equals(policy.getItemType())) {
            throw new IllegalArgumentException(
                    "Can't perform policy storage operation for policy " + policy.getKey() +
                            " with non-release bundle type");
        }
    }

    public static void assertReleaseBundleCleanupPolicy(DbPolicy dbPolicy) {
        if (!PolicyType.CLEANUP.getCode().equals(dbPolicy.type())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() + " can't be mapped since `type` is not `cleanup`");
        }
        if (!PolicyItemType.RELEASE_BUNDLE.getCode().equals(dbPolicy.itemType())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() +
                            " can't be mapped since `item type` is not `release bundle`");
        }
    }

    public static void assertBuildCleanupPolicy(BuildCleanupPolicy policy) {
        if (!PolicyType.CLEANUP.equals(policy.getType())) {
            throw new IllegalArgumentException(
                    "Can't perform policy storage operation for policy " + policy.getKey() +
                            " with non-cleanup type");
        }
        if (!PolicyItemType.BUILD.equals(policy.getItemType())) {
            throw new IllegalArgumentException(
                    "Can't perform policy storage operation for policy " + policy.getKey() +
                            " with non-build type");
        }
    }

    public static void assertBuildCleanupPolicy(DbPolicy dbPolicy) {
        if (!PolicyType.CLEANUP.getCode().equals(dbPolicy.type())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() + " can't be mapped since `type` is not `cleanup`");
        }
        if (!PolicyItemType.BUILD.getCode().equals(dbPolicy.itemType())) {
            throw new IllegalArgumentException(
                    "DB Policy with key " + dbPolicy.key() +
                            " can't be mapped since `item type` is not `build`");
        }
    }
}
