package org.artifactory.throttling.rrl.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;
import static org.jfrog.metrics.model.Metric.MetricType.COUNTER;

/**
 * @author Igor Gumush
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestRateLimiterMetricProvider implements MetricProvider {

    private final IRequestRateLimiterService rrlService;

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

    @Override
    public String getName() {
        return "jfrt_rrl";
    }

    @Override
    public String getDescription() {
        return "Artifactory Request Rate Limiter Metric Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return SECONDS.toMillis(60);
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        if (rrlService.isFeatureDisabled()) {
            return metrics;
        }
        rrlService.getMetrics().forEach((domainSuffix, rlMetrics) -> {
            Map<String, Object> labels = Map.of(
                    "domainSuffix", domainSuffix,
                    "isBlockingEnabled", ConstantValues.rrlGenericIsBlockingModeEnabled.getBoolean()
            );

            rlMetrics.forEach((metricKey, metricValue) -> {
                String label = metricKey.toLowerCase().replace(" ", "_");
                addMetricWithLabelsToList(metrics, labels, label, COUNTER, metricKey, metricValue);
            });
        });
        return metrics;
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0;
    }
}