package org.artifactory.policy.restore.packages;

import lombok.Builder;

@Builder(toBuilder = true)
public record PackageRestoreWarmResponse(String responseMessage, int runSummaryId) {

}