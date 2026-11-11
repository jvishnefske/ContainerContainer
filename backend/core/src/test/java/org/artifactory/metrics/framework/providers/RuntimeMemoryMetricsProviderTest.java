package org.artifactory.metrics.framework.providers;

import org.jfrog.metrics.model.Metric;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RuntimeMemoryMetricsProviderTest {
    RuntimeMemoryMetricsProvider runtimeMemoryMetricProvider;

    @BeforeClass
    public void setUp() {
        runtimeMemoryMetricProvider = new RuntimeMemoryMetricsProvider();
    }

    @Test
    public void testGetName() {
        assertEquals("jfrt_runtime_heap", runtimeMemoryMetricProvider.getName());
    }

    @Test
    public void testGetDescription() {
        assertEquals("Artifactory Runtime Heap Metrics Provider", runtimeMemoryMetricProvider.getDescription());
    }

    @Test
    public void testGetRefreshIntervalMillis() {
        assertEquals(0L, runtimeMemoryMetricProvider.getRefreshIntervalMillis());
    }

    @Test
    public void testCollect() {
        java.util.List<Metric> metricList = runtimeMemoryMetricProvider.collect();
        assertEquals(metricList.size(), 4);
        List<String> allowedMetrics = new ArrayList<>();
        allowedMetrics.add("freememory");
        allowedMetrics.add("maxmemory");
        allowedMetrics.add("totalmemory");
        allowedMetrics.add("processors");
        assertTrue(metricList.stream().allMatch(metric -> allowedMetrics.contains(metric.getName())));
    }
}
