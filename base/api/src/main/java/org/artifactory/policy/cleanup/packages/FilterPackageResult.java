package org.artifactory.policy.cleanup.packages;

import lombok.Builder;

@Builder(toBuilder = true)
public record FilterPackageResult(
        String path,
        Long propertyValue
) {
}
