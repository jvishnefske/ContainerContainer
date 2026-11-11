package org.artifactory.threadlocal.cache;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.RepoConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.artifactory.common.ConstantValues.fscEnabled;
import static org.artifactory.common.ConstantValues.fscLocalReposMinimum;
import static org.artifactory.common.ConstantValues.fscRemoteReposMinimum;
import static org.artifactory.common.ConstantValues.fscSupportedPackages;
import static org.artifactory.common.ConstantValues.fscSupportedRemotePackages;
import static org.artifactory.common.ConstantValues.reposItemsLimitReachedCacheExpMS;
import static org.artifactory.common.ConstantValues.reposItemsLimitReachedCacheSize;
import static org.artifactory.model.LocalCacheRepoConfig.PATH_SUFFIX;

/**
 * A flow of a current thread caching mechanism.
 * This class hold a thread local instance of {@link ReposItemsCache}.
 * Instructions on how to build the key: The key should be some unique string to the calling method. If you want
 * Cacheable object can be any object that implements {@link RepositoryNameProvider}
 * several methods to use the same cache then you can use the same key.
 *
 * @author Andrey Tashlitsky
 */
@Slf4j
@UtilityClass
public class ReposItemsThreadLocalCacheService {
    private static final ThreadLocal<ReposItemsCache<RepositoryNameProvider, Exception>> cacheHolder =
            new ThreadLocal<>();
    private static final Set<RepoType> supportedLocalPackages = new HashSet<>();
    private static final Set<RepoType> supportedRemotePackages = new HashSet<>();
    private static final Object supportedPackagesLock = new Object();
    @Getter(lazy = true)
    private static final Cache<String, ReposCount> virtualRepoNumberOfLocalRepos =
            initVirtualRepoNumberOfLocalReposCache();

    @Getter(lazy = true)
    private static final MeterRegistry meterRegistry = ContextHelper.get().beanForType(MeterRegistry.class);

    public <T, Y extends Exception> T wrap(@NonNull String repoKey, @NonNull SupplierWithError<T, Y> supplier)
            throws Y {
        return wrap(repoKey, supplier, () -> true);
    }

    public <T, Y extends Exception> T wrap(@NonNull String repoKey, @NonNull SupplierWithError<T, Y> supplier,
            BooleanSupplier isFeatureEnabledInThisContext) throws Y {
        RepoType repoType = getRepoType(repoKey);
        if (!shouldUseThreadLocalCache(repoKey, repoType, isFeatureEnabledInThisContext)) {
            return supplier.get();
        }

        ReposItemsCache<RepositoryNameProvider, Exception> prevState = cacheHolder.get();
        try {
            log.debug("Wrapping with thread local cache");
            startUseCacheForSupplierFlow(repoType);
            return supplier.get();
        } finally {
            if (prevState == null) {
                endUseCacheForCurrentFlow();
            } else {
                cacheHolder.set(prevState);
            }
            ReposItemsCache.printIfNeeded(getMeterRegistry(), supportedLocalPackages, supportedRemotePackages);
        }
    }

    @Nullable
    private static RepoType getRepoType(@NotNull String repoKey) {
        RepoConfig repoConfig = ContextHelper.get()
                .beanForType(RepositoryService.class)
                .getRepoConfigByKey(repoKey.trim());
        return repoConfig == null ? null : repoConfig.getPackageType();
    }

    public void invalidateCache(String repoName) {
        log.debug("Clear thread local cache for repo: {}", repoName);
        ReposItemsCache<RepositoryNameProvider, Exception> cache = cacheHolder.get();
        if (cache != null) {
            cache.invalidate(repoName);
        }
    }

    public <T extends RepositoryNameProvider, Y extends Exception> T get(@NonNull String key, @NonNull String repoName,
            @NonNull SupplierWithLimit<T, Y> supplierWithLimit, long limit,
            @NonNull FallbackSupplier<T, Y> fallback)
            throws Y {
        return Optional.ofNullable(getAll(key, repoName, supplierWithLimit, limit, fallback))
                .flatMap(nodes -> nodes.stream().findFirst()).orElse(null);
    }

    @Nullable
    public <T extends RepositoryNameProvider, Y extends Exception> List<T> getAll(@NonNull String key,
            @NonNull String repoName, @NonNull SupplierWithLimit<T, Y> supplierWithLimit, long limit,
            @NonNull FallbackSupplier<T, Y> fallback)
            throws Y {
        ReposItemsCache<T, Y> localExecutionCache = (ReposItemsCache<T, Y>) cacheHolder.get();
        if (localExecutionCache == null) {
            log.trace("GettAll: No thread local cache, using fallback");
            return fallback.get();
        }
        log.trace("GetAll: Found thread local cache");
        return localExecutionCache.get(key, repoName, supplierWithLimit, limit, fallback);
    }

    public <T extends RepositoryNameProvider, Y extends Exception> boolean exists(@NonNull String key,
            @NonNull String repoName, @NonNull SupplierWithLimit<T, Y> function, long limit,
            @NonNull NodeExistsSupplier<Y> fallback)
            throws Y {
        ReposItemsCache<T, Y> localExecutionCache = (ReposItemsCache<T, Y>) cacheHolder.get();
        if (localExecutionCache == null) {
            log.trace("Exists: No thread local cache, using fallback");
            return fallback.exists();
        }
        log.trace("Exists: Found thread local cache");
        return localExecutionCache.exists(key, repoName, function, limit, fallback);
    }

    @VisibleForTesting
    void startUseCacheForSupplierFlow(RepoType repoType) {
        cacheHolder.set(new ReposItemsCache<>(supportedLocalPackages, supportedRemotePackages, getMeterRegistry(),
                repoType));
    }

    @VisibleForTesting
    void endUseCacheForCurrentFlow() {
        cacheHolder.remove();
    }

    @VisibleForTesting
    boolean shouldUseThreadLocalCache(String repoKey, RepoType repoType,
            BooleanSupplier isFeatureEnabledInThisContext) {
        if (repoType == null) {
            log.debug("Could not determine package type for repo {}, not using thread local cache", repoKey);
            return false;
        }
        if (!fscEnabled.getBoolean() ||
                !isPackageTypeSupported(repoType) ||
                !isFeatureEnabledInThisContext.getAsBoolean()) {
            log.debug("Either Thread local cache is disabled or package type of repo " + repoKey +
                    " is not supported for usage of Thread Local Cache");
            return false;
        }
        return isNumberOfLocalOrRemoteReposReached(repoKey);
    }

    @VisibleForTesting
    boolean isPackageTypeSupported(RepoType packageType) {
        loadSupportedTypes(supportedLocalPackages, fscSupportedPackages.getString().split(","));
        loadSupportedTypes(supportedRemotePackages, fscSupportedRemotePackages.getString().split(","));
        return supportedLocalPackages.contains(packageType) || supportedRemotePackages.contains(packageType);
    }

    boolean isNumberOfLocalOrRemoteReposReached(String repoKey) {
        VirtualRepoDescriptor virtualRepoDescriptor =
                ContextHelper.get().beanForType(RepositoryService.class).virtualRepoDescriptorByKey(repoKey);
        if (isNull(virtualRepoDescriptor)) {
            log.debug("Repo {} is not a virtual repository, not using local cache", repoKey);
            return false;
        }
        ReposCount numberOfLocals = getNumberOfLocalRepos(repoKey);
        int minLocals = fscLocalReposMinimum.getInt();
        int minRemotes = fscRemoteReposMinimum.getInt();
        if (numberOfLocals.numberOfLocals >= minLocals || numberOfLocals.numberOfRemotes >= minRemotes) {
            return true;
        }

        log.debug("Virtual repo {} has not enough local/remote repos, the minimum is {} locals or {} remote" +
                "won't user local cache", repoKey, minLocals, minRemotes);
        return false;
    }

    private Cache<String, ReposCount> initVirtualRepoNumberOfLocalReposCache() {
        return CacheBuilder.newBuilder()
                .maximumSize(reposItemsLimitReachedCacheSize.getLong())
                .expireAfterWrite(reposItemsLimitReachedCacheExpMS.getLong(), MILLISECONDS)
                .build();
    }

    private ReposCount getNumberOfLocalRepos(String repoKey) {
        ReposCount reposCount = getVirtualRepoNumberOfLocalRepos().getIfPresent(repoKey);
        if (reposCount == null) {
            List<RepoDescriptor> repos = ContextHelper.get().beanForType(RepositoryService.class)
                    .getVirtualResolvedLocalFederatedAndCacheDescriptors(repoKey);
            int numberOfLocals = (int) repos.stream().filter(repo -> !repo.getKey().contains(PATH_SUFFIX)).count();
            int numberOfRemotesRepos = repos.size() - numberOfLocals;
            reposCount = new ReposCount(numberOfLocals, numberOfRemotesRepos);
            getVirtualRepoNumberOfLocalRepos().put(repoKey, reposCount);
        }
        return reposCount;
    }

    private void loadSupportedTypes(Set<RepoType> supportedPackages, String[] fscSupportedPackages) {
        if (supportedPackages.isEmpty()) {
            synchronized (supportedPackagesLock) {
                if (supportedPackages.isEmpty()) {
                    Set<RepoType> supported = Stream.of(fscSupportedPackages)
                            .map(String::trim)
                            .filter(StringUtils::isNotBlank)
                            .map(RepoType::fromType)
                            .collect(Collectors.toSet());
                    supportedPackages.addAll(supported);
                }
            }
        }
    }

    static class ReposCount {
        int numberOfLocals;
        int numberOfRemotes;

        public ReposCount(int numberOfLocals, int numberOfRemotes) {
            this.numberOfLocals = numberOfLocals;
            this.numberOfRemotes = numberOfRemotes;
        }
    }
}