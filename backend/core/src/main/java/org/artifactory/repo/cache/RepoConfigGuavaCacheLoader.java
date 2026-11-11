package org.artifactory.repo.cache;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheLoader;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.model.RepoConfig;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import static org.artifactory.repo.cache.RepoConfigCacheLoaderOptimizationUtils.shouldGetAllOptimizationBeApplied;

/**
 * Cache loader for repo configs. Based on {@link CacheLoader}.
 * Has optimization for batch load repo configs by keys according to an actual state of repo count and app settings,
 * since sometimes it is faster to load all data than do find by keys via IN.
 */
@Slf4j
public class RepoConfigGuavaCacheLoader extends CacheLoader<String, RepoConfig> {
    private final Supplier<Integer> allReposCountFunction;
    private final Supplier<Map<String, RepoConfig>> getAllRepoConfigsFunction;
    private final Function<String, RepoConfig> getRepoConfigByKeyFunction;
    private final Function<Set<String>, Map<String, RepoConfig>> getRepoConfigsByKeysFunction;
    private final int batchOperationSize;
    private final double optimizationRatio;

    /**
     * @param batchOperationSize           - DB batch operation size for load all query
     * @param optimizationRatio            - optimization ratio to define when optimization should be applied.
     *                                     If less than 0 - will not be applied.
     *                                     If equals to 0 - will be applied always.
     * @param allReposCountFunction        - function to get current number of repos
     * @param getAllRepoConfigsFunction    - function to load all repo configs from DB
     * @param getRepoConfigByKeyFunction   - function to load a repo config by key from DB
     * @param getRepoConfigsByKeysFunction - function to load repo configs by keys from DB
     */
    public RepoConfigGuavaCacheLoader(int batchOperationSize, double optimizationRatio,
            Supplier<Integer> allReposCountFunction,
            Supplier<Map<String, RepoConfig>> getAllRepoConfigsFunction,
            Function<String, RepoConfig> getRepoConfigByKeyFunction,
            Function<Set<String>, Map<String, RepoConfig>> getRepoConfigsByKeysFunction) {
        this.batchOperationSize = batchOperationSize;
        this.optimizationRatio = optimizationRatio;
        this.allReposCountFunction = allReposCountFunction;
        this.getAllRepoConfigsFunction = getAllRepoConfigsFunction;
        this.getRepoConfigByKeyFunction = getRepoConfigByKeyFunction;
        this.getRepoConfigsByKeysFunction = getRepoConfigsByKeysFunction;
    }

    @Override
    public RepoConfig load(String key) {
        try {
            log.trace("Loading repo config key: {} from DB...", key);
            return getRepoConfigByKeyFunction.apply(key);
        } finally {
            log.trace("Loading repo config key: {} from DB has been done", key);
        }
    }

    @Override
    public Map<String, RepoConfig> loadAll(Iterable<? extends String> keys) throws Exception {
        try {
            int keysCount = (int) StreamSupport.stream(keys.spliterator(), false).count();
            if (shouldGetAllOptimizationBeApplied(keysCount, allReposCountFunction::get, batchOperationSize,
                    optimizationRatio)) {
                log.trace("Loading all repo configs from DB because of optimization...");
                // it is ok to return all of loaded repo configs, since they are loaded anyway. All of them
                // will be updated in cache
                return getAllRepoConfigsFunction.get();
            }

            if (log.isTraceEnabled()) {
                log.trace("Loading repo configs by keys: [{}] from DB...", Joiner.on(",").join(keys));
            }
            return getRepoConfigsByKeysFunction.apply((Set<String>) keys);
        } finally {
            if (log.isTraceEnabled()) {
                log.trace("Loading repo configs by keys: [{}] from DB has been done", Joiner.on(",").join(keys));
            }
        }
    }
}