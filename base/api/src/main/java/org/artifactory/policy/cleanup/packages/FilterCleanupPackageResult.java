package org.artifactory.policy.cleanup.packages;

import lombok.Builder;
import java.util.List;

@Builder(toBuilder = true)
public record FilterCleanupPackageResult(
        List<FilterPackageResult> packages
) {
}
