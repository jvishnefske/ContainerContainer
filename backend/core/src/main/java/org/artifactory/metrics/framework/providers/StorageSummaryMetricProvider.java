package org.artifactory.metrics.framework.providers;

import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.metrics.framework.providers.util.NumericValuesSummary;
import org.artifactory.metrics.framework.providers.util.RepoTypeAndPackage;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.artifactory.metrics.MetricsUtil.addMetricToList;
import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;

/**
 * Provides storage metrics about the amount and size of files managed by the Artifactory.
 * The {@link StorageSummaryService} service computes and caches this information on application's startup and then
 * updates it from time to time (infrequently), using the scheduled job.
 *
 * @see StorageSummaryInfo
 * @see StorageSummaryService
 *
 * @author Sergey Steinvil
 */
@Component
public class StorageSummaryMetricProvider implements MetricProvider {
    private final StorageSummaryService storageSummaryService;

    public StorageSummaryMetricProvider(StorageSummaryService storageSummaryService) {
        this.storageSummaryService = storageSummaryService;
    }

    @Override
    public String getName() {
        return "jfrt_stg_summary";
    }

    @Override
    public String getDescription() {
        return "Artifactory Storage Summary Metrics (cached)";
    }

    @Override
    public ReportType getReportType() {
        return ReportType.DEDUPED;
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(60);
    }

    @Override
    public boolean isAllowedInSaas() {
        return true;
    }

    @Override
    public long getLastUpdatedMillis() {
        StorageSummaryInfo info = getStorageSummary();
        return (info == null) ? 0 : info.getLastUpdated();
    }

    @Override
    public List<Metric> collect() {
        List<Metric> metrics = new ArrayList<>();
        StorageSummaryInfo info = getStorageSummary();
        if (info != null) {
            addGeneralMetrics(metrics, info);
            addRepositoryMetrics(metrics, info);
            addProjectMetrics(metrics, info);
        }
        return metrics;
    }

    private void addGeneralMetrics(List<Metric> metrics, StorageSummaryInfo info) {
        // jfrt_stg_summary_checkpoint_time_total
        addMetricToList(metrics,
                "checkpoint_time", Metric.MetricType.GAUGE,
                "The UNIX timestamp of the last storage summary checkpoint",
                info.getLastUpdated());

        // jfrt_stg_summary_size_bytes
        addMetricToList(metrics,
                "size", Metric.MetricType.BYTES,
                "The total size of all artifacts stored in all repositories",
                info.getTotalSize());

        // jfrt_stg_summary_files_total
        addMetricToList(metrics,
                "files", Metric.MetricType.GAUGE,
                "The total number of files stored in all repositories",
                info.getTotalFiles());

        // jfrt_stg_summary_folders_total
        addMetricToList(metrics,
                "folders", Metric.MetricType.GAUGE,
                "The total number of folders stored in all repositories",
                info.getTotalFolders());

        // jfrt_stg_summary_checkpoint_binaries_total
        addMetricToList(metrics,
                "checkpoint_binaries", Metric.MetricType.GAUGE,
                "The number of all binaries at last checkpoint",
                info.getBinariesInfo().getBinariesCount());

        // jfrt_stg_summary_checkpoint_binaries_size_bytes
        addMetricToList(metrics,
                "checkpoint_binaries_size", Metric.MetricType.BYTES,
                "The size of all binaries at last checkpoint",
                info.getBinariesInfo().getBinariesSize());
    }

    private void addRepositoryMetrics(List<Metric> metrics, StorageSummaryInfo info) {
        Map<RepoTypeAndPackage, RepoStats> repoStatsMap = new HashMap<>();
        for (RepoStorageSummaryInfo repo : info.getRepoStorageSummaries().values()) {
            RepoTypeAndPackage key = new RepoTypeAndPackage(repo.getRepoType().name(), repo.getType());
            RepoStats stats = repoStatsMap.computeIfAbsent(key, k -> new RepoStats());
            stats.filesCount.append(repo.getFilesCount());
            stats.foldersCount.append(repo.getFoldersCount());
            stats.usedSpace.append(repo.getUsedSpace());
        }

        repoStatsMap.forEach((key, stats) -> {
            Map<String, Object> labels = Map.of("type", key.repoType(), "package", key.packageType());

            // jfrt_stg_summary_repo_count_total
            addMetricWithLabelsToList(metrics, labels,
                    "repo_count", Metric.MetricType.GAUGE,
                    "The number of repositories (from storage stats)",
                    stats.usedSpace.getCount());

            // jfrt_stg_summary_repo_size_bytes
            addMetricWithLabelsToList(metrics, labels,
                    "repo_size", Metric.MetricType.BYTES,
                    "The total storage size (in bytes), per repository type",
                    stats.usedSpace.getSum());

            // jfrt_stg_summary_repo_files_total
            addMetricWithLabelsToList(metrics, labels,
                    "repo_files", Metric.MetricType.GAUGE,
                    "The total number of files per repository type",
                    stats.filesCount.getSum());

            // jfrt_stg_summary_repo_folders_total
            addMetricWithLabelsToList(metrics, labels,
                    "repo_folders", Metric.MetricType.GAUGE,
                    "The total number of folders per repository type",
                    stats.foldersCount.getSum());

            // jfrt_stg_summary_repo_max_size_bytes
            addMetricWithLabelsToList(metrics, labels,
                    "repo_max_size", Metric.MetricType.BYTES,
                    "The maximum storage size (in bytes) per repository type",
                    stats.usedSpace.getMax());

            // jfrt_stg_summary_repo_max_files_total
            addMetricWithLabelsToList(metrics, labels,
                    "repo_max_files", Metric.MetricType.GAUGE,
                    "The maximum number of files per repository type",
                    stats.filesCount.getMax());

            // jfrt_stg_summary_repo_max_folders_total
            addMetricWithLabelsToList(metrics, labels,
                    "repo_max_folders", Metric.MetricType.GAUGE,
                    "The maximum number of folders per repository type",
                    stats.foldersCount.getMax());
        });
    }

    private void addProjectMetrics(List<Metric> metrics, StorageSummaryInfo info) {
        NumericValuesSummary projectFilesCount = new NumericValuesSummary();
        info.getProjectsTotalFilesCount().values().forEach(projectFilesCount::append);

        NumericValuesSummary projectFilesSize = new NumericValuesSummary();
        info.getProjectsTotalSizes().values().forEach(projectFilesSize::append);

        // jfrt_stg_summary_project_count_total
        addMetricToList(metrics,
                "project_count", Metric.MetricType.GAUGE,
                "The total number of projects",
                projectFilesCount.getCount());

        // jfrt_stg_summary_project_size_total
        addMetricToList(metrics,
                "project_size", Metric.MetricType.BYTES,
                "The total size of files in all projects",
                projectFilesSize.getSum());

        // jfrt_stg_summary_projects_files_total
        addMetricToList(metrics,
                "project_files", Metric.MetricType.GAUGE,
                "The total number of files in all projects",
                projectFilesCount.getSum());

        // jfrt_stg_summary_repo_max_size_bytes
        addMetricToList(metrics,
                "project_max_size", Metric.MetricType.BYTES,
                "The maximal size of a project (in bytes)",
                projectFilesSize.getMax());

        // jfrt_stg_summary_repo_max_files_total
        addMetricToList(metrics,
                "project_max_files", Metric.MetricType.GAUGE,
                "The maximal number of files in a single project",
                projectFilesCount.getAverage());
    }

    private StorageSummaryInfo getStorageSummary() {
        try {
            return this.storageSummaryService.getLocalCache();
        } catch (CacheUnAvailableException ignored) {
            return null;
        }
    }

    static class RepoStats {
        final NumericValuesSummary filesCount = new NumericValuesSummary();
        final NumericValuesSummary foldersCount = new NumericValuesSummary();
        final NumericValuesSummary usedSpace = new NumericValuesSummary();
    }
}
