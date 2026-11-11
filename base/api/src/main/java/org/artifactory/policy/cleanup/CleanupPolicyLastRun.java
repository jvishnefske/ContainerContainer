package org.artifactory.policy.cleanup;

import lombok.Builder;

@Builder
public record CleanupPolicyLastRun(
        String status,
        long lastRunTime,
        boolean dryRun,
        int runId,
        boolean reportDownloaded,
        long totalItems
) {
}
