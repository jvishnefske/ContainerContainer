package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;
import org.artifactory.storage.db.policy.PackageRunSummaryConfig;

@Builder
public record CleanupPolicyRun(int runId,
                               String taskType,
                               String policyKey,
                               long startTime,
                               long endTime,
                               long cleanedItems,
                               long totalItems,
                               long cleanedSize,
                               String status,
                               String triggeredBy,
                               boolean manual,
                               boolean dryRun,
                               int policyTaskId,
                               PackageRunSummaryConfig runSummaryConfig


) {
}