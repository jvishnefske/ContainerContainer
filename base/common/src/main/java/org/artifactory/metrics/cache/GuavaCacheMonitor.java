package org.artifactory.metrics.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public interface GuavaCacheMonitor {
    /**
     * Record metrics on a Guava cache. You must call {@link CacheBuilder#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @return true if cache was instrumented, false if monitoring is disabled for this cache.
     * @see com.google.common.cache.CacheStats
     */
    boolean monitor(Cache<?,?> cache, String cacheName, Class<?> ownerClass);
}
