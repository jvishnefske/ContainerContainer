package org.artifactory.storage.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.metrics.backoffice.ArtifactoryBackofficeMetricLogger;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.util.AolUtils;
import org.jfrog.metrics.backoffice.exception.BackofficeMetricsException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class StorageSummaryBackofficeMetricLogger {
    private final ArtifactoryBackofficeMetricLogger backofficeMetricLogger;

    public StorageSummaryBackofficeMetricLogger(ArtifactoryBackofficeMetricLogger backofficeMetricLogger) {
        this.backofficeMetricLogger = backofficeMetricLogger;
    }

    /**
     * Log storage summary metrics
     *
     * @param storageSummaryInfo Storage Summary Info to log
     */
    public void logMetrics(@NonNull StorageSummaryInfo storageSummaryInfo) throws BackofficeMetricsException {
        if (!isEnabled()) {
            log.trace("Storage per repo metrics are disabled");
            return;
        }
        int metricsCount = 0;
        log.info("Start logging storage summary metrics");
        // Log metrics for each repository
        for (Map.Entry<String, RepoStorageSummaryInfo> entry : storageSummaryInfo.getRepoStorageSummaries()
                .entrySet()) {
            String repoName = entry.getKey();
            RepoStorageSummaryInfo repoSumInfo = entry.getValue();
            String repoType = repoSumInfo.getRepoType().name();
            String packageType = repoSumInfo.getType();
            String project = repoSumInfo.getProjectKey();
            Map<String, String> labels = Map.of(
                    "name", repoName,
                    "repo_type", repoType,
                    "package_type", packageType,
                    "project", project
            );

            backofficeMetricLogger.logMetric(labels, "jfrt_storage_repo_used_space", repoSumInfo.getUsedSpace());
            backofficeMetricLogger.logMetric(labels, "jfrt_storage_repo_files", repoSumInfo.getFilesCount());
            metricsCount++;
        }
        log.info("Finish logging storage summary metrics: {} out of {} were logged", metricsCount,
                storageSummaryInfo.getRepoStorageSummaries().size());
    }

    /**
     * Checks if the storage per repo metrics are enabled.
     * The metrics are enabled if RT runs as SaaS or if
     * the {@linkplain ConstantValues#storagePerRepoMetricsEnabled} property is set to <code>true</code>.
     *
     * @return <code>true</code> if the metrics are enabled, <code>false</code> otherwise
     */
    public boolean isEnabled() {
        return AolUtils.isAol() || ConstantValues.storagePerRepoMetricsEnabled.getBoolean();
    }
}
