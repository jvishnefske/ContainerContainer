package org.artifactory.metrics.core;

import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;
import org.artifactory.throtlling.qrl.model.QueryRateLimiterMetrics;
import org.jfrog.metrics.model.Metric;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyMap;
import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;
import static org.jfrog.metrics.model.Metric.MetricType.GAUGE;
import static org.jfrog.metrics.model.Metric.MetricType.RATIO;

/**
 * @author Andrey Tashlitsky
 */
@Component
public class CoreMetricsRegistry {

    private final AtomicReference<Map<QueryRateLimiterType, QueryRateLimiterMetrics>> qrlMetrics =
            new AtomicReference<>(emptyMap());

    public void setQueryRateLimiterMetrics(@Nonnull Map<QueryRateLimiterType, QueryRateLimiterMetrics> metrics) {
        qrlMetrics.set(metrics);
    }

    @Nonnull
    public List<Metric> getQueryRateLimiterMetrics() {
        List<Metric> metricList = new ArrayList<>();
        qrlMetrics.get().forEach((type, metrics) -> {
            Map<String, Object> labels = Map.of("qrl_type", type.name().toLowerCase());
            addMetricWithLabelsToList(metricList, labels, "permits", GAUGE, "Permits", metrics.getTotalPermits());
            addMetricWithLabelsToList(metricList, labels, "queries", GAUGE, "Queries", metrics.getTotalQueries());
            addMetricWithLabelsToList(metricList, labels, "charged_query_time", GAUGE, "Charged Query Time in Millis",
                    metrics.getTotalChargedQueryTime());
            addMetricWithLabelsToList(metricList, labels, "slowed_down", RATIO, "Slowed Down",
                    metrics.getTotalSlowedDownPercentage());
            addMetricWithLabelsToList(metricList, labels, "slowed_down_by_time_quota", RATIO,
                    "Time Slowed Down By Time Quota",
                    metrics.getTotalSlowedDownByTimePercentage());
        });
        return metricList;
    }
}