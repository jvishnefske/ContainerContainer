package org.artifactory.metrics.cache;

import com.google.common.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.GuavaCacheMetrics;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * The default implementation of the {@link GuavaCacheMonitor}, binding Guava caches to OpenMetrics.
 */
@Slf4j
@Getter
public class SimpleGuavaCacheMonitor implements GuavaCacheMonitor {
    protected MeterRegistry meterRegistry;
    @Setter
    protected boolean disabled;
    protected final Set<String> packagesToIgnore = new HashSet<>();
    protected final Set<String> classesToIgnore = new HashSet<>();
    protected final Set<String> cachesToIgnore = new HashSet<>();

    public SimpleGuavaCacheMonitor(MeterRegistry meterRegistry,
            boolean disabled,
            Collection<String> ignoreCaches,
            Collection<String> ignoreClasses,
            Collection<String> ignorePackages) {
        this.meterRegistry = meterRegistry;
        this.disabled = disabled;
        this.packagesToIgnore.addAll(ignorePackages);
        this.classesToIgnore.addAll(ignoreClasses);
        this.cachesToIgnore.addAll(ignoreCaches);
    }

    @Override
    public boolean monitor(Cache<?,?> cache, String cacheName, Class<?> ownerClass) {
        if (cache == null || disabled || CacheMonitorUtils
                .shouldIgnore(cacheName, ownerClass, packagesToIgnore, classesToIgnore, cachesToIgnore)) {
            log.debug("Skip monitoring the {} Guava cache (owner class={}).", cacheName, ownerClass);
            return false;
        }
        GuavaCacheMetrics.monitor(this.meterRegistry, cache, cacheName);
        return true;
    }
}
