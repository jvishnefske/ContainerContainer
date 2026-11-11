package org.artifactory.metrics.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalCacheMetricsTest {
    ArtifactoryHomeStub homeStub;

    @BeforeEach
    void beforeEach() {
        Metrics.globalRegistry.clear();

        System.setProperty("artifactory.metrics.enabled", "true");
        System.setProperty("artifactory.metrics.caches.ignore.classes", "String, java.lang.Exception");

        this.homeStub = new ArtifactoryHomeStub(getClass().getName());
        this.homeStub.loadSystemProperties();
        ArtifactoryHome.bind(this.homeStub);

        // We need the following line just to make sure the GLOBAL_GUAVA_MONITOR instance is instantiated
        assertFalse(GlobalCacheMetrics.GLOBAL_GUAVA_MONITOR.monitor(null, "test", getClass()));
    }

    @AfterEach
    void cleanupAfterEach() {
        System.clearProperty("artifactory.metrics.enabled");
        System.clearProperty("artifactory.metrics.caches.enabled");
        System.clearProperty("artifactory.metrics.caches.ignore.names");
        System.clearProperty("artifactory.metrics.caches.ignore.classes");
        System.clearProperty("artifactory.metrics.caches.ignore.packages");
        Metrics.globalRegistry.clear();
        ArtifactoryHome.unbind();
    }

    @Test
    void globalCacheMetrics_shouldBeEnabledByDefault() {
        System.clearProperty("artifactory.metrics.enabled");
        this.homeStub.loadSystemProperties();

        SimpleGuavaCacheMonitor monitor = GlobalCacheMetrics.createGlobalGuavaMonitor();
        boolean actual = monitor.isDisabled();
        assertFalse(actual, "GlobalCacheMetrics disabled despite metrics being enabled by default");
    }

    @Test
    void globalCacheMetrics_shouldBeEnabledWhenMetricsAreEnabled() {
        System.setProperty("artifactory.metrics.enabled", "true");
        this.homeStub.loadSystemProperties();

        SimpleGuavaCacheMonitor monitor = GlobalCacheMetrics.createGlobalGuavaMonitor();
        boolean actual = monitor.isDisabled();
        assertFalse(actual, "GlobalCacheMetrics disabled despite artifactory.metrics.enabled=true");
    }

    @Test
    void globalCacheMetrics_shouldBeDisabledWhenMonitoringIsExplicitlyDisabled() {
        System.setProperty("artifactory.metrics.caches.enabled", "false");
        this.homeStub.loadSystemProperties();

        SimpleGuavaCacheMonitor monitor = GlobalCacheMetrics.createGlobalGuavaMonitor();
        boolean actual = monitor.isDisabled();
        assertTrue(actual, "GlobalCacheMetrics enabled despite artifactory.metrics.caches.enabled=false");
    }

    @Test
    void globalCacheMetrics_shouldReadIgnoreListsFromConfig() {
        System.setProperty("artifactory.metrics.caches.ignore.names", "test, sample, ");
        System.setProperty("artifactory.metrics.caches.ignore.classes", "String, java.lang.Exception");
        System.setProperty("artifactory.metrics.caches.ignore.packages", "org.artifactory.packages");
        this.homeStub.loadSystemProperties();

        SimpleGuavaCacheMonitor monitor = GlobalCacheMetrics.createGlobalGuavaMonitor();

        assertIterableEquals(List.of("test", "sample"), monitor.getCachesToIgnore());
        assertIterableEquals(List.of("String", "java.lang.Exception"), monitor.getClassesToIgnore());
        assertIterableEquals(List.of("org.artifactory.packages"), monitor.getPackagesToIgnore());
    }

    @Test
    void globalCacheMetrics_monitor_Guava_shouldReadConfigurationFromProperties() {
        SimpleGuavaCacheMonitor monitor = (SimpleGuavaCacheMonitor) GlobalCacheMetrics.GLOBAL_GUAVA_MONITOR;
        assertFalse(monitor.isDisabled(),
                "GlobalCacheMetrics.GLOBAL_GUAVA_MONITOR disabled despite metrics.enabled = true");
        assertTrue(monitor.getCachesToIgnore().isEmpty(), "Has caches to ignore despite empty config");
        assertIterableEquals(List.of("String", "java.lang.Exception"), monitor.getClassesToIgnore());
        assertTrue(monitor.getPackagesToIgnore().isEmpty(), "Has packages to ignore despite empty config");
    }

    @Test
    void globalCacheMetrics_monitorGuava_shouldReportMetrics() throws ExecutionException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Metrics.globalRegistry.add(registry);

        Cache<String,String> cache = CacheBuilder.newBuilder().maximumSize(2).recordStats().build();
        boolean actual = GlobalCacheMetrics.monitorGuava(cache, "global", getClass());
        assertTrue(actual, "monitor() did not instrument the given cache");

        // Make couple changes in the cache
        cache.get("1", () -> "A"); // miss
        cache.put("2", "B"); // put
        cache.get("1", () -> "A"); // hit
        cache.get("1", () -> "A"); // hit
        cache.put("3", "B"); // put, evict
        cache.cleanUp();

        // Now check that we indeed reported some metrics
        String metrics = registry.getMetersAsString();
        assertEquals("""
                cache.evictions(COUNTER)[cache='global']; count=1.0
                cache.gets(COUNTER)[cache='global', result='hit']; count=2.0
                cache.gets(COUNTER)[cache='global', result='miss']; count=1.0
                cache.puts(COUNTER)[cache='global']; count=1.0
                cache.size(GAUGE)[cache='global']; value=2.0""", metrics);
    }
}