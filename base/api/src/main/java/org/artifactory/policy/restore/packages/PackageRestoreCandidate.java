package org.artifactory.policy.restore.packages;

import lombok.Builder;
import org.artifactory.policy.NodeMetadata;

import java.util.List;

@Builder(toBuilder = true)
public record PackageRestoreCandidate(
        String policyKey,
        int policyRunId,
        String packageName,
        String packageVersion,
        boolean lead,
        String repo,
        String nodePath,
        String nodeName,
        long binLength,
        String sha1,
        Integer status,
        String message,
        List<PackageRestoreCandidate> artifacts,
        NodeMetadata metadata
) {
}