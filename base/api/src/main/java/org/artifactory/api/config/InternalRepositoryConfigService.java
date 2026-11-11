package org.artifactory.api.config;

import org.artifactory.model.RepoConfig;
import org.artifactory.threadlocal.cache.SupplierWithError;

import java.util.Collection;
import java.util.Set;

/**
 * @author Mor Merhav
 */
public interface InternalRepositoryConfigService extends RepositoryConfigService {

    /**
     * Returns all repo configs from cache without deep copy. For internal use only (e.g. repository rebuild flow).
     * Callers must not mutate returned configs; copy before modifying (e.g. before setProxyRef).
     *
     * @return unmodifiable collection of cached config instances
     */
    Collection<RepoConfig> getAllRepoConfigsForRebuild();

    Set<RepoConfig> getAllRepoConfigsSkipCache();

    void updateRepoConfig(RepoConfig repositoryConfig, boolean skipRepoNameValidation);

    /**
     * Returns the repository configuration for the given repository key allowing to skip cache repo validation.
     * @param repoKey the key of the repository
     * @param skipCacheRepoValidation if true, the method will skip cache repo validation. Should be used only for
     *                                cases where a remote repo key might include "-cache" suffix, before it was
     *                                restricted
     * @return the repository configuration
     */
    RepoConfig getRepoConfigByKey(String repoKey, boolean skipCacheRepoValidation);

    RepoConfig getRepoConfigByKeyNoValidationChecks(String repoKey);

    <T, Y extends Exception> T runWithVirtualResolveConfigCache(String virtualRepoKey,
            SupplierWithError<T, Y> supplier) throws Y;

    int deleteAll(boolean reload, boolean propagateChanges);

    /**
     * Checks if the repository is managed. Unmanaged repositories are not managed by RepositoryConfigService.
     * Use {@link org.artifactory.api.repo.RepositoryService} instead
     * Examples for unmanaged repositories are: - cache, trash, support bundle, etc.
     *
     * @param repoKey The key of the repository to check
     * @return true if the repository is managed, false otherwise
     */
    boolean isManaged(String repoKey);

    /**
     * Find all repository keys that do not have corresponding root folder nodes in the nodes table.
     * Used when atomic repo root creation is disabled to determine which repos need root folder creation
     * during initialization.
     *
     * @return set of repo keys that are missing root folder nodes
     */
    Set<String> findAllMissingRepoRootPaths();

    /**
     * Creates root folder nodes for all repositories that are missing them.
     * This is a data consistency operation that ensures every repository has a corresponding root node.
     *
     * @return the number of root folder nodes created
     */
    int createMissingRepoRootFolders();

    /**
     * Rebuilds the in-memory repository config cache on the current node from the database, without firing reload
     * listeners or HA propagation. For internal use by maintenance/migration flows that persist config blobs directly
     * via the storage service and then need the cache to reflect those writes.
     */
    void rebuildRepoConfigCache();
}
