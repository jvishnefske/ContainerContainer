package org.artifactory.metrics.cache;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Monitor for Caffeine caches - provides integration with Micrometer metrics.
 * Similar to {@link GuavaCacheMonitor} but for Caffeine cache library.
 */
public interface CaffeineCacheMonitor {
    /**
     * Record metrics on a Caffeine cache.
     * You must call {@link com.github.benmanes.caffeine.cache.Caffeine#recordStats()}
     * prior to building the cache for metrics to be recorded.
     *
     * @param cache      The cache to instrument.
     * @param cacheName  Will be used to tag metrics with "cache".
     * @param ownerClass The class that owns the cache.
     * @param <K>        Cache key type.
     * @param <V>        Cache value type.
     * @param <C>        The cache type.
     * @return true if cache was instrumented, otherwise false (if monitoring is disabled for this cache)
     * @see com.github.benmanes.caffeine.cache.stats.CacheStats
     */
    boolean monitor(Cache<?, ?> cache, String cacheName, Class<?> ownerClass);
}
