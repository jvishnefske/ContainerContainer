package org.artifactory.policy.restore.packages;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record PackageRestoreWarmRequest(
        Boolean restoreToOriginal,
        String fallbackDestinationPath,
        String newDestinationPath,
        List<String> manifestPaths) {
}