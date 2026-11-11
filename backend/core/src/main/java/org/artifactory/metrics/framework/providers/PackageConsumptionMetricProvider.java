package org.artifactory.metrics.framework.providers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.analytics.PackageUsageCounter;
import org.artifactory.common.ConstantValues;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.collectPackagesDownloadMetrics;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.collectPackagesHeadRequestsMetrics;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.collectPackagesRedirectMetrics;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.collectPackagesUploadMetrics;

/**
 * Package consumption metric provider is handling the consumption logs collection of packages
 * downloads, uploads, redirect and head requests.
 * <p>
 * Log output example:
 * jfrt_package_event_count{package_type="docker", action_type="upload"} 1 1690975167832
 * </p>
 *
 * @author BarakH
 */
@Slf4j
public class PackageConsumptionMetricProvider implements MetricProvider {

    private final AddonsManager addonsManager;

    public PackageConsumptionMetricProvider(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public String getName() {
        return "jfrt_package_event";
    }

    @Override
    public String getDescription() {
        return "Packages consumption metrics";
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        return 0;
    }

    @Override
    public long getRefreshIntervalMillis() {
        long refreshInterval = ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds.getLong() - 1;
        if (refreshInterval < 0) {
            refreshInterval = 60;
        }
        return TimeUnit.SECONDS.toMillis(refreshInterval);
    }

    @Override
    public List<Metric> collect() {
        log.debug("Started collecting package consumption metrics");
        List<Metric> metrics = new ArrayList<>();
        PackageUsageCounter packageUsageData = addonsManager.addonByType(AnalyticsAddon.class).getPackageUsageData();
        collectPackagesDownloadMetrics(metrics, packageUsageData);
        collectPackagesUploadMetrics(metrics, packageUsageData);
        collectPackagesRedirectMetrics(metrics, packageUsageData);
        collectPackagesHeadRequestsMetrics(metrics, packageUsageData);
        return metrics;
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }
}
