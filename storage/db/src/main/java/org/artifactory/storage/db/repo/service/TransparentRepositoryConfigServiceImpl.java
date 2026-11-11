package org.artifactory.storage.db.repo.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.repo.dao.TransparentRepositoryConfigDao;
import org.artifactory.storage.db.repo.entity.DbRepositoryConfig;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigAttributesMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapper;
import org.artifactory.storage.db.repo.service.mapper.RepositoryConfigMapperImpl;
import org.artifactory.storage.db.util.BaseDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TransparentRepositoryConfigServiceImpl implements TransparentRepositoryConfigService {

    private final TransparentRepositoryConfigDao dao;
    private final RepositoryConfigMapper repositoryConfigMapper;
    private final LoadingCache<String, Boolean> transparentKeyCache;
    @Setter(onMethod_ = @Autowired)
    private RepositoryConfigStorageService repositoryConfigStorageService;

    @Autowired
    public TransparentRepositoryConfigServiceImpl(TransparentRepositoryConfigDao dao) {
        this.dao = dao;
        this.repositoryConfigMapper = new RepositoryConfigMapperImpl();
        this.transparentKeyCache = Caffeine.newBuilder()
                .expireAfterWrite(ConstantValues.transparentRepoCacheExpirationSeconds.getInt(), TimeUnit.SECONDS)
                .build(this::existsByKey);
    }

    @PostConstruct
    public void init() {
        // Startup must be resilient — if the initial load fails the cache stays empty and fills
        // on demand via the loader. reloadAllFromDb itself throws to surface failures to HA
        // callers, but here at startup we only log.
        try {
            reloadAllFromDb();
        } catch (StorageException e) {
            log.warn("Failed to pre-populate transparent repo cache on startup; cache will fill on demand", e);
        }
    }

    @Override
    public void upsertRepoConfig(RepoConfig repositoryConfig) {
        if (repositoryConfig == null) {
            log.warn("Cannot upsert transparent repository: repository config cannot be null");
            return;
        }
        stampCreationIfMissing(repositoryConfig);
        DbRepositoryConfig dbConfig = repositoryConfigMapper.repositoryConfigToDbRepositoryConfig(repositoryConfig);
        // Cache check avoids a redundant INSERT attempt on the common "already transparent" path.
        // On cache miss, try INSERT first and fallback to UPDATE if the row already exists
        // (another concurrent writer may have inserted since our cache check)
        if (isRepoTransparent(dbConfig.getKey())) {
            updateOrThrow(dbConfig);
            return;
        }
        try {
            dao.insert(dbConfig);
            markKeysTransparent(List.of(dbConfig.getKey()));
        } catch (SQLException e) {
            if (!BaseDao.isUniqueConstraintViolation(e)) {
                throw new StorageException(
                        "Failed to upsert transparent repository config for key: " + dbConfig.getKey(), e);
            }
            updateOrThrow(dbConfig);
        }
    }

    /**
     * Wire payload from federation mirror does not carry creation/modification; the active-table
     * insert validator requires non-zero creation. Stamp on the incoming config — mirrors what
     * {@code RepositoryConfigServiceImpl} does for active REPO_CREATE — so the blob carries it
     * too and a later {@link #transitionReposToActive} can bulk-insert without rejection.
     */
    static void stampCreationIfMissing(RepoConfig repositoryConfig) {
        if (repositoryConfig.getCreation() <= 0) {
            long now = System.currentTimeMillis();
            repositoryConfig.setCreation(now);
            if (repositoryConfig.getModification() <= 0) {
                repositoryConfig.setModification(now);
            }
        }
    }

    private void updateOrThrow(DbRepositoryConfig dbConfig) {
        try {
            dao.update(dbConfig);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to update transparent repository config for key: " + dbConfig.getKey(), e);
        }
    }

    @Override
    public void insert(DbRepositoryConfig config) {
        if (existsByKey(config.getKey())) {
            log.debug("Repository '{}' is already transparent, skipping insert", config.getKey());
            return;
        }
        try {
            dao.insert(config);
        } catch (SQLException e) {
            throw new StorageException("Failed to insert transparent repository config for key: " + config.getKey(), e);
        }
        markKeysTransparent(List.of(config.getKey()));
    }

    @Override
    public void update(DbRepositoryConfig config) {
        try {
            dao.update(config);
        } catch (SQLException e) {
            throw new StorageException("Failed to update transparent repository config for key: " + config.getKey(), e);
        }
    }

    @Override
    public Optional<DbRepositoryConfig> findByKey(String repoKey) {
        try {
            return dao.findByKey(repoKey);
        } catch (SQLException e) {
            throw new StorageException("Failed to find transparent repository config for key: " + repoKey, e);
        }
    }

    @Override
    public List<DbRepositoryConfig> getAll() {
        try {
            return dao.getAll();
        } catch (SQLException e) {
            throw new StorageException("Failed to retrieve all transparent repository configs", e);
        }
    }

    @Override
    public boolean existsByKey(String repoKey) {
        try {
            return dao.existsByKey(repoKey);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to check existence of transparent repository config for key: " + repoKey, e);
        }
    }

    @Override
    public boolean existsByKeyIgnoreCase(String repoKey) {
        try {
            return dao.existsByKeyIgnoreCase(repoKey);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to check case-insensitive existence of transparent repository config for key: " + repoKey,
                    e);
        }
    }

    @Override
    public int delete(String repoKey) {
        int deleted;
        try {
            deleted = dao.delete(repoKey);
        } catch (SQLException e) {
            throw new StorageException("Failed to delete transparent repository config for key: " + repoKey, e);
        }
        unmarkKeysTransparent(List.of(repoKey));
        return deleted;
    }

    @Override
    public Set<String> findAllKeysLowerCase() {
        try {
            return dao.findAllKeysLowerCase();
        } catch (SQLException e) {
            throw new StorageException("Failed to fetch all transparent repository keys", e);
        }
    }

    @Override
    public void bulkInsert(List<DbRepositoryConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        Set<String> existingLower = findAllKeysLowerCase();
        List<DbRepositoryConfig> toInsert = configs.stream()
                .filter(c -> !existingLower.contains(c.getKey().toLowerCase(Locale.ROOT)))
                .toList();
        if (toInsert.isEmpty()) {
            log.debug("All requested repos are already transparent; nothing to bulk-insert");
            return;
        }
        try {
            dao.bulkInsert(toInsert);
        } catch (SQLException e) {
            throw new StorageException("Failed to bulk-insert transparent repository configs", e);
        }
        markKeysTransparent(toInsert.stream().map(DbRepositoryConfig::getKey).toList());
    }

    @Override
    public void bulkDelete(List<String> repoKeys) {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return;
        }
        try {
            dao.bulkDelete(repoKeys);
        } catch (SQLException e) {
            throw new StorageException("Failed to bulk-delete transparent repository configs", e);
        }
        unmarkKeysTransparent(repoKeys);
    }

    @Override
    public List<DbRepositoryConfig> findByKeys(List<String> repoKeys) {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return List.of();
        }
        try {
            return dao.findByKeys(repoKeys);
        } catch (SQLException e) {
            throw new StorageException("Failed to bulk-find transparent repository configs", e);
        }
    }

    @Override
    public boolean isRepoTransparent(String repoKey) {
        if (StringUtils.isBlank(repoKey)) {
            return false;
        }
        return Boolean.TRUE.equals(transparentKeyCache.get(repoKey.toLowerCase()));
    }

    @Override
    public List<RepoConfig> getAllTransparentRepoConfigs() {
        return getAll().stream()
                .map(repositoryConfigMapper::dbInfoBlobToRepositoryConfig)
                // null = repo skipped by the unknown-package-type policy; drop it
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public Optional<RepoConfig> getTransparentRepoConfigByKey(String repoKey) {
        return findByKey(repoKey).map(repositoryConfigMapper::dbInfoBlobToRepositoryConfig);
    }

    @Override
    public void invalidateCacheEntry(String repoKey) {
        if (StringUtils.isNotBlank(repoKey)) {
            transparentKeyCache.refresh(repoKey.toLowerCase());
        }
    }

    @Override
    public void reloadAllFromDb() {
        try {
            List<DbRepositoryConfig> all = dao.getAll();
            transparentKeyCache.invalidateAll();
            all.forEach(config -> transparentKeyCache.put(config.getKey().toLowerCase(), true));
            log.debug("Reloaded {} transparent repo keys into cache", all.size());
        } catch (SQLException e) {
            // Surface the failure so the HA REST endpoint returns 500 and the sender's HTTP-level
            // retry can re-attempt. As a long-tail safety net the Caffeine cache will refresh on its
            // own when entries hit expireAfterWrite.
            throw new StorageException("Failed to reload transparent repo cache from DB", e);
        }
    }

    @Override
    @Transactional
    public void transitionReposToTransparent(List<String> repoKeys) {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return;
        }
        Set<String> candidateKeys = new HashSet<>(repoKeys);
        // Filter out keys already transparent
        candidateKeys.removeIf(this::existsByKey);
        if (candidateKeys.isEmpty()) {
            log.debug("All requested repos are already transparent; nothing to transition");
            return;
        }
        Set<DbRepositoryConfig> activeConfigs =
                repositoryConfigStorageService.findRepositoryConfigsByKeys(candidateKeys);
        if (activeConfigs.isEmpty()) {
            log.debug("None of the requested repos exist in active storage; nothing to transition");
            return;
        }
        Set<String> keysToMove = activeConfigs.stream()
                .map(DbRepositoryConfig::getKey)
                .collect(Collectors.toSet());
        try {
            dao.bulkInsert(List.copyOf(activeConfigs));
        } catch (SQLException e) {
            throw new StorageException("Failed to insert transparent rows during bulk transition", e);
        }
        repositoryConfigStorageService.bulkDeleteRepositoryConfigs(keysToMove);
        // Local cache updates immediately (a stale-cache transient on rollback is acceptable — the
        // cache loader falls back to DB on miss). HA propagation is deferred until afterCommit so
        // peers query a committed view; see propagateAfterCommitOrNow.
        markKeysTransparent(List.copyOf(keysToMove));
        log.info("Repositories {} transitioned to transparent", keysToMove);
    }

    @Override
    @Transactional
    public void transitionReposToActive(List<String> repoKeys) {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return;
        }
        List<DbRepositoryConfig> transparentConfigs = findByKeys(repoKeys);
        if (transparentConfigs.isEmpty()) {
            log.debug("None of the requested repos exist in transparent storage; nothing to transition");
            return;
        }
        // Symmetric with transitionReposToTransparent: filter out keys already in the active table
        // so a duplicate row doesn't fail the whole batch on the unique-key constraint.
        List<DbRepositoryConfig> configsToMove = transparentConfigs.stream()
                .filter(c -> !repositoryConfigStorageService.exists(c.getKey()))
                .toList();
        if (configsToMove.isEmpty()) {
            log.debug("All requested repos are already active; nothing to transition");
            return;
        }
        Set<String> keysToMove = configsToMove.stream()
                .map(DbRepositoryConfig::getKey)
                .collect(Collectors.toSet());
        // transparent_repo_config has no repo_type_v2 column, so the configs read back from it carry a null
        // shadow value. Reconstruct it from the preserved type before re-entering the active table; otherwise
        // the row would land with repo_type_v2 = NULL and the one-shot v284 backfill would never re-run.
        configsToMove.forEach(config ->
                config.setRepoTypeV2(RepositoryConfigAttributesMapper.repoTypeV2ForType(config.getType())));
        // No root-folder creation: these repos were previously active and their root nodes still exist.
        repositoryConfigStorageService.bulkInsertRepositoryConfigs(
                new HashSet<>(configsToMove), Set.of());
        try {
            dao.bulkDelete(List.copyOf(keysToMove));
        } catch (SQLException e) {
            throw new StorageException("Failed to delete transparent rows during bulk transition", e);
        }
        unmarkKeysTransparent(List.copyOf(keysToMove));
        log.info("Repositories {} transitioned to active", keysToMove);
    }

    /**
     * Marks the given keys as transparent in the local cache and notifies HA peers.
     * For single-key changes, sends a targeted per-key invalidation; for bulk changes, sends one
     * "reload" propagation so each peer does a single re-sync instead of N invalidations.
     * When called from inside an active transaction, HA propagation is deferred until after commit
     * so peers see the row state we just wrote (not an uncommitted view).
     */
    private void markKeysTransparent(List<String> repoKeys) {
        repoKeys.forEach(k -> transparentKeyCache.put(k.toLowerCase(), true));
        propagateAfterCommitOrNow(repoKeys);
    }

    /**
     * Invalidates the given keys in the local cache and notifies HA peers (see
     * {@link #markKeysTransparent} for the per-key vs bulk propagation rule and the after-commit deferral).
     */
    private void unmarkKeysTransparent(List<String> repoKeys) {
        List<String> repoKeysLowerCased = repoKeys.stream().map(String::toLowerCase).toList();
        transparentKeyCache.invalidateAll(repoKeysLowerCased);
        propagateAfterCommitOrNow(repoKeys);
    }

    /**
     * If a transaction is active, defer HA propagation until {@code afterCommit} so peers query a
     * committed view of the DB. Otherwise propagate immediately (e.g. singular non-transactional
     * {@link #insert} / {@link #delete} called outside a tx).
     */
    private void propagateAfterCommitOrNow(List<String> repoKeys) {
        if (repoKeys.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Take a defensive copy: the caller's collection may be a transient set built per-tx.
            List<String> snapshot = List.copyOf(repoKeys);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    propagateChangeToHaNodes(snapshot);
                }
            });
        } else {
            propagateChangeToHaNodes(repoKeys);
        }
    }

    /**
     * Best-effort propagation with bounded retry. Mirrors the try/catch/retry shape used by
     * {@code FederatedRepoServiceImpl#handleHaPropagation} for the RTFS-enable flow: we attempt the
     * propagation, and on failure we back off and try again. Unlike that flow we cannot revert the
     * underlying DB change (the transition transaction is already committed by the time we get here),
     * so after exhausting retries we just log — peers will pick up the change via their own cache
     * expiration window in the worst case.
     */
    private void propagateChangeToHaNodes(List<String> repoKeys) {
        if (repoKeys.isEmpty()) {
            return;
        }
        HaCommonAddon ha;
        try {
            ha = ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaCommonAddon.class);
        } catch (NullPointerException | IllegalStateException e) {
            // No HA context (e.g. test environment without a wired AddonsManager): nothing to propagate to.
            log.debug("HA addon unavailable; skipping transparent repo cache propagation for keys {}: {}",
                    repoKeys, e.getMessage());
            return;
        }
        int maxAttempts = Math.max(1, ConstantValues.transparentRepoPropagationMaxAttempts.getInt());
        long backoffMs = Math.max(0L, ConstantValues.transparentRepoPropagationBackoffMs.getLong());
        // Pass the single key for targeted invalidation; null signals "full reload" to the receiver.
        // See HaCommonAddon#propagateTransparentRepoCacheChange.
        String targetedKey = repoKeys.size() == 1 ? repoKeys.iterator().next() : null;
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ha.propagateTransparentRepoCacheChange(targetedKey);
                if (attempt > 1) {
                    log.info("Transparent repo cache propagation succeeded on attempt {}/{} for keys {}",
                            attempt, maxAttempts, repoKeys);
                }
                return;
            } catch (Exception e) {
                lastFailure = e;
                log.warn("Failed to propagate transparent repo cache change for keys {} (attempt {}/{}): {}",
                        repoKeys, attempt, maxAttempts, e.getMessage());
                if (attempt < maxAttempts && backoffMs > 0) {
                    sleepUninterruptibly(backoffMs * attempt);
                }
            }
        }
        log.error("Exhausted retries for transparent repo cache propagation; keys: {}", repoKeys, lastFailure);
    }

    private static void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
