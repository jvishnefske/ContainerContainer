package org.artifactory.policy.cleanup.bundle;

import lombok.Builder;
import org.artifactory.policy.ReleaseBundlePolicyConfig;

/**
 * @author Chaitanya Vootla
 */
@Builder(toBuilder = true)
public record BundleCleanupPolicyConfig(
        Integer durationInMinutes,
        BundleCleanupPolicySearchCriteria searchCriteria) implements ReleaseBundlePolicyConfig {
}