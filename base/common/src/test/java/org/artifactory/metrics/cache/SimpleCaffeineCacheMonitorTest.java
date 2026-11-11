package org.artifactory.metrics.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author oleksandrl
 */
class SimpleCaffeineCacheMonitorTest {
    SimpleCaffeineCacheMonitor monitor;
    Cache<String, String> cache;

    @BeforeEach
    void beforeEach() {
        monitor = new SimpleCaffeineCacheMonitor(Metrics.globalRegistry, false, List.of(), List.of(), List.of());
        cache = Caffeine.newBuilder().maximumSize(2).recordStats().build();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @Test
    void monitor_shouldRegisterCache_inMetricsRegistry() {
        // Arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.globalRegistry.add(registry);
        // Act
        boolean actual = monitor.monitor(cache, "sample", getClass());
        // Assert
        assertTrue(actual);
        cache.get("first", key -> "sample"); // miss
        cache.put("second", "value"); // put
        cache.get("first", key -> "sample"); // hit
        cache.get("first", key -> "sample"); // hit
        cache.put("third", "value"); // put, evict
        cache.cleanUp();
        String metrics = registry.getMetersAsString();
        assertEquals("""
                cache.eviction.weight(COUNTER)[cache='sample']; count=1.0
                cache.evictions(COUNTER)[cache='sample']; count=1.0
                cache.gets(COUNTER)[cache='sample', result='miss']; count=1.0
                cache.gets(COUNTER)[cache='sample', result='hit']; count=2.0
                cache.puts(COUNTER)[cache='sample']; count=1.0
                cache.size(GAUGE)[cache='sample']; value=2.0""", metrics);
    }

    @Test
    void monitor_shouldReturnFalse_whenCacheIsNull() {
        // Act
        boolean result = monitor.monitor(null, "sample", getClass());
        // Assert
        assertFalse(result);
    }

    @Test
    void monitor_shouldReturnFalse_whenDisabled() {
        // Arrange
        monitor.setDisabled(true);
        // Act
        boolean actual = monitor.monitor(cache, "sample", getClass());
        // Assert
        assertFalse(actual);
    }

    @Test
    void monitor_shouldReturnFalse_whenCacheNameIsInIgnoreList() {
        // Arrange
        monitor.getCachesToIgnore().add("ignored-cache");
        // Act
        boolean result = monitor.monitor(cache, "ignored-cache", getClass());
        // Assert
        assertFalse(result);
    }

    @Test
    void monitor_shouldReturnFalse_whenClassIsInIgnoreList() {
        // Arrange
        monitor.getClassesToIgnore().add("SimpleCaffeineCacheMonitorTest");
        // Act
        boolean result = monitor.monitor(cache, "test-cache", getClass());
        // Assert
        assertFalse(result);
    }

    @Test
    void monitor_shouldReturnFalse_whenPackageIsInIgnoreList() {
        // Arrange
        monitor.getPackagesToIgnore().add("org.artifactory.metrics.cache");
        // Act
        boolean result = monitor.monitor(cache, "test-cache", getClass());
        // Assert
        assertFalse(result);
    }
}
