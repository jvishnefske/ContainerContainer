package org.artifactory.policy.restore.packages;

import lombok.Builder;

@Builder(toBuilder = true)
public record PackageRestoreColdResponse(int runSummaryId) {
}
