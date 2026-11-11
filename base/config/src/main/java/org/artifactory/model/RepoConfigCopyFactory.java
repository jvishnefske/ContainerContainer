package org.artifactory.model;

import lombok.experimental.UtilityClass;

import java.util.Objects;

/**
 * @author Dor Solomon
 */
@UtilityClass
public class RepoConfigCopyFactory {

    /**
     * Create a deep copy of repo config
     *
     * @param repoConfig the repo config to be copied
     * @return a copy of the repo config
     * @throws NullPointerException in case {@code repoConfig} is null
     */
    public <T extends RepoConfig> T copy(T repoConfig) {
        Objects.requireNonNull(repoConfig, "Repo config cannot be null");
        if (repoConfig instanceof ReleaseBundleRepoConfig releaseBundleRepoConfig) {
            return (T) ReleaseBundleRepoConfig.copy(releaseBundleRepoConfig);
        }
        if (repoConfig instanceof FederatedRepoConfig federatedRepoConfig) {
            return (T) FederatedRepoConfig.copy(federatedRepoConfig);
        }
        if (repoConfig instanceof LocalRepoConfig localRepoConfig) {
            return (T) LocalRepoConfig.copy(localRepoConfig);
        }
        if (repoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
            return (T) RemoteRepoConfig.copy(remoteRepoConfig);
        }
        if (repoConfig instanceof VirtualRepoConfig virtualRepoConfig) {
            return (T) VirtualRepoConfig.copy(virtualRepoConfig);
        }
        return null;
    }
}
