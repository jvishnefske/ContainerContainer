package org.artifactory.throttling.qrl.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.metrics.core.CoreMetricsRegistry;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRateLimiterMetricProvider implements MetricProvider {

    private final QueryRateLimiterService qrlService;
    private final CoreMetricsRegistry coreMetricsRegistry;

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

    @Override
    public String getName() {
        return "jfrt_qrl";
    }

    @Override
    public String getDescription() {
        return "Artifactory Query Rate Limiter Metric Provider";
    }

    @Override
    public long getRefreshIntervalMillis() {
        return SECONDS.toMillis(60);
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        if (!qrlService.isFeatureDisabled()) {
            metrics.addAll(coreMetricsRegistry.getQueryRateLimiterMetrics());
        }
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
