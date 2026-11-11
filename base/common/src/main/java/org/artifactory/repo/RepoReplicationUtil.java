package org.artifactory.repo;

import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.replication.ReplicationBaseDescriptor;
import org.artifactory.model.RepoConfig;

import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

/**
 * Shared helper for resolving whether a repository has at least one enabled replication. Local and Federated
 * repositories may have multiple outbound replications; Remote repositories may have a single inbound replication.
 * Used by both the REST UI repo-config response builder and the repo CUD event metadata resolver, so the same
 * "has-replication" semantics surface on every consumer.
 */
public final class RepoReplicationUtil {

    private RepoReplicationUtil() {
    }

    /**
     * Returns {@code true} if the repository identified by {@code repoConfig} has at least one enabled replication
     * configured in {@code descriptor}. Returns {@code false} on null inputs, on unsupported repo types, or when no
     * replication is configured.
     */
    public static boolean hasEnabledReplication(RepoConfig repoConfig, CentralConfigDescriptor descriptor) {
        if (repoConfig == null || descriptor == null) {
            return false;
        }
        RepoDetailsType repoType = repoConfig.getRepoType();
        if (repoType == LOCAL || repoType == FEDERATED) {
            return descriptor.getMultiLocalReplications(repoConfig.getKey()).stream()
                    .anyMatch(ReplicationBaseDescriptor::isEnabled);
        }
        if (repoType == REMOTE) {
            RemoteReplicationDescriptor replication = descriptor.getRemoteReplication(repoConfig.getKey());
            return replication != null && replication.isEnabled();
        }
        return false;
    }
}
