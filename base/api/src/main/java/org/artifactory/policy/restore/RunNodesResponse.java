package org.artifactory.policy.restore;


import lombok.Builder;
import org.artifactory.policy.restore.packages.PackageRestoreCandidate;

import java.util.List;

@Builder(toBuilder = true)
public record RunNodesResponse(
        List<PackageRestoreCandidate> nodes) {
}