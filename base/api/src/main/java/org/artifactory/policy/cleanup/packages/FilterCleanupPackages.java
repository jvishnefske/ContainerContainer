package org.artifactory.policy.cleanup.packages;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record FilterCleanupPackages(
        String property,
        Long propertyThreshold,
        List<FilterPackage> packages
) {
}
