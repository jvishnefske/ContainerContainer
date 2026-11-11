package org.artifactory.shadow;

import org.jfrog.metrics.model.Metric;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Michael Akushsky
 */
class ShadowMetricRegistryTest {

    @Test
    void testGetNonExistsMetric() {
        final ShadowMetricRegistry shadowMetricRegistry = new ShadowMetricRegistry();
        assertEquals(0, shadowMetricRegistry.collectAndMap(List.of(ShadowMetric.shadowedRequests)).size());
    }

    @Test
    void testMaxInc() {
        Map<String, Object> labels = Map.of("packageType", "npm");
        final ShadowMetricRegistry shadowMetricRegistry = new ShadowMetricRegistry();
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests, labels, Long.MAX_VALUE);
        Number value = shadowMetricRegistry.collectAndMap(List.of(ShadowMetric.shadowedRequests)).get(0).getValue();
        assertEquals(Long.MAX_VALUE, value);
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests, labels, 5);
        value = shadowMetricRegistry.collectAndMap(List.of(ShadowMetric.shadowedRequests)).get(0).getValue();
        assertEquals("-9223372036854775804", value.toString());
    }

    @Test
    void collectMetricsWithLabels() {
        Map<String, Object> labelsNpm = Map.of("packageType", "npm", "result", 1);
        Map<String, Object> labelsVcs = Map.of("packageType", "vcs", "result", 0, "reason", "headers");
        final ShadowMetricRegistry shadowMetricRegistry = new ShadowMetricRegistry();
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests, labelsNpm);
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests, labelsVcs);
        List<Metric> metrics =
                shadowMetricRegistry.collectAndMap(List.of(ShadowMetric.shadowedRequests));
        assertEquals(2, metrics.size());
        assertEquals(ShadowMetric.shadowedRequests.getKey(), metrics.get(0).getName());
        assertEquals(1L, metrics.get(0).getValue());
        assertEquals(ShadowMetric.shadowedRequests.getKey(), metrics.get(1).getName());
        assertEquals(1L, metrics.get(1).getValue());
        // Order could be different, so checking anyMatch way
        assertTrue(metrics.stream().anyMatch(metric -> metric.getLabels().equals(labelsVcs)));
        assertTrue(metrics.stream().anyMatch(metric -> metric.getLabels().equals(labelsNpm)));
    }
}