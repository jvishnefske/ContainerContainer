package org.artifactory.storage.db.policy;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.policy.Policy;

/**
 * @author vovam
 */
@UtilityClass
public class PolicyValidator {

    public static void validateBeforeInsert(Policy<?> policy) {
        if (StringUtils.isBlank(policy.getKey())) {
            throw new IllegalArgumentException("Policy 'key' field can't be blank");
        }
        if (policy.getType() == null) {
            throw new IllegalArgumentException("Policy 'type' field can't be null");
        }
        if (policy.getItemType() == null) {
            throw new IllegalArgumentException("Policy 'item type' field can't be null");
        }
        if (StringUtils.isBlank(policy.getCreatedBy())) {
            throw new IllegalArgumentException("Policy 'created by' field can't be blank");
        }
        if (policy.getCreated() <= 0) {
            throw new IllegalArgumentException("Policy 'created' field can't be less or equal 0");
        }
        if (policy.getConfig() == null) {
            throw new IllegalArgumentException("Policy 'config' field can't be null");
        }
    }

    public static void validateBeforeUpdate(Policy<?> policy) {
        validateBeforeInsert(policy);

        if (StringUtils.isBlank(policy.getModifiedBy())) {
            throw new IllegalArgumentException("Policy 'modified by' field can't be blank");
        }
        if (policy.getModified() == null || policy.getModified() <= 0) {
            throw new IllegalArgumentException("Policy 'modified' field can't be null or less/equal 0");
        }
    }
}