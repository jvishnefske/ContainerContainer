package org.artifactory.metrics;

import lombok.experimental.UtilityClass;
import org.jfrog.metrics.model.Metric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UtilityClass
public class MetricsUtil {

    private Metric buildMetric(Map<String, Object> labels, String metricName, Metric.MetricType metricType,
            String description, Number metricValue) {
        return Metric.builder()
                .name(metricName)
                .type(metricType)
                .labels(labels)
                .description(description)
                .value(metricValue)
                .build();
    }

    public void addMetricWithLabelsToList(List<Metric> metrics, Map<String, Object> labels, String metricName,
            Metric.MetricType metricType, String description, Number metricValue) {
        metrics.add(buildMetric(labels, metricName, metricType, description, metricValue));
    }

    public void addMetricToList(List<Metric> metrics, String metricName, Metric.MetricType metricType,
            String description, Number metricValue) {
        Map<String, Object> labels = new HashMap<>();
        metrics.add(buildMetric(labels, metricName, metricType, description, metricValue));
    }
}
