package org.artifactory.metrics.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Metrics;
import lombok.experimental.UtilityClass;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;

import java.util.List;

/**
 * Provides static function that can be used everywhere (without dependency injection) to monitor usage
 * of Guava and Caffeine cache metrics and exposing that usage as OpenMetrics.
 * <p>
 *     The usage if of a static helper instead of dependency injection was intentional. In the near future
 *     we plan to replace this implementation with aspects (based on AspectJ compile-time weaving), so it
 *     does not make sense to add new dependency to many classes (and their tests) to just remove it later,
 *     after moving to AOP.
 * </p>
 * <p>
 *    Using this class is straightforward:
 *    <pre>{@code
 *       void initCache() {
 *           // Note: calling recordStats() is critical, without it, all metric counters will be zero
 *           Cache<String,String> cache = CacheBuilder.newBuilder().recordStats().build();
 *           // The cache name parameter ("my-cache") will then appear in a "name" label
 *           GlobalCacheMetrics.monitor(cache, "my-cache", getClass());
 *       }
 *    }</pre>
 * </p>
 * <p>
 *     The behavior of the global cache metrics can be controlled by five system properties:
 *     <ul>
 *         <li><b>artifactory.metrics.enabled</b> - a boolean that enables/disabled all metrics</li>
 *         <li><b>artifactory.metrics.caches.enabled</b> - a boolean that enables/disabled cache metrics</li>
 *         <li><b>artifactory.metrics.caches.ignore.names</b> - list of cache names to exclude from metrics</li>
 *         <li><b>artifactory.metrics.caches.ignore.classes</b> - list of owner class names, whose caches should
 *         be excluded from metrics.</li>
 *         <li><b>artifactory.metrics.caches.ignore.packages</b> - list of owner class package names, whose caches
 *         should be excluded from metrics.</li>
 *     </ul>
 * </p>
 */
@UtilityClass
public class GlobalCacheMetrics {
    public static final GuavaCacheMonitor GLOBAL_GUAVA_MONITOR = createGlobalGuavaMonitor();
    public static final CaffeineCacheMonitor GLOBAL_CAFFEINE_MONITOR = createGlobalCaffeineMonitor();

    @VisibleForTesting
    static SimpleGuavaCacheMonitor createGlobalGuavaMonitor() {
        if (!ArtifactoryHome.isBound()) {
            return new SimpleGuavaCacheMonitor(Metrics.globalRegistry, true, List.of(), List.of(), List.of());
        }
        return new SimpleGuavaCacheMonitor(
                Metrics.globalRegistry,
                !ConstantValues.metricsEnabled.getBoolean() || !ConstantValues.cacheMetricsEnabled.getBoolean(),
                ConstantValues.cacheMetricsIgnoreNames.getList(),
                ConstantValues.cacheMetricsIgnoreClasses.getList(),
                ConstantValues.cacheMetricsIgnorePackages.getList()
            );
    }

    @VisibleForTesting
    static SimpleCaffeineCacheMonitor createGlobalCaffeineMonitor() {
        if (!ArtifactoryHome.isBound()) {
            return new SimpleCaffeineCacheMonitor(Metrics.globalRegistry, true, List.of(), List.of(), List.of());
        }
        return new SimpleCaffeineCacheMonitor(
                Metrics.globalRegistry,
                !ConstantValues.metricsEnabled.getBoolean() || !ConstantValues.cacheMetricsEnabled.getBoolean(),
                ConstantValues.cacheMetricsIgnoreNames.getList(),
                ConstantValues.cacheMetricsIgnoreClasses.getList(),
                ConstantValues.cacheMetricsIgnorePackages.getList()
        );
    }

    /**
     * Record metrics on a Guava cache. You must call {@link com.google.common.cache.CacheBuilder#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @param <C> The cache type.
     * @return true if cache was instrumented, otherwise false (if monitoring is disabled for this cache)
     * @see com.google.common.cache.CacheStats
     */
    public static <K, V, C extends com.google.common.cache.Cache<K, V>> boolean monitorGuava(
            C cache, String cacheName, Class<?> ownerClass) {
        return GLOBAL_GUAVA_MONITOR.monitor(cache, cacheName, ownerClass);
    }

    /**
     * Record metrics on a Caffeine cache.
     * You must call {@link com.github.benmanes.caffeine.cache.Caffeine#recordStats()}
     * prior to building the cache for metrics to be recorded.
     * @param cache The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param <K> Cache key type.
     * @param <V> Cache value type.
     * @param <C> The cache type.
     * @return true if cache was instrumented, otherwise false (if monitoring is disabled for this cache)
     * @see com.github.benmanes.caffeine.cache.stats.CacheStats
     */
    public static <K, V, C extends Cache<K, V>> boolean monitorCaffeine(
            C cache, String cacheName, Class<?> ownerClass) {
        return GLOBAL_CAFFEINE_MONITOR.monitor(cache, cacheName, ownerClass);
    }
}
