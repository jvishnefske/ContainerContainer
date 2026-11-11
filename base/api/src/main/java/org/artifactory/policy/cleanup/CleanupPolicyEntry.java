package org.artifactory.policy.cleanup;

import lombok.Builder;

import java.util.List;

@Builder
public record CleanupPolicyEntry(
        String itemType,
        String policyKey,
        Long nextRunTime,
        Boolean enabled,
        String displayProject,
        CleanupPolicyLastRun lastRunData,
        List<String> includedProjects
) {
}

