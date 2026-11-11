package org.artifactory.policy.cleanup.packages;

import lombok.Builder;
import org.artifactory.policy.PackagePolicyConfig;
import org.artifactory.policy.PackagePolicySearchCriteria;

/**
 * @author vovam
 */
@Builder(toBuilder = true)
public record PackageCleanupPolicyConfig(
        boolean skipTrashcan,
        Integer durationInMinutes,
        PackagePolicySearchCriteria searchCriteria) implements PackagePolicyConfig {
}