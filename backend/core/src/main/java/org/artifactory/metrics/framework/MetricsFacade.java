package org.artifactory.metrics.framework;

import com.google.common.annotations.VisibleForTesting;
import org.artifactory.common.ConstantValues;
import org.artifactory.metrics.framework.federation.FederationMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Facade for metrics collection.
 *
 * @author Igor Usenko
 */
@Service
public class MetricsFacade {

    private final MetricsService metricsService;
    private final LastUsageMonitor metricsServiceMonitor;

    private final FederationMetricsService federationMetricsService;

    @Autowired
    public MetricsFacade(FederationMetricsService federationMetricsService, MetricsService metricsService) {
        this(federationMetricsService,
                metricsService,
                new LastUsageMonitor(
                        ConstantValues.collectMetricsTimeout.getLong(),
                        "Metrics are not collected for more than %d milliseconds".formatted(
                                ConstantValues.collectMetricsTimeout.getLong()),
                        System::currentTimeMillis)
        );
    }

    @VisibleForTesting
    MetricsFacade(FederationMetricsService federationMetricsService,
            MetricsService metricsService, LastUsageMonitor lastUsageMonitor) {
        this.federationMetricsService = federationMetricsService;
        this.metricsService = metricsService;
        this.metricsServiceMonitor = lastUsageMonitor;
    }

    public String getAllOpenMetrics() {
        String metrics = getMetrics(false);
        if (isNotBlank(metrics)) {
            metricsServiceMonitor.register();
        }
        return metrics;
    }

    public String getApplicationOpenMetrics() {
        return getMetrics(true);
    }

    public void checkMetricsLastUsage() {
        metricsServiceMonitor.check();
    }

    private String getMetrics(boolean applicationMetricsOnly) {
        String metrics = metricsService.getOpenMetrics(applicationMetricsOnly);
        metrics += federationMetricsService.getOpenMetrics(applicationMetricsOnly);
        return metrics;
    }

}
