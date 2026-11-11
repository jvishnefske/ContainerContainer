package org.artifactory.metrics.framework.providers;

import org.artifactory.shadow.ShadowRequestService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ShadowMetricProvider implements MetricProvider {
    private final ShadowRequestService shadowRequestService;

    public ShadowMetricProvider(ShadowRequestService shadowRequestService) {
        this.shadowRequestService = shadowRequestService;
    }

    @Override
    public String getName() {
        return "jfrt_shadow";
    }

    @Override
    public String getDescription() {
        return "Artifactory Shadow testing Metric Provider";
    }

    @Override
    public List<Metric> collect() {
        return shadowRequestService.collectMetrics();
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0; // should be 0 for CONTINUOUS metrics
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }
}
