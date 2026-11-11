package org.artifactory.storage.db.policy;

import org.artifactory.policy.Policy;
import org.artifactory.policy.PolicyItemType;
import org.artifactory.policy.PolicyType;
import org.artifactory.policy.cleanup.packages.PackageCleanupPolicyConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PolicyValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void validateNewPolicyInvalidKey(String key) {
        Policy<?> policy = new Policy<>();
        policy.setKey(key);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeInsert(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'key' field can't be blank");
    }

    @Test
    void validateNewPolicyInvalidPolicyType() {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeInsert(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'type' field can't be null");
    }

    @Test
    void validateNewPolicyInvalidPolicyItemType() {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeInsert(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'item type' field can't be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void validateNewPolicyInvalidPolicyCreatedBy(String createdBy) {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy(createdBy);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeInsert(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'created by' field can't be blank");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void validateNewPolicyInvalidPolicyCreatedTime(long created) {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy("user");
        policy.setCreated(created);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeInsert(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'created' field can't be less or equal 0");
    }

    @Test
    void validateNewPolicyInvalidPolicyEmptyConfig() {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy("user");
        policy.setCreated(1L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeInsert(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'config' field can't be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void validateExistingPolicyInvalidKey(String key) {
        Policy<?> policy = new Policy<>();
        policy.setKey(key);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'key' field can't be blank");
    }

    @Test
    void validateExistingPolicyInvalidPolicyType() {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'type' field can't be null");
    }

    @Test
    void validateExistingPolicyInvalidPolicyItemType() {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'item type' field can't be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void validateExistingPolicyInvalidPolicyCreatedBy(String createdBy) {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy(createdBy);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'created by' field can't be blank");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void validateExistingPolicyInvalidPolicyCreatedTime(long created) {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy("user");
        policy.setCreated(created);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'created' field can't be less or equal 0");
    }

    @Test
    void validateExistingPolicyInvalidPolicyEmptyConfig() {
        Policy<?> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy("user");
        policy.setCreated(1L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'config' field can't be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void validateExistingPolicyInvalidPolicyModifiedBy(String modifiedBy) {
        Policy<PackageCleanupPolicyConfig> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy("user");
        policy.setCreated(1L);
        policy.setConfig(new PackageCleanupPolicyConfig(true, 0, null));
        policy.setModifiedBy(modifiedBy);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'modified by' field can't be blank");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    void validateExistingPolicyInvalidPolicyCreatedTime(Long modified) {
        Policy<PackageCleanupPolicyConfig> policy = new Policy<>();
        policy.setKey("key");
        policy.setType(PolicyType.CLEANUP);
        policy.setItemType(PolicyItemType.PACKAGE);
        policy.setCreatedBy("user");
        policy.setCreated(1L);
        policy.setConfig(new PackageCleanupPolicyConfig(true, 0, null));
        policy.setModifiedBy("user");
        policy.setModified(modified);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PolicyValidator.validateBeforeUpdate(policy)
        );
        assertEquals(exception.getMessage(), "Policy 'modified' field can't be null or less/equal 0");
    }
}