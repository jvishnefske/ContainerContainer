package org.artifactory.metrics;

import org.jfrog.metrics.model.Metric;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsUtilTest {

    @Test
    void testAddMetricWithoutPoolLabel() {
        List<Metric> metrics = new ArrayList<>();
        Map<String, Object> labels = Map.of();
        String metricName = "metric_name";
        String description = "description";
        Number metricValue = 1;
        MetricsUtil.addMetricWithLabelsToList(metrics, labels, metricName, Metric.MetricType.COUNTER, description,
                metricValue);
        assertEquals("metric_name", metrics.get(0).getName());
    }
}