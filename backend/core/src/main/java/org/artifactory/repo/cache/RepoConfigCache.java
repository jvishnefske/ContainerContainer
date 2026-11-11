package org.artifactory.repo.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.UncheckedExecutionException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.ArtifactoryExecutor;
import org.artifactory.concurrent.ArtifactoryExecutorFactory;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.cache.GlobalCacheMetrics;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.RepoConfigCopyFactory;
import org.artifactory.model.RepoConfigPropagationModel;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.threadlocal.cache.SupplierWithError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.repoConfigCacheDbFallbackOnMissEnabled;
import static org.artifactory.common.ConstantValues.repoConfigCacheNegativeLookupMaxSize;
import static org.artifactory.common.ConstantValues.repoConfigCacheNegativeLookupTtlMillis;
import static org.artifactory.common.ConstantValues.repoConfigCacheRecordStatsEnabled;
import static org.artifactory.common.ConstantValues.virtualResolveConfigThreadLocalCacheEnabled;

/**
 * Repository config cache. Based on either Guava {@link com.google.common.cache.LoadingCache}
 * or Caffeine {@link LoadingCache} depending on the system property.
 * Caches all repo configs from the app start and provides a way to update the cache depending on repo
 * create/update/delete operations.
 *
 * @author vovam
 */
@Slf4j
public class RepoConfigCache {

    private static final int MAX_REPO_KEYS_PRINT_THRESHOLD = 20;

    private final Supplier<Map<String, BasicRepoConfig>> getAllBasicRepoConfigsFunction;
    private final com.google.common.cache.LoadingCache<String, RepoConfig> guavaCache;
    private final LoadingCache<String, RepoConfig> caffeineCache;
    private final boolean useCaffeine;
    private final ArtifactoryExecutor artifactoryExecutor;
    private final Supplier<Map<String, RepoConfig>> getAllRepoConfigsFunction;
    private final Function<String, RepoConfig> getRepoConfigByKeyFunction;
    private final Function<Set<String>, Map<String, RepoConfig>> getRepoConfigsByKeysFunction;
    private final Consumer<RepoConfigPropagationModel> callOnRepoConfigReloadAsync;
    private volatile ConcurrentHashMap<String, BasicRepoConfig> cachedBasicRepoConfigs;
    private final String identifier = RandomStringUtils.randomAlphabetic(4);
    private final Cache<String, Boolean> negativeLookupCache;
    private final ThreadLocal<VirtualResolveConfigCacheState> virtualResolveConfigCacheHolder = new ThreadLocal<>();

    public RepoConfigCache(long syncUpPeriodMillis, long repoConfigExpirationMillis, int batchOperationSize,
            double optimizationRatio, Supplier<Map<String, RepoConfig>> getAllRepoConfigsFunction,
            Function<String, RepoConfig> getRepoConfigByKeyFunction,
            Function<Set<String>, Map<String, RepoConfig>> getRepoConfigsByKeysFunction,
            Consumer<RepoConfigPropagationModel> callOnRepoConfigReloadAsync,
            Supplier<Map<String, BasicRepoConfig>> getAllBasicRepoConfigsFunction
    ) {
        if (syncUpPeriodMillis <= 0) {
            throw new IllegalArgumentException("Repo config cache: sync repo keys period can't be negative or zero");
        }
        this.useCaffeine = ConstantValues.enableCaffeineCache.getBoolean();
        this.getAllBasicRepoConfigsFunction = getAllBasicRepoConfigsFunction;
        this.getAllRepoConfigsFunction = getAllRepoConfigsFunction;
        this.getRepoConfigByKeyFunction = getRepoConfigByKeyFunction;
        this.getRepoConfigsByKeysFunction = getRepoConfigsByKeysFunction;
        this.callOnRepoConfigReloadAsync = callOnRepoConfigReloadAsync;

        log.debug("Initializing repo config cache instance {} using {}. Getting all repo configs from DB...",
                identifier, useCaffeine ? "Caffeine" : "Guava");
        Map<String, RepoConfig> allRepoConfigs = getAllRepoConfigsFunction.get();
        log.debug("Finishing getting all repo configs");

        this.cachedBasicRepoConfigs = new ConcurrentHashMap<>(allRepoConfigs.size());
        allRepoConfigs.values().forEach(r -> this.cachedBasicRepoConfigs.put(r.getKey(), new BasicRepoConfig(r)));

        if (useCaffeine) {
            log.info("Using Caffeine repo config cache");
            this.caffeineCache = buildCaffeineCache(allRepoConfigs, repoConfigExpirationMillis, batchOperationSize,
                    optimizationRatio);
            this.guavaCache = null;
        } else {
            log.info("Using Guava repo config cache");
            this.caffeineCache = null;
            this.guavaCache = buildGuavaCache(allRepoConfigs, repoConfigExpirationMillis, batchOperationSize,
                    optimizationRatio);
        }

        this.negativeLookupCache = Caffeine.newBuilder()
                .maximumSize(repoConfigCacheNegativeLookupMaxSize.getLong())
                .expireAfterWrite(repoConfigCacheNegativeLookupTtlMillis.getLong(),
                        TimeUnit.MILLISECONDS)
                .build();

        ScheduledExecutorService scheduledExecutorService =
                Executors.newSingleThreadScheduledExecutor(BasicThreadFactory.builder()
                        .namingPattern("repo-conf-cache-" + identifier)
                        .daemon(true)
                        .uncaughtExceptionHandler(
                                (t, e) -> log.error("Can't sync repo config keys while updating repo config cache", e))
                        .build());
        this.artifactoryExecutor =
                ArtifactoryExecutorFactory.newArtifactoryExecutor(scheduledExecutorService, ContextHelper.get(), false);

        long randomizedSyncUpPeriodMillis = ThreadLocalRandom.current().nextLong(0, syncUpPeriodMillis);

        ((ScheduledExecutorService) this.artifactoryExecutor.getExecutor()).scheduleAtFixedRate(
                this::syncRepoConfigKeys, randomizedSyncUpPeriodMillis, syncUpPeriodMillis,
                TimeUnit.MILLISECONDS);
        log.debug("Finishing repo config cache initialization");
    }

    /**
     * Check is repo config present by key.
     */
    public boolean existByKey(String repoKey) {
        log.trace("Checking does repo config by key: {} exist in repo cache...", repoKey);
        boolean exist = cachedBasicRepoConfigs.containsKey(repoKey);
        if (!exist && repoConfigCacheDbFallbackOnMissEnabled.getBoolean()) {
            exist = fallbackGetRepoConfigOnCacheMiss(repoKey) != null;
        }
        log.trace("Finishing checking repo config by key: {} in repo cache. Exists: {}",
                repoKey, exist);
        return exist;
    }

    /**
     * Return a cached repo config by key.
     * NOTE: If loading cache can't load a repo config by the given key because of not-synced state -
     * as a fallback action this repo config will be loaded from DB directly and will not be cached.
     *
     * @return a cached RepoConfig if present. Otherwise, <code>null</code>
     * @throws IllegalStateException - error occurs if loading cache can't load data because of an internal error
     */
    @Nullable
    public RepoConfig getByKey(@Nonnull String repoKey) {
        VirtualResolveConfigCacheState virtualResolveConfigCache = getActiveVirtualResolveConfigCache();
        if (virtualResolveConfigCache != null) {
            RepoConfig scopedConfig = virtualResolveConfigCache.get(repoKey);
            if (scopedConfig != null) {
                return scopedConfig;
            }
        }
        try {
            log.trace("Getting repo config by key: {} from repo cache...", repoKey);
            if (cachedBasicRepoConfigs.containsKey(repoKey)) {
                log.trace("Repo config with key: {} is present in cache", repoKey);
                RepoConfig config = getCacheValueCopyOrFallback(repoKey);
                rememberInVirtualResolveConfigCache(virtualResolveConfigCache, config);
                return config;
            }
            if (repoConfigCacheDbFallbackOnMissEnabled.getBoolean()) {
                RepoConfig config = fallbackGetRepoConfigOnCacheMiss(repoKey);
                rememberInVirtualResolveConfigCache(virtualResolveConfigCache, config);
                return config;
            }
            return null;
        } finally {
            log.trace("Finishing getting repo config by key: {} from repo cache", repoKey);
        }
    }

    /**
     * Retrieves the {@link RepoDetailsType} associated with the repo key.
     *
     * @param repoKey the key of the repository configuration to retrieve
     * @return the {@link RepoDetailsType} for the given {@code repoKey}, or <code>null</code> if repo config is not
     * cached or if specified repoKey is <code>null</code>
     */
    @Nullable
    public RepoDetailsType getRepoDetailsTypeByKey(String repoKey) {
        return getMappedBasicRepoConfigByKey(repoKey, BasicRepoConfig::getRepoType);
    }

    /**
     * Retrieves the {@link RepoType} associated with the repo key.
     *
     * @param repoKey the key of the repository configuration to retrieve
     * @return the {@link RepoType} for the given {@code repoKey}, or <code>null</code> if repo config is not
     * cached or if specified repoKey is <code>null</code>
     */
    @Nullable
    public RepoType getRepoPackageTypeByKey(String repoKey) {
        return getMappedBasicRepoConfigByKey(repoKey, BasicRepoConfig::getPackageType);
    }

    private <T> T getMappedBasicRepoConfigByKey(String repoKey, Function<BasicRepoConfig, T> mapper) {
        if (repoKey == null) {
            log.warn("Can not retrieve repo config, repo key is null");
            return null;
        }
        BasicRepoConfig basicRepoConfig = cachedBasicRepoConfigs.get(repoKey);
        if (basicRepoConfig != null) {
            log.trace("Repository config with key: {} found in cache", repoKey);
            return mapper.apply(basicRepoConfig);
        }
        if (repoConfigCacheDbFallbackOnMissEnabled.getBoolean()) {
            RepoConfig repoConfig = fallbackGetRepoConfigOnCacheMiss(repoKey);
            if (repoConfig != null) {
                return mapper.apply(new BasicRepoConfig(repoConfig));
            }
        }
        log.debug("Repository config with key: {} not found in cache", repoKey);
        return null;
    }

    /**
     * Return cached repo configs by set of keys.
     * NOTE: If loading cache can't load repo configs by given keys because of not-synced state -
     * as a fallback action these repo configs will be loaded from DB directly and will not be cached.
     *
     * @throws IllegalStateException - error occurs if cache loader can't load data because of an internal error
     */
    @Nonnull
    public ImmutableCollection<RepoConfig> getRepositoryConfigsByKeys(Set<String> repoKeys)
            throws IllegalStateException {
        try {
            if (log.isTraceEnabled()) {
                log.trace("Getting repo configs by keys: [{}] from repo cache...", String.join(",", repoKeys));
            }
            VirtualResolveConfigCacheState virtualResolveConfigCache = getActiveVirtualResolveConfigCache();
            if (virtualResolveConfigCache == null) {
                return getRepositoryConfigsByKeysFromSharedCache(repoKeys);
            }
            return getRepositoryConfigsByKeysWithVirtualResolveConfigCache(repoKeys, virtualResolveConfigCache);
        } finally {
            if (log.isTraceEnabled()) {
                log.trace("Finishing getting repo configs by keys: [{}] from repo cache",
                        Joiner.on(",").join(repoKeys));
            }
        }
    }

    public <T, Y extends Exception> T runWithVirtualResolveConfigCache(String virtualRepoKey,
            SupplierWithError<T, Y> supplier) throws Y {
        if (!virtualResolveConfigThreadLocalCacheEnabled.getBoolean()) {
            return supplier.get();
        }
        VirtualResolveConfigCacheState state = virtualResolveConfigCacheHolder.get();
        if (state == null) {
            state = new VirtualResolveConfigCacheState();
            virtualResolveConfigCacheHolder.set(state);
        }
        state.refCount++;
        log.debug("Opened virtual resolve config cache for {} (refCount={})", virtualRepoKey, state.refCount);
        try {
            return supplier.get();
        } finally {
            closeVirtualResolveConfigCache(state);
        }
    }

    private ImmutableCollection<RepoConfig> getRepositoryConfigsByKeysFromSharedCache(Set<String> repoKeys) {
        Collection<RepoConfig> repoConfigs = getCacheValues(repoKeys,
                () -> getRepoConfigsByKeysFunction.apply(repoKeys));
        return repoConfigs.stream().map(RepoConfigCopyFactory::copy)
                .collect(ImmutableList.toImmutableList());
    }

    private ImmutableCollection<RepoConfig> getRepositoryConfigsByKeysWithVirtualResolveConfigCache(
            Set<String> repoKeys, VirtualResolveConfigCacheState virtualResolveConfigCache) {
        ImmutableList.Builder<RepoConfig> configs = ImmutableList.builder();
        Set<String> missingKeys = new HashSet<>();
        for (String repoKey : repoKeys) {
            RepoConfig scopedConfig = virtualResolveConfigCache.get(repoKey);
            if (scopedConfig != null) {
                configs.add(scopedConfig);
            } else {
                missingKeys.add(repoKey);
            }
        }
        if (missingKeys.isEmpty()) {
            return configs.build();
        }
        getRepositoryConfigsByKeysFromSharedCache(missingKeys).forEach(config -> {
            RepoConfig cachedConfig = virtualResolveConfigCache.rememberIfAbsent(config);
            configs.add(cachedConfig);
        });
        return configs.build();
    }

    /**
     * Return all cached data
     * If loading cache can't load repo configs by keys that are present in cached keys set because of not-synced
     * state - as a fallback action all repo configs will be loaded from DB directly and will not be cached.
     *
     * @throws IllegalStateException - error occurs if loading cache can't load data because of an internal error
     */
    @Nonnull
    public Collection<RepoConfig> getAll() throws IllegalStateException {
        try {
            log.trace("Getting all repo config from repo cache...");
            Collection<RepoConfig> repoConfigs = getCacheValues(cachedBasicRepoConfigs.keySet(),
                    getAllRepoConfigsFunction);
            return repoConfigs.stream().map(RepoConfigCopyFactory::copy).toList();
        } finally {
            log.trace("Finishing getting all repo config from repo cache");
        }
    }

    /**
     * Returns cached repo config instances without copying. For internal use only (e.g. rebuild flow).
     * Callers must not mutate returned configs; copy before modifying.
     */
    @Nonnull
    public Collection<RepoConfig> getAllWithoutCopy() throws IllegalStateException {
        try {
            log.trace("Getting all repo config from repo cache (no copy)...");
            Collection<RepoConfig> repoConfigs = getCacheValues(cachedBasicRepoConfigs.keySet(),
                    getAllRepoConfigsFunction);
            return Collections.unmodifiableCollection(repoConfigs);
        } finally {
            log.trace("Finishing getting all repo config from repo cache (no copy)");
        }
    }

    /**
     * Return all cached basic repo config cached data
     */
    public Collection<BasicRepoConfig> getAllBasicConfigs() {
        try {
            log.trace("Getting all cached basic repo configs...");
            return new ArrayList<>(cachedBasicRepoConfigs.values());
        } finally {
            log.trace("Finishing getting all cached basic repo configs");
        }
    }

    /**
     * Returns a read-only point-in-time view of cached basic repo configs without creating a snapshot.
     * The view is backed by the current map instance and may become stale after a cache refresh.
     * Callers should not retain the result across calls.
     */
    @Nonnull
    public Collection<BasicRepoConfig> getAllBasicConfigsView() {
        try {
            log.trace("Getting all cached basic repo configs (view)...");
            return Collections.unmodifiableCollection(cachedBasicRepoConfigs.values());
        } finally {
            log.trace("Finishing getting all cached basic repo configs (view)");
        }
    }

    /**
     * Return count of all cached data
     */
    public int countAll() {
        log.trace("Getting all repo config count from repo cache...");
        int size = cachedBasicRepoConfigs.size();
        log.trace("Finishing getting all repo config count from repo cache. Count: {}", size);
        return size;
    }

    /**
     * Add only repo keys. if a new repo was added - a cache will load it lazily.
     * It decreases number of race conditions, however increases number of calls to DB.
     */
    public void addRepoConfigKeys(Map<String, BasicRepoConfig> data) {
        if (log.isTraceEnabled()) {
            log.trace("Adding repo keys: [{}] to cache...", String.join(",", data.keySet()));
        }
        if (repoConfigCacheDbFallbackOnMissEnabled.getBoolean()) {
            negativeLookupCache.invalidateAll(data.keySet());
        }
        cachedBasicRepoConfigs.putAll(data);
        log.trace("Finishing adding repo keys to cache");
    }

    public void updateRepoConfigKeys(Map<String, BasicRepoConfig> data) {
        if (log.isTraceEnabled()) {
            log.trace("Updating repo keys: [{}] in cache...", String.join(",", data.keySet()));
        }
        cachedBasicRepoConfigs.putAll(data);
        invalidateAll(data.keySet());
        log.trace("Finishing updating repo keys in cache");
    }

    /**
     * Delete repo configs from cache by keys
     */
    public void deleteRepoConfigsByKeys(Collection<String> keys) {
        if (log.isTraceEnabled()) {
            log.trace("Deleting repo config keys: [{}] from repo cache...", String.join(",", keys));
        }
        if (repoConfigCacheDbFallbackOnMissEnabled.getBoolean()) {
            keys.forEach(key -> negativeLookupCache.put(key, Boolean.TRUE));
        }
        keys.forEach(cachedBasicRepoConfigs::remove);
        invalidateAll(keys);
        if (log.isTraceEnabled()) {
            log.trace("Finishing repo config deletion by keys");
        }
    }

    public void addAndUpdateRepoConfigKeys(Map<String, BasicRepoConfig> keyToBasicRepoConfigMap) {
        if (log.isTraceEnabled()) {
            log.trace("Adding and updating repo keys: [{}] to cache...",
                    String.join(",", keyToBasicRepoConfigMap.keySet()));
        }
        if (repoConfigCacheDbFallbackOnMissEnabled.getBoolean()) {
            negativeLookupCache.invalidateAll(keyToBasicRepoConfigMap.keySet());
        }
        cachedBasicRepoConfigs.putAll(keyToBasicRepoConfigMap);
        invalidateAll(keyToBasicRepoConfigMap.keySet());
        log.trace("Finishing adding and updating repo keys to cache");
    }

    /**
     * Return set of all cached repo keys
     */
    public Set<String> getAllRepoKeys() {
        log.trace("Getting all repo config keys from repo cache...");
        Set<String> keys = Collections.unmodifiableSet(cachedBasicRepoConfigs.keySet());
        if (log.isTraceEnabled()) {
            log.trace("Finishing getting all repo config keys from repo cache. Keys: [{}]", String.join(",", keys));
        }
        return keys;
    }

    /**
     * Fill repo config keys set and remove cached repo configs if they were deleted.
     * DO NOT CALL THIS METHOD OUT OF THE SINGLE THREAD SCHEDULER because it is not synchronized.
     */
    @VisibleForTesting
    void syncRepoConfigKeys() {
        log.debug("Repo cache instance: {}. Sync repo config keys. Getting all repo config keys from db...",
                identifier);
        Map<String, BasicRepoConfig> dbBasicRepoConfigs = getAllBasicRepoConfigsFunction.get();
        if (log.isDebugEnabled()) {
            StringBuilder messageBuilder = new StringBuilder("Repo cache instance: ");
            messageBuilder.append(identifier);
            messageBuilder.append(". Finishing getting all repo config keys from db");
            if (log.isTraceEnabled()) {
                messageBuilder
                        .append(". Repo config keys: [")
                        .append(Joiner.on(",").join(dbBasicRepoConfigs.keySet()))
                        .append("]");
            }
            log.debug(messageBuilder.toString());
        }

        ConcurrentHashMap<String, BasicRepoConfig> dbRepoKeysConcurrentHashMap =
                new ConcurrentHashMap<>(dbBasicRepoConfigs.size());
        dbRepoKeysConcurrentHashMap.putAll(dbBasicRepoConfigs);

        ConcurrentHashMap<String, BasicRepoConfig> copyOfOldBasicRepoConfigs =
                new ConcurrentHashMap<>(cachedBasicRepoConfigs);

        // new basic repo configs are available here
        cachedBasicRepoConfigs = dbRepoKeysConcurrentHashMap;
        negativeLookupCache.invalidateAll();

        if (log.isDebugEnabled()) {
            StringBuilder messageBuilder = new StringBuilder("Repo cache instance: ")
                    .append(identifier)
                    .append(". Repo keys are updated");
            if (log.isTraceEnabled()) {
                messageBuilder.append(". Prev state: [")
                        .append(Joiner.on(", ").join(copyOfOldBasicRepoConfigs.keySet()))
                        .append("]. New state: [")
                        .append(Joiner.on(", ").join(dbRepoKeysConcurrentHashMap.keySet()))
                        .append("]");
            }
            log.debug(messageBuilder.toString());
        }

        copyOfOldBasicRepoConfigs.forEach((key, modification) -> {
            if (cachedBasicRepoConfigs.containsKey(key)) {
                if (cachedBasicRepoConfigs.get(key).getModification() != modification.getModification()) {
                    invalidate(key);
                    log.debug("Modification date for repo {} is changed. Repo config cache has been invalidated", key);
                }
            } else {
                invalidate(key);
                log.debug("Repo {} does not exist in DB. Repo config cache has been invalidated", key);
            }
        });

        RepoConfigPropagationModel propagationModel =
                RepoConfigCacheDiffCalculator.calculateCacheDiff(copyOfOldBasicRepoConfigs, dbBasicRepoConfigs);
        if (propagationModel != null) {
            callOnRepoConfigReloadAsync.accept(propagationModel);
        }
    }

    /**
     * Terminate a thread scheduler that syncs repo keys on a time basis.
     */
    public void destroy() {
        try {
            if (!artifactoryExecutor.isShutdown()) {
                log.trace("Destroying repo config cache...");
                this.artifactoryExecutor.shutdown();
                log.trace("Repo config cache has been successfully destroyed");
            }
        } catch (Throwable th) {
            log.warn("Issue occurs while destroying repo config cache. Reason: {}", th.getMessage());
        }
    }

    public Map<String, BasicRepoConfig> getCachedBasicRepoConfigs() {
        return Map.copyOf(cachedBasicRepoConfigs);
    }

    private LoadingCache<String, RepoConfig> buildCaffeineCache(
            Map<String, RepoConfig> allRepoConfigs, long repoConfigExpirationMillis, int batchOperationSize,
            double optimizationRatio) {
        com.github.benmanes.caffeine.cache.CacheLoader<String, RepoConfig> cacheLoader =
                new RepoConfigCaffeineCacheLoader(batchOperationSize, optimizationRatio, this::countAll,
                        getAllRepoConfigsFunction, getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        var cacheBuilder = Caffeine.newBuilder()
                .initialCapacity(allRepoConfigs.size())
                .expireAfterWrite(repoConfigExpirationMillis, TimeUnit.MILLISECONDS);
        if (repoConfigCacheRecordStatsEnabled.getBoolean()) {
            cacheBuilder.recordStats();
        }
        LoadingCache<String, RepoConfig> cache = cacheBuilder.build(cacheLoader);
        if (repoConfigCacheRecordStatsEnabled.getBoolean()) {
            GlobalCacheMetrics.monitorCaffeine(cache, "repo-config-cache", getClass());
        }
        cache.putAll(allRepoConfigs);
        return cache;
    }

    private com.google.common.cache.LoadingCache<String, RepoConfig> buildGuavaCache(
            Map<String, RepoConfig> allRepoConfigs, long repoConfigExpirationMillis, int batchOperationSize,
            double optimizationRatio) {
        CacheLoader<String, RepoConfig> cacheLoader =
                new RepoConfigGuavaCacheLoader(batchOperationSize, optimizationRatio, this::countAll,
                        getAllRepoConfigsFunction, getRepoConfigByKeyFunction, getRepoConfigsByKeysFunction);
        var cacheBuilder = CacheBuilder.newBuilder()
                .initialCapacity(allRepoConfigs.size())
                .expireAfterWrite(repoConfigExpirationMillis, TimeUnit.MILLISECONDS);
        if (repoConfigCacheRecordStatsEnabled.getBoolean()) {
            cacheBuilder.recordStats();
        }
        com.google.common.cache.LoadingCache<String, RepoConfig> cache = cacheBuilder.build(cacheLoader);
        if (repoConfigCacheRecordStatsEnabled.getBoolean()) {
            GlobalCacheMetrics.monitorGuava(cache, "repo-config-cache", getClass());
        }
        cache.putAll(allRepoConfigs);
        return cache;
    }

    @Nullable
    private RepoConfig getCacheValueCopyOrFallback(@Nonnull String repoKey) throws IllegalStateException {
        if (useCaffeine) {
            RepoConfig config;
            try {
                config = caffeineCache.get(repoKey);
            } catch (RuntimeException e) { // Thrown if CacheLoader fails
                String errorMessage = createCacheLoadErrorMessage(Set.of(repoKey));
                log.error(errorMessage, e);
                throw new IllegalStateException(errorMessage, e);
            }
            if (config == null) {
                return fallbackGetRepoConfigFromDb(repoKey);
            }
            return RepoConfigCopyFactory.copy(config);
        } else {
            try {
                return RepoConfigCopyFactory.copy(guavaCache.get(repoKey));
            } catch (ExecutionException | UncheckedExecutionException e) { // Thrown if CacheLoader fails
                String errorMessage = createCacheLoadErrorMessage(Set.of(repoKey));
                log.error(errorMessage, e);
                throw new IllegalStateException(errorMessage, e);
            } catch (CacheLoader.InvalidCacheLoadException e) {
                // This means that the CacheLoader returned null, and we need to fall back to a DB query
                return fallbackGetRepoConfigFromDb(repoKey);
            }
        }
    }

    private String createCacheLoadErrorMessage(Set<String> repoKeys) {
        String repoKeysString = repoKeys.stream()
                .limit(MAX_REPO_KEYS_PRINT_THRESHOLD).collect(Collectors.joining(", "));
        if (repoKeys.size() > MAX_REPO_KEYS_PRINT_THRESHOLD) {
            repoKeysString += ", ...";
        }
        return "Failed to load repo configs by repo keys: [%s]".formatted(repoKeysString);
    }

    private RepoConfig fallbackGetRepoConfigFromDb(String repoKey) {
        log.debug("Can't get repo by key from CacheLoader. Executing a fallback query to DB...");
        RepoConfig config = getRepoConfigByKeyFunction.apply(repoKey);
        if (config != null) { // this log was added temporarily to monitor if DB fallback is useful in real envs
            log.info("Fallback to DB successfully retrieved repo config from DB for repoKey: '{}'. " +
                     "Cache implementation used: '{}'", repoKey, useCaffeine ? "caffeine" : "guava");
        }
        return config;
    }

    /**
     * Fallback for cache miss on {@link #getByKey}: queries DB directly and backfills the cache
     * so that subsequent calls don't incur a DB hit. Guarded by the
     * {@code repo.config.cache.db.fallback.on.miss.enabled} feature flag.
     */
    @Nullable
    private RepoConfig fallbackGetRepoConfigOnCacheMiss(String repoKey) {
        if (negativeLookupCache.getIfPresent(repoKey) != null) {
            log.trace("Repo key '{}' found in negative lookup cache, "
                    + "skipping DB query", repoKey);
            return null;
        }
        try {
            log.debug("Repo config cache miss for key '{}'. "
                    + "Falling back to DB...", repoKey);
            RepoConfig config = getRepoConfigByKeyFunction.apply(repoKey);
            if (config != null) {
                log.info("Cache-miss DB fallback retrieved repo config "
                        + "for repoKey: '{}'", repoKey);
                backfillCacheFromDbResult(repoKey, config);
                return RepoConfigCopyFactory.copy(config);
            }
            negativeLookupCache.put(repoKey, Boolean.TRUE);
            return null;
        } catch (Exception e) {
            log.warn("DB fallback failed for repo key '{}': {}",
                    repoKey, e.getMessage());
            log.debug("DB fallback exception details", e);
            return null;
        }
    }

    private void backfillCacheFromDbResult(String repoKey, RepoConfig config) {
        negativeLookupCache.invalidate(repoKey);
        if (useCaffeine) {
            caffeineCache.put(repoKey, config);
        } else {
            guavaCache.put(repoKey, config);
        }
        cachedBasicRepoConfigs.put(repoKey, new BasicRepoConfig(config));
    }

    private Collection<RepoConfig> getCacheValues(Set<String> repoKeys,
            Supplier<Map<String, RepoConfig>> dbFallbackFunction)
            throws IllegalStateException {
        if (repoKeys == null || repoKeys.isEmpty()) {
            return Set.of();
        }
        if (useCaffeine) {
            try {
                return caffeineCache.getAll(repoKeys).values();
            } catch (RuntimeException e) {
                // No need for fallback to load from the DB, because the Caffeine CacheLoader ensures we can't get nulls
                // Thus, if we got here, there was an error when loading the cache
                String errorMessage = createCacheLoadErrorMessage(repoKeys);
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage, e);
            }
        } else {
            try {
                return guavaCache.getAll(repoKeys).values();
            } catch (ExecutionException | UncheckedExecutionException e) {
                // Means that there was an error thrown from the Guava CacheLoader
                String errorMessage = createCacheLoadErrorMessage(repoKeys);
                log.error(errorMessage);
                throw new IllegalStateException(errorMessage, e);
            } catch (CacheLoader.InvalidCacheLoadException e) {
                // Means that Guava CacheLoader returned a null mapping
                log.debug("Executing a fallback query to load repo configs from the DB");
                return dbFallbackFunction.get().values();
            }
        }
    }

    private void invalidateAll(Collection<String> keys) {
        if (useCaffeine) {
            caffeineCache.invalidateAll(keys);
        } else {
            guavaCache.invalidateAll(keys);
        }
        invalidateVirtualResolveConfigCache(keys);
    }

    private void invalidate(String key) {
        if (useCaffeine) {
            caffeineCache.invalidate(key);
        } else {
            guavaCache.invalidate(key);
        }
        invalidateVirtualResolveConfigCache(Set.of(key));
    }

    private VirtualResolveConfigCacheState getActiveVirtualResolveConfigCache() {
        if (!virtualResolveConfigThreadLocalCacheEnabled.getBoolean()) {
            return null;
        }
        VirtualResolveConfigCacheState state = virtualResolveConfigCacheHolder.get();
        if (state == null || state.refCount <= 0) {
            return null;
        }
        return state;
    }

    private void rememberInVirtualResolveConfigCache(VirtualResolveConfigCacheState state, RepoConfig config) {
        if (state != null && config != null) {
            state.remember(config);
        }
    }

    private void invalidateVirtualResolveConfigCache(Collection<String> keys) {
        VirtualResolveConfigCacheState state = getActiveVirtualResolveConfigCache();
        if (state != null) {
            keys.forEach(state::remove);
        }
    }

    private void closeVirtualResolveConfigCache(VirtualResolveConfigCacheState state) {
        state.refCount--;
        if (state.refCount <= 0) {
            virtualResolveConfigCacheHolder.remove();
        }
    }

    private static final class VirtualResolveConfigCacheState {
        private int refCount;
        private final Map<String, RepoConfig> overlay = new HashMap<>();

        private RepoConfig get(String repoKey) {
            return overlay.get(repoKey);
        }

        private void remember(RepoConfig config) {
            overlay.put(config.getKey(), config);
        }

        private RepoConfig rememberIfAbsent(RepoConfig config) {
            RepoConfig scopedConfig = overlay.putIfAbsent(config.getKey(), config);
            return scopedConfig == null ? config : scopedConfig;
        }

        private void remove(String repoKey) {
            overlay.remove(repoKey);
        }
    }
}
