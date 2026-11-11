package org.artifactory.policy.archive.packages;

import lombok.Builder;
import org.artifactory.policy.PackagePolicyConfig;
import org.artifactory.policy.PackagePolicySearchCriteria;

/**
 * @author Kunal Mazumdar
 */
@Builder(toBuilder = true)
public record PackageArchivePolicyConfig(
        boolean skipTrashcan,
        Integer durationInMinutes,
        Integer expirationTimeInMonths,
        PackagePolicySearchCriteria searchCriteria) implements PackagePolicyConfig {
}