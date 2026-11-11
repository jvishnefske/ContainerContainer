package org.artifactory.repo.metadata;

import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoMetadata;
import org.springframework.stereotype.Component;

/**
 * Single construction point for {@link RepoConfigChange} on the repo CUD path. The factory resolves the per-repo
 * {@link RepoMetadata} snapshot once, at construction time, and attaches it to the change so the same snapshot
 * travels through both the BEFORE and AFTER interceptor phases without being re-fetched.
 *
 * <p>Centralising construction here lets us add new ambient fields to {@link RepoConfigChange} (anything else that
 * needs to be resolved once and carried across phases) in a single place rather than at every call site.
 *
 * <p>The plain {@code RepoConfigChange.createOf/updateOf/deleteOf} static factories remain for tests and for
 * non-CUD callers (e.g. referenced-proxy / referenced-layout changes) that do not need metadata enrichment; the
 * downstream event consumers fall back to {@link RepoMetadata#empty()} when the field is {@code null}.
 */
@Component
public class RepoConfigChangeFactory {

    private final RepoMetadataResolver metadataResolver;

    public RepoConfigChangeFactory(RepoMetadataResolver metadataResolver) {
        this.metadataResolver = metadataResolver;
    }

    public RepoConfigChange create(RepoConfig newRepoConfig) {
        return enrich(RepoConfigChange.createOf(newRepoConfig), newRepoConfig);
    }

    public RepoConfigChange update(RepoConfig oldRepoConfig, RepoConfig newRepoConfig) {
        return enrich(RepoConfigChange.updateOf(oldRepoConfig, newRepoConfig), newRepoConfig);
    }

    public RepoConfigChange delete(RepoConfig oldRepoConfig) {
        return enrich(RepoConfigChange.deleteOf(oldRepoConfig), oldRepoConfig);
    }

    /**
     * Attaches a resolved metadata snapshot to a pre-built change. Use for code paths that already produce a
     * {@link RepoConfigChange} via the static factories or by manual construction and want to opt into metadata
     * enrichment without rewriting the construction call (e.g. the bulk insert/upsert paths that materialise many
     * changes inside a single stream).
     */
    public RepoConfigChange enrich(RepoConfigChange change) {
        if (change == null) {
            return null;
        }
        RepoConfig source = change.getNewRepoConfig() != null ? change.getNewRepoConfig() : change.getOldRepoConfig();
        return enrich(change, source);
    }

    private RepoConfigChange enrich(RepoConfigChange change, RepoConfig source) {
        if (isRepoCudEventsEnabled()) {
            change.setRepoMetadata(metadataResolver.resolve(source));
        }
        return change;
    }

    private static boolean isRepoCudEventsEnabled() {
        return ConstantValues.repoCudWorkerEventsEnabled.getBoolean()
                || ConstantValues.repoCudJfbusEventsEnabled.getBoolean();
    }
}
