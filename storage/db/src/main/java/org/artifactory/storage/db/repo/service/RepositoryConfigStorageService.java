package org.artifactory.storage.db.repo.service;

import lombok.NonNull;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Tamar Rosen
 */
public interface RepositoryConfigStorageService {
    /**
     * Find a stored repository configuration by its key
     *
     * @param repoKey the key of the repository
     * @return the repository config DB model
     */
    DbRepositoryConfig findRepositoryByKey(String repoKey);

    /**
     * Find all stored repositories
     *
     * @return List of repository config DB models
     */
    List<DbRepositoryConfig> getAllRepositories();

    /**
     * Find all stored repositories by their keys
     *
     * @return List of repository config DB models
     */
    Set<DbRepositoryConfig> findRepositoryConfigsByKeys(Set<String> repoKeys);

    /**
     * Insert a new repository configuration
     *
     * @param dbRepositoryConfig repository configuration to insert
     * @param createCacheRootFolder if false, creates main root folder with repo key (for local, virtual, federated,
     *                              release-bundle repos); if true, creates cache root folder with repo key + "-cache"
     *                              suffix (for remote repos with caching enabled)
     */
    @Transactional
    void insertRepositoryConfig(DbRepositoryConfig dbRepositoryConfig, boolean createCacheRootFolder);

    /**
     * Insert multiple repository configurations
     *
     * @param dbRepositoryConfigs a list of repository config DB models to insert
     * @param repoKeysToCreateRootFolders set of repo keys that need root folder nodes created
     *                                    (includes cache repo keys for remote repos with caching enabled)
     */
    @Transactional
    void bulkInsertRepositoryConfigs(Set<DbRepositoryConfig> dbRepositoryConfigs,
            Set<String> repoKeysToCreateRootFolders);

    /**
     * Delete multiple repository configurations
     *
     * @param repoKeys a list of repository keys to delete
     */
    @Transactional
    void bulkDeleteRepositoryConfigs(Set<String> repoKeys);

    /**
     * Update an existing repository configuration
     *
     * @param dbRepositoryConfig the updated repository configuration
     * @param createCacheRootFolder if true, creates cache root folder with repo key + "-cache" suffix
     *                              (used when caching is enabled on a remote repo); if false, no root folder is created
     */
    @Transactional
    void updateRepositoryConfig(DbRepositoryConfig dbRepositoryConfig, boolean createCacheRootFolder);

    /**
     * Update multiple repository configurations
     *
     * @param dbRepositoryConfigs a list of repository config DB models to update
     * @param repoKeysToCreateRootFolders set of repo keys that need root folder nodes created
     *                                    (e.g., cache repos for remote repos where caching was just enabled)
     */
    @Transactional
    void bulkUpdateRepositoryConfigs(Set<DbRepositoryConfig> dbRepositoryConfigs,
            Set<String> repoKeysToCreateRootFolders);

    void delete(String repoKey);

    boolean exists(String repoKey);

    int countAll();

    int deleteAll();

    Set<DbRepositoryConfig> getAllBasicRepoConfigs();

    Set<String> getAllRepoKeys();

    /**
     * Flush bulk changes to repository configurations
     *
     * @param repositoriesToCreate repositories to create
     * @param repositoriesToUpdate repositories to update
     * @param repositoriesToDelete repository keys to delete
     * @param rootFoldersToCreateForInsert repo keys that need root folders created (for new repos)
     * @param rootFoldersToCreateForUpdate repo keys that need root folders created (for updated repos)
     */
    void flushBulkChanges(Set<DbRepositoryConfig> repositoriesToCreate, Set<DbRepositoryConfig> repositoriesToUpdate,
            Set<String> repositoriesToDelete, Set<String> rootFoldersToCreateForInsert,
            Set<String> rootFoldersToCreateForUpdate);

    Collection<DbRepositoryConfig> findRepositoriesByDetailsType(String repoDetailsType);

    /**
     * Find all repository keys that do not have corresponding root folder nodes in the nodes table.
     * Used when atomic repo root creation is disabled to determine which repos need root folder creation
     * during initialization.
     *
     * @return set of repo keys that are missing root folder nodes
     */
    @NonNull
    Set<String> findAllMissingRepoRootPaths();

    /**
     * Creates root folder nodes for all repositories that are missing them.
     * This is a data consistency operation that ensures every repository has a corresponding root node.
     *
     * @return the number of root folder nodes created
     */
    int createMissingRepoRootFolders();

    Set<String> findKeysFromSet(Set<String> repoKeys);
}