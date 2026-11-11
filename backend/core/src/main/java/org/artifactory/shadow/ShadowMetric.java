package org.artifactory.shadow;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jfrog.metrics.model.Metric;

import static org.jfrog.metrics.model.Metric.MetricBuilder;
import static org.jfrog.metrics.model.Metric.MetricType;
import static org.jfrog.metrics.model.Metric.MetricType.COUNTER;

/**
 * @author Michael Akushsky
 * List all metric collected by shadow requests service.
 * <a href="https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md">naming convension</a>
 */
@Getter
@AllArgsConstructor
public enum ShadowMetric {

    shadowedRequests("requests", "Counts the total shadowed requests", COUNTER);

    private final String key;
    private final String description;
    private final MetricType reportType;

    public MetricBuilder toJfrogMetric() {
        return Metric.builder().name(this.key).description(this.description).type(this.reportType);
    }
}
