package org.artifactory.metrics.framework.providers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Package count metric provider is handling the generic packages counting for the consumption logs.
 * <p>
 * Log output example:
 * jfrt_package_count_total{package_type="generic"} 101 1690975167832
 * </p>
 *
 * @author BarakH
 */
@Slf4j
public class PackageCountMetricProvider implements MetricProvider {

    private static final Set<String> GENERIC_PACKAGES_TYPES =
            Set.of(RepoType.Generic.getDisplayName(), RepoType.GitLfs.getDisplayName(), RepoType.VCS.getDisplayName());
    private final StorageSummaryService storageSummaryService;
    private long lastUpdatedMillis = -1;

    public PackageCountMetricProvider(StorageSummaryService storageSummaryService) {
        this.storageSummaryService = storageSummaryService;
    }

    @Override
    public String getName() {
        return "jfrt_package_count";
    }

    @Override
    public String getDescription() {
        return "Packages count consumption metrics";
    }

    @Override
    public ReportType getReportType() {
        return ReportType.CONTINUOUS;
    }

    @Override
    public long getLastUpdatedMillis() {
        StorageSummaryInfo info = getStorageSummary();
        return (info == null) ? 0 : info.getLastUpdated();
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds.getLong());
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

    @Override
    public List<Metric> collect() {
        log.debug("Started collecting package count metrics");
        List<Metric> metrics = new ArrayList<>();
        StorageSummaryInfo localCache = getStorageSummary();
        if (localCache == null) {
            return metrics;
        }
        int countIntervalMinutes = ConstantValues.consumptionUsageMetricsPackagesCountIntervalMinutes.getInt();
        if (lastUpdatedMillis + TimeUnit.MINUTES.toMillis(countIntervalMinutes) > System.currentTimeMillis()) {
            log.debug("Skipping package count metrics collection, last update was less than {} minutes ago",
                    countIntervalMinutes);
            return metrics;
        }
        lastUpdatedMillis = System.currentTimeMillis();
        Map<String, Long> repoTypeToFilesCount = localCache.getRepoStorageSummaries()
                .values()
                .stream()
                .filter(r -> GENERIC_PACKAGES_TYPES.contains(r.getType()))
                .collect(Collectors.groupingBy(RepoStorageSummaryInfo::getType,
                        Collectors.summingLong(RepoStorageSummaryInfo::getFilesCount)));
        PackagesConsumptionMetricsHelper.collectPackagesCountMetrics(metrics, repoTypeToFilesCount);
        return metrics;
    }

    private StorageSummaryInfo getStorageSummary() {
        try {
            return this.storageSummaryService.getLocalCache();
        } catch (CacheUnAvailableException ignored) {
            log.debug("Storage summary is unavailable, cannot collect data");
            return null;
        }
    }
}
