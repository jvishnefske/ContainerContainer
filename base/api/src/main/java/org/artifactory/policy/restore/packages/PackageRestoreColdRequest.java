package org.artifactory.policy.restore.packages;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record PackageRestoreColdRequest(
        String policyKey,
        String namespace,
        List<String> manifestPaths) {
}
