package org.artifactory.policy.cleanup;

import java.util.List;

public record CleanupPolicyView(
        List<CleanupPolicyEntry> policies
) {
}
