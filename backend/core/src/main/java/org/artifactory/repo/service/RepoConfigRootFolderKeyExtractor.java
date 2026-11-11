package org.artifactory.repo.service;

import lombok.experimental.UtilityClass;
import org.artifactory.model.LocalCacheRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChanges;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * Utility class for extracting repository keys that need root folder nodes created.
 * Centralizes the logic for determining which repositories require root folders
 * during insert and update operations.
 *
 * @author Mor Merhav
 */
@UtilityClass
public class RepoConfigRootFolderKeyExtractor {

    /**
     * Extracts root folder keys for a set of repo configs (used for initial bootstrap).
     * For non-remote repos: the repo key itself
     * For remote repos with caching enabled: the cache repo key
     *
     * @param repositoryConfigs the repository configurations
     * @return set of repo keys that need root folders created
     */
    public static Set<String> extractRootFolderKeys(Set<? extends RepoConfig> repositoryConfigs) {
        Set<String> rootFolderKeys = new HashSet<>();
        for (RepoConfig repoConfig : repositoryConfigs) {
            addRootFolderKey(repoConfig, rootFolderKeys);
        }
        return rootFolderKeys;
    }

    /**
     * Extracts the set of repo keys that need root folder nodes created for new repositories.
     * For non-remote repos: the repo key itself
     * For remote repos with caching enabled: the cache repo key
     *
     * @param changes the repo config changes
     * @return set of repo keys that need root folders created
     */
    public static Set<String> extractRootFolderKeysForInsert(RepoConfigChanges changes) {
        Set<String> rootFolderKeys = new HashSet<>();

        changes.filterBy(REPO_CREATE).stream()
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(Objects::nonNull)
                .forEach(repoConfig -> addRootFolderKey(repoConfig, rootFolderKeys));

        return rootFolderKeys;
    }

    /**
     * Extracts the set of repo keys that need root folder nodes created for updated repositories.
     * This handles cases where caching was just enabled on a remote repo.
     *
     * @param changes the repo config changes
     * @return set of repo keys that need root folders created
     */
    public static Set<String> extractRootFolderKeysForUpdate(RepoConfigChanges changes) {
        Set<String> rootFolderKeys = new HashSet<>();

        changes.filterBy(REPO_UPDATE).stream()
                .filter(change -> change.getNewRepoConfig() instanceof RemoteRepoConfig)
                .forEach(change -> {
                    RemoteRepoConfig newConfig = (RemoteRepoConfig) change.getNewRepoConfig();
                    RemoteRepoConfig oldConfig = (RemoteRepoConfig) change.getOldRepoConfig();

                    // If caching was just enabled (was false, now true), create the cache root folder
                    boolean cachingJustEnabled = newConfig.getRepoTypeConfig().isStoreArtifactsLocally() &&
                            (oldConfig == null || !oldConfig.getRepoTypeConfig().isStoreArtifactsLocally());

                    if (cachingJustEnabled) {
                        rootFolderKeys.add(newConfig.getKey() + LocalCacheRepoConfig.PATH_SUFFIX);
                    }
                });

        return rootFolderKeys;
    }

    /**
     * Adds the appropriate root folder key for a repo config.
     * For non-remote repos: adds the repo key itself
     * For remote repos with caching enabled: adds the cache repo key
     */
    private static void addRootFolderKey(RepoConfig repoConfig, Set<String> rootFolderKeys) {
        // Add the main repo key (for non-remote repos)
        if (!(repoConfig instanceof RemoteRepoConfig)) {
            rootFolderKeys.add(repoConfig.getKey());
        }
        // For remote repos, add cache repo key if caching is enabled
        if (repoConfig instanceof RemoteRepoConfig remoteConfig
                && remoteConfig.getRepoTypeConfig().isStoreArtifactsLocally()) {
            rootFolderKeys.add(repoConfig.getKey() + LocalCacheRepoConfig.PATH_SUFFIX);
        }
    }
}
