package org.artifactory.metrics.backoffice;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.MetricsLoggerAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.jfrog.metrics.backoffice.model.Metric;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ArtifactoryBackofficeMetricLogger {

    /**
     * Log a metric with the given labels, metric name and value
     */
    public void logMetric(Map<String, String> labels, String metricName, long metricValue)
            throws BackofficeMetricsException {
        Metric boMetric = Metric.builder()
                .metricName(metricName)
                .labels(labels)
                .value(metricValue)
                .build();
        ArtifactoryContext artifactoryContext = ContextHelper.get();
        AddonsManager addonsManager = artifactoryContext.beanForType(AddonsManager.class);
        MetricsLoggerAddon metricsLoggerAddon = addonsManager.addonByType(MetricsLoggerAddon.class);
        log.debug("Logging metric: {}", boMetric.toString());
        metricsLoggerAddon.log(boMetric);
    }
}
