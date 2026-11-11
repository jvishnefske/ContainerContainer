package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;

@Builder
public record CleanupPolicyViewRun(int runId,
                                   String policyKey,
                                   String type,
                                   long startTime,
                                   boolean dryRun,
                                   String status,
                                   long totalItems
) {
}
