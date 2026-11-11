package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;

/**
 * @author vovam
 */
@Builder(toBuilder = true)
public record CleanupRunPolicyKeyAndRunId(
        String policyKey,
        int policyRunId
) {
}