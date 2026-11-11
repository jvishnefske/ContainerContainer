package org.artifactory.repo;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Project / replication / environments / shared-with-projects fields surfaced on the repo CUD event {@code metadata}
 * payload. Resolved at the point where a {@link RepoConfigChange} is constructed (via the change factory) so the same
 * snapshot is reused by both the synchronous BEFORE worker handlers and the asynchronous AFTER event dispatcher,
 * including AFTER_REPO_DELETE where the repository has already been removed from descriptors / Access and a live
 * lookup would degrade to empty.
 *
 * <p>Fields default to a safe empty value when the underlying lookup fails or the repository is not yet present.
 */
public record RepoMetadata(
        @Nullable String project,
        boolean hasSharedProjects,
        boolean hasReplication,
        List<String> environments) {

    private static final RepoMetadata EMPTY = new RepoMetadata(null, false, false, List.of());

    public static RepoMetadata empty() {
        return EMPTY;
    }
}
