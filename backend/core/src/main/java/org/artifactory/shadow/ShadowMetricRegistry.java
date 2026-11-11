package org.artifactory.shadow;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.jfrog.metrics.model.Metric;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Michael Akushsky
 */
@Slf4j
public class ShadowMetricRegistry {
    private final LoadingCache<ShadowMetricWithLabels, LongAdder> shadowMetricWithLabelsRegistry;

    public ShadowMetricRegistry() {
        this.shadowMetricWithLabelsRegistry = CacheBuilder.newBuilder()
                .build(CacheLoader.from(metric -> new LongAdder()));
    }

    public void inc(ShadowMetric metric, Map<String, Object> labels) {
        inc(metric, labels, 1L);
    }

    public void inc(ShadowMetric metric, Map<String, Object> labels, long value) {
        if (metric.getReportType() != Metric.MetricType.COUNTER && metric.getReportType() != Metric.MetricType.BYTES) {
            throw new IllegalArgumentException("Only counters can be incremented.");
        }
        ShadowMetricWithLabels shadowMetricWithLabels = new ShadowMetricWithLabels(metric, labels);
        shadowMetricWithLabelsRegistry.getUnchecked(shadowMetricWithLabels).add(value);
    }

    public List<Metric> collectAndMap(List<ShadowMetric> toCollect) {
        List<Metric> result = new LinkedList<>();
        for (ShadowMetric metric : toCollect) {
            if (metric.getReportType() == Metric.MetricType.BYTES ||
                    metric.getReportType() == Metric.MetricType.COUNTER) {
                List<Metric> metricsWithLabels = collectAllLabelsForMetric(metric);
                result.addAll(metricsWithLabels);
            }
        }
        return result;
    }

    private List<Metric> collectAllLabelsForMetric(ShadowMetric shadowMetric) {
        return shadowMetricWithLabelsRegistry.asMap().entrySet().stream()
                .filter(entry -> shadowMetric == entry.getKey().shadowMetric)
                .map(ShadowMetricRegistry::getMetric).toList();
    }

    private static Metric getMetric(Map.Entry<ShadowMetricWithLabels, LongAdder> entry) {
        Map<String, Object> allLabels = new HashMap<>(entry.getKey().labels());
        ShadowMetric metric = entry.getKey().shadowMetric();
        Metric.MetricBuilder builder = metric.toJfrogMetric().labels(allLabels);
        builder.value(entry.getValue().longValue());
        return builder.build();
    }

    private record ShadowMetricWithLabels(ShadowMetric shadowMetric, Map<String, Object> labels) {
    }
}
