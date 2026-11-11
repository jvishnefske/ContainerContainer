package org.artifactory.metrics.framework.providers;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.framework.providers.util.NumericValuesSummary;
import org.artifactory.metrics.framework.providers.util.RepoTypeAndPackage;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.artifactory.metrics.MetricsUtil.addMetricWithLabelsToList;

@Component
public class RepositoryMetricProvider implements MetricProvider {
    private static final Set<RepoDetailsType> MEMBER_TYPES = Set.of(RepoDetailsType.VIRTUAL, RepoDetailsType.FEDERATED);

    private final RepositoryService repositoryService;

    public RepositoryMetricProvider(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public String getName() {
        return "jfrt_repo";
    }

    @Override
    public String getDescription() {
        return "Artifactory Repository Metric Provider";
    }

    @Override
    public List<Metric> collect() {
        Map<RepoTypeAndPackage, Long> countByType = collectCountByType();
        Map<RepoTypeAndPackage, NumericValuesSummary> countMembersByType = collectMemberStats();

        List<Metric> metrics = new ArrayList<>();
        addCountByTypeMetrics(countByType, metrics);
        addMemberStatsMetrics(countMembersByType, metrics);
        return metrics;
    }

    /**
     * Counts repositories grouped by (repoType, packageType) using
     * lightweight {@link BasicRepoConfig} — no deep-copy of full
     * config objects.
     */
    private Map<RepoTypeAndPackage, Long> collectCountByType() {
        Map<RepoTypeAndPackage, Long> countByType = new HashMap<>();
        for (BasicRepoConfig basic : repositoryService.getAllBasicRepoConfigsView()) {
            RepoDetailsType rt = basic.getRepoType();
            RepoType pt = basic.getPackageType();
            if (rt == null || pt == null) {
                continue;
            }
            RepoTypeAndPackage key = new RepoTypeAndPackage(rt.typeName(), pt.name());
            countByType.merge(key, 1L, Long::sum);
        }
        return countByType;
    }

    /**
     * Collects member-count statistics for Virtual and Federated
     * repositories using lightweight {@link BasicRepoConfig} — no
     * mutable config objects leave the cache.
     */
    private Map<RepoTypeAndPackage, NumericValuesSummary> collectMemberStats() {
        Map<RepoTypeAndPackage, NumericValuesSummary> stats = new HashMap<>();
        for (BasicRepoConfig basic : repositoryService.getAllBasicRepoConfigsView()) {
            RepoDetailsType rt = basic.getRepoType();
            RepoType pt = basic.getPackageType();
            if (rt == null || pt == null || !MEMBER_TYPES.contains(rt)) {
                continue;
            }
            RepoTypeAndPackage key = new RepoTypeAndPackage(
                    rt.typeName(), pt.name());
            stats.computeIfAbsent(key, k -> new NumericValuesSummary())
                    .append(basic.getMemberCount());
        }
        return stats;
    }

    private void addCountByTypeMetrics(
            Map<RepoTypeAndPackage, Long> countByType,
            List<Metric> metrics) {
        countByType.forEach((key, value) -> addMetricWithLabelsToList(
                metrics,
                Map.of("type", key.repoType(),
                        "package", key.packageType()),
                "by_type",
                Metric.MetricType.GAUGE,
                "The number of repositories by type and package type",
                value));
    }

    private void addMemberStatsMetrics(
            Map<RepoTypeAndPackage, NumericValuesSummary> countMembersByType,
            List<Metric> metrics) {
        countMembersByType.forEach((key, stats) -> {
            Map<String, Object> labels = Map.of(
                    "type", key.repoType(),
                    "package", key.packageType());
            addMetricWithLabelsToList(
                    metrics, labels,
                    "avg_members",
                    Metric.MetricType.GAUGE,
                    "The average number of members in "
                            + "virtual/federated repositories",
                    stats.getAverage());
            addMetricWithLabelsToList(
                    metrics, labels,
                    "max_members",
                    Metric.MetricType.GAUGE,
                    "The maximal number of members in "
                            + "virtual/federated repositories",
                    stats.getMax());
        });
    }

    @Override
    public long getRefreshIntervalMillis() {
        return TimeUnit.SECONDS.toMillis(60);
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
