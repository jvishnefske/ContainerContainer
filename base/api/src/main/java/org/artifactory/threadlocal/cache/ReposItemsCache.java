package org.artifactory.threadlocal.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.utils.PathUtils;

import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.fscStatsIntervalMillis;
import static org.artifactory.common.ConstantValues.reposItemsLimitReachedCacheExpMS;
import static org.artifactory.common.ConstantValues.reposItemsLimitReachedCacheSize;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.RepoPath.REMOTE_CACHE_SUFFIX;

/**
 * This class represents a cache object per thread. Used in {@link ReposItemsThreadLocalCacheService}.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
class ReposItemsCache<T extends RepositoryNameProvider, Y extends Exception> {
    public static final String THREAD_LOCAL_CACHE_FALLBACK = "thread.local.cache.fallback";
    public static final String REASON = "reason";
    public static final String THREAD_LOCAL_CACHE_EVENT = "thread.local.cache.event";
    public static final String RESULT = "result";
    private static final String LIMIT_REACHED = "LIMIT_REACHED";
    private static final String REPO_TYPE = "repoType";
    @VisibleForTesting
    protected Map<String, Map<String, List<T>>> cache = new HashMap<>();
    private final RepositoryService repositoryService;
    private final MeterRegistry meterRegistry;
    private final Set<String> invalidatedRepos;
    private final Set<RepoType> supportedRemotePackages;
    private final Set<RepoType> supportedLocalPackages;
    private final RepoType repoType;
    private static final long MAX_CACHE_SIZE = reposItemsLimitReachedCacheSize.getLong();
    private static final long CACHE_EXP_TIME = reposItemsLimitReachedCacheExpMS.getLong();
    private static long lastPrintTime = 0;
    static final Cache<String, String> itemsLimitReachedKeys = CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(CACHE_EXP_TIME, TimeUnit.MILLISECONDS)
            .build();

    private static final ReentrantLock LOCK = new ReentrantLock();

    public ReposItemsCache(Set<RepoType> supportedLocalPackages, Set<RepoType> supportedRemotePackages,
            MeterRegistry meterRegistry, RepoType repoType) {
        this.repoType = repoType;
        repositoryService = ContextHelper.get().beanForType(RepositoryService.class);
        this.meterRegistry = meterRegistry;
        invalidatedRepos = new HashSet<>();
        this.supportedRemotePackages = supportedRemotePackages;
        this.supportedLocalPackages = supportedLocalPackages;
    }

    void invalidate(String repoName) {
        invalidatedRepos.add(repoName);
    }

    @Nullable
    public List<T> get(String key, String repoName, SupplierWithLimit<T, Y> getItemsFromAllRepos, long limit,
            FallbackSupplier<T, Y> fallbackSupplier) throws Y {
        Map<String, List<T>> nodes = getDataOrNullIfFallbackRequired(key, repoName, getItemsFromAllRepos, limit);
        if (nodes == null) {
            return fallbackSupplier.get();
        }
        log.trace("Get entry from cache for key {} for the requested repo {}", key, repoName);
        return nodes.get(repoName);
    }

    public boolean exists(String key, String repoName, SupplierWithLimit<T, Y> supplier, long limit,
            NodeExistsSupplier<Y> fallbackSupplier) throws Y {
        Map<String, List<T>> nodes = getDataOrNullIfFallbackRequired(key, repoName, supplier, limit);
        if (nodes == null) {
            return fallbackSupplier.exists();
        }
        log.trace("Check if entry exists for key {} for the requested repo {}", key, repoName);
        return nodes.get(repoName) != null;
    }

    private FallbackType getFallbackType(String key, String repoName) {
        if (isItemsLimitReached(key)) {
            return FallbackType.LIMIT;
        } else if (invalidatedRepos.contains(repoName)) {
            return FallbackType.INVALIDATED;
        } else if (isVirtualRepo(repoName)) {
            return FallbackType.VIRTUAL;
        } else if (!(isSupportedRemoteCacheRepo(repoName) || isSupportedLocalCacheRepo(repoName))) {
            return FallbackType.NON_SUPPORTED;
        }
        return FallbackType.NONE;
    }

    @Nullable
    private Map<String, List<T>> getDataOrNullIfFallbackRequired(String key, String repoName,
            SupplierWithLimit<T, Y> supplier, long limit) throws Y {
        FallbackType fallback = getFallbackType(key, repoName);
        if (FallbackType.NONE.equals(fallback)) {
            Map<String, List<T>> nodes = getFromCacheOrFillCacheIfNeeded(key, supplier, limit);
            if (!isItemsLimitReached(key)) {
                return nodes;
            }
            fallback = FallbackType.LIMIT;
        }
        fallback.report(meterRegistry, repoType);
        return null;
    }

    @NonNull
    @VisibleForTesting
    Map<String, List<T>> getFromCacheOrFillCacheIfNeeded(@NonNull String key,
            SupplierWithLimit<T, Y> getSupplierWithLimit, long limit) throws Y {
        log.trace("Checking key {} in cache", key);
        Map<String, List<T>> reposToNodes = cache.get(key);
        if (reposToNodes != null) {
            reportCacheHit(meterRegistry, repoType);
            return reposToNodes;
        }
        reportCacheMiss(meterRegistry, repoType);
        log.debug("Cache for {} is empty, filling it", key);
        List<T> allNodes = getSupplierWithLimit.apply(limit);
        if (isLimitReached(allNodes, limit)) {
            log.debug("The number of nodes reached the {} limit for key {}, skipping cache", limit, key);
            itemsLimitReachedKeys.put(key, LIMIT_REACHED);
            return Collections.emptyMap();
        }
        reposToNodes = allNodes.stream().collect(Collectors.groupingBy(T::getRepo, Collectors.toList()));
        cache.put(key, reposToNodes);
        log.trace("nodes = {}", reposToNodes);
        return reposToNodes;
    }

    private boolean isSupportedRemoteCacheRepo(@NonNull String repoName) {
        if (!repoName.endsWith(REMOTE_CACHE_SUFFIX)) {
            return false;
        }
        String remoteRepoName = PathUtils.removeDashCache(repoName);
        if (!repositoryService.existsByRepoKey(remoteRepoName)) {
            return false;
        }
        log.trace("The {} repo is a remote repo, skipping cache", repoName);
        RemoteRepoConfig remoteRepoConfigByKey = repositoryService.getRemoteRepoConfigByKey(remoteRepoName);
        if (remoteRepoConfigByKey == null) {
            return false;
        }
        RepoType packageType = remoteRepoConfigByKey.getPackageType();
        return supportedRemotePackages.contains(packageType);
    }

    private boolean isSupportedLocalCacheRepo(@NonNull String repoName) {
        try {
            LocalConfig localRepoConfig = repositoryService.getLocalOrFederatedRepoConfigByKey(repoName);
            if (localRepoConfig == null) {
                return false;
            }
            if (localRepoConfig.getRepoType() == LOCAL || localRepoConfig.getRepoType() == FEDERATED) {
                return supportedLocalPackages.contains(localRepoConfig.getPackageType());
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    boolean isItemsLimitReached(String key) {
        return itemsLimitReachedKeys.getIfPresent(key) != null;
    }

    private boolean isVirtualRepo(String repoName) {
        try {
            return Optional.ofNullable(repositoryService.getRepoConfigByKey(repoName))
                    .map(repoConfig -> repoConfig.getRepoType() == VIRTUAL)
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isLimitReached(@NonNull List<T> nodes, long limit) {
        return nodes.size() >= limit;
    }

    private static void reportCacheHit(MeterRegistry meterRegistry, RepoType repoType) {
        Counter.builder(THREAD_LOCAL_CACHE_EVENT)
                .description("Thread Local Cache events")
                .tags(RESULT, "hit", REPO_TYPE, repoType.getType())
                .register(meterRegistry)
                .increment();
    }

    private static void reportCacheMiss(MeterRegistry meterRegistry, RepoType repoType) {
        Counter.builder(THREAD_LOCAL_CACHE_EVENT)
                .description("Thread Local Cache events")
                .tags(RESULT, "miss", REPO_TYPE, repoType.getType())
                .register(meterRegistry)
                .increment();
    }

    private static void reportNonAllowedVirtualRepo(MeterRegistry meterRegistry, RepoType repoType) {
        Counter.builder(THREAD_LOCAL_CACHE_FALLBACK)
                .description("Requested repository is virtual")
                .tags(REASON, "virtual", REPO_TYPE, repoType.getType())
                .register(meterRegistry)
                .increment();
    }

    private static void reportNotSupportedLocalOrRemote(MeterRegistry meterRegistry, RepoType repoType) {
        Counter.builder(THREAD_LOCAL_CACHE_FALLBACK)
                .description("The corresponding package type is not supported for the local / remote repo")
                .tags(REASON, "notSupportedLocalOrRemote", REPO_TYPE, repoType.getType())
                .register(meterRegistry)
                .increment();
    }

    private static void reportInvalidatedRepo(MeterRegistry meterRegistry, RepoType repoType) {
        Counter.builder(THREAD_LOCAL_CACHE_FALLBACK)
                .description("Repo was invalidated (updated during the request)")
                .tags(REASON, "invalidated", REPO_TYPE, repoType.getType())
                .register(meterRegistry)
                .increment();
    }

    private static void reportLimitReached(MeterRegistry meterRegistry, RepoType repoType) {
        Counter.builder(THREAD_LOCAL_CACHE_FALLBACK)
                .description("We reached the limit for number of items allowed to receive from DB")
                .tags(REASON, "limit", REPO_TYPE, repoType.getType())
                .register(meterRegistry)
                .increment();
    }

    public static void printIfNeeded(MeterRegistry meterRegistry,
            Set<RepoType> supportedLocalPackages, Set<RepoType> supportedRemotePackages) {
        boolean lockAcquired = LOCK.tryLock();
        if (!lockAcquired) {
            return;
        }
        try {
            long currentTimeMillis = System.currentTimeMillis();
            if (lastPrintTime + fscStatsIntervalMillis.getLong() > currentTimeMillis) {
                return;
            }
            Set<RepoType> supportedPackages = new HashSet<>(supportedLocalPackages);
            supportedPackages.addAll(supportedRemotePackages);
            for (RepoType repoType : supportedPackages) {
                double hit = getMetricCount(meterRegistry, THREAD_LOCAL_CACHE_EVENT, RESULT, "hit", repoType);
                double miss = getMetricCount(meterRegistry, THREAD_LOCAL_CACHE_EVENT, RESULT, "miss", repoType);

                double limit = getMetricCount(meterRegistry,
                        THREAD_LOCAL_CACHE_FALLBACK, REASON, "limit", repoType);
                double virtual = getMetricCount(meterRegistry,
                        THREAD_LOCAL_CACHE_FALLBACK, REASON, "virtual", repoType);
                double notSupportedLocalOrRemote = getMetricCount(meterRegistry,
                        THREAD_LOCAL_CACHE_FALLBACK, REASON, "notSupportedLocalOrRemote", repoType);
                double invalidated = getMetricCount(meterRegistry,
                        THREAD_LOCAL_CACHE_FALLBACK, REASON, "invalidated", repoType);

                if (hit + miss + limit + virtual + notSupportedLocalOrRemote + invalidated > 0) {
                    log.info("{RepoType: \"{}\", \"Hit\": {}, \"Miss\": {}, " + "\"Limit\": {}, \"Virtual\": {}, " +
                                    "\"NonSupportedLocalOrRemote\": {}, \"Invalidated\": {}}",
                            repoType.getType(), hit, miss, limit, virtual, notSupportedLocalOrRemote, invalidated);
                }
            }

            lastPrintTime = currentTimeMillis;
        } finally {
            LOCK.unlock();
        }
    }

    private static double getMetricCount(
            MeterRegistry meterRegistry, String metricName, String tag, String tagValue, RepoType repoType) {
        return meterRegistry.counter(metricName, tag, tagValue, REPO_TYPE, repoType.getType()).count();
    }

    enum FallbackType {
        NONE(null),
        LIMIT(ReposItemsCache::reportLimitReached),
        INVALIDATED(ReposItemsCache::reportInvalidatedRepo),
        VIRTUAL(ReposItemsCache::reportNonAllowedVirtualRepo),
        NON_SUPPORTED(ReposItemsCache::reportNotSupportedLocalOrRemote);

        private final BiConsumer<MeterRegistry, RepoType> reporter;

        FallbackType(BiConsumer<MeterRegistry, RepoType> reporter) {
            this.reporter = reporter;
        }

        public void report(MeterRegistry meterRegistry, RepoType repoType) {
            if (!NONE.equals(this)) {
                reporter.accept(meterRegistry, repoType);
            }
        }
    }
}
