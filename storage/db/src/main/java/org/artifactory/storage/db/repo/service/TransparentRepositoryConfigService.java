package org.artifactory.storage.db.repo.service;

import org.artifactory.model.RepoConfig;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for transparent repository configurations. Transparent repos reserve a repository key
 * without participating in active repository flows.
 */
public interface TransparentRepositoryConfigService {

    /**
     * Persists a new transparent repository configuration and marks the key as transparent in the local cache.
     * Also propagates the change to other HA nodes.
     */
    void insert(DbRepositoryConfig config);

    /**
     * Upserts the given typed {@link RepoConfig} into the transparent store.
     * Service-level symmetric API to {@code RepositoryConfigService.upsertRepoConfig(RepoConfig)}
     * for active configs: caller passes the model, the service handles the
     * conversion to {@link DbRepositoryConfig} internally.
     * <p>
     * Inserts a new transparent row when the key is absent; updates the
     * existing row otherwise.
     */
    void upsertRepoConfig(RepoConfig repositoryConfig);

    /**
     * Updates an existing transparent repository configuration in the DB.
     */
    void update(DbRepositoryConfig config);

    /**
     * Returns the raw DB config for the given repo key, or empty if not found in the transparent table.
     */
    Optional<DbRepositoryConfig> findByKey(String repoKey);

    /**
     * Returns all raw DB configs from the transparent table.
     */
    List<DbRepositoryConfig> getAll();

    /**
     * Returns true if the given repo key exists in the transparent table (exact, case-sensitive match).
     */
    boolean existsByKey(String repoKey);

    /**
     * Returns true if the given repo key exists in the transparent table, using a case-insensitive match.
     * Use for name-uniqueness checks during repo creation; not for request routing.
     */
    boolean existsByKeyIgnoreCase(String repoKey);

    /**
     * Deletes the transparent config for the given repo key, invalidates its local cache entry,
     * and propagates the invalidation to other HA nodes.
     *
     * @return number of rows deleted (0 if the key was not found)
     */
    int delete(String repoKey);

    /**
     * Returns true if the given repo key is in the transparent config table.
     * Cache-backed for hot-path lookups; the cache loader falls back to DB on miss.
     */
    boolean isRepoTransparent(String repoKey);

    /**
     * Returns the domain-level configs for all transparent repositories.
     */
    List<RepoConfig> getAllTransparentRepoConfigs();

    /**
     * Returns the domain-level config for the given transparent repository key, or empty if not found.
     */
    Optional<RepoConfig> getTransparentRepoConfigByKey(String repoKey);

    /**
     * Triggers a cache refresh for the given repo key so the local node reloads its value from DB.
     * Called by HA propagation when another node inserts or deletes a transparent config entry.
     */
    void invalidateCacheEntry(String repoKey);

    /**
     * Drops the entire local cache and re-populates it from DB. Called by HA propagation when another
     * node performs a bulk transparent change and wants peers to re-sync in a single round-trip rather
     * than receiving N per-key invalidations.
     */
    void reloadAllFromDb();

    /**
     * Bulk-inserts a batch of transparent repository configurations using a single JDBC batch.
     * Intended for grid-bootstrap scenarios where many keys land on the local node at once.
     */
    void bulkInsert(List<DbRepositoryConfig> configs);

    /**
     * Bulk-deletes the given repo keys from the transparent table in a single statement.
     * Invalidates the cache and propagates an invalidation to HA peers.
     */
    void bulkDelete(List<String> repoKeys);

    /**
     * Returns all repository keys from the transparent table, lower-cased, in a single query.
     * Use for bulk deduplication checks instead of N individual {@link #existsByKeyIgnoreCase} calls.
     */
    Set<String> findAllKeysLowerCase();

    /**
     * Bulk read: returns the DB rows for every key found in the transparent table.
     * Missing keys are simply absent from the result; the result size is bounded by the input size.
     */
    List<DbRepositoryConfig> findByKeys(List<String> repoKeys);

    /**
     * Atomically moves a batch of repositories from the active config table to the transparent config table
     * in a single DB transaction (delete-from-active + insert-into-transparent commit together or roll back together).
     * Keys not present in the active table are skipped; keys already transparent are also skipped.
     * After the transaction commits, a reload of the active config cache is triggered so listeners see the change.
     */
    void transitionReposToTransparent(List<String> repoKeys);

    /**
     * Atomically moves a batch of repositories from the transparent config table back to the active config table
     * in a single DB transaction. Keys not present in the transparent table are skipped.
     * After the transaction commits, a reload of the active config cache is triggered so listeners see the change.
     */
    void transitionReposToActive(List<String> repoKeys);
}
