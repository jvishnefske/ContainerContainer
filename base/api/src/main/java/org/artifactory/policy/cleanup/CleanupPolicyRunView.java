package org.artifactory.policy.cleanup;

import org.artifactory.api.rest.pagination.Pagination;

import java.util.Set;

public record CleanupPolicyRunView(Set<CleanupPolicyRunViewEntry> runViews,
                                   Pagination pagination) {
}