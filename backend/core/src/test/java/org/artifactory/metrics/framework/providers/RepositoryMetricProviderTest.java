package org.artifactory.metrics.framework.providers;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.BasicRepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryMetricProviderTest {
    @Test
    void repoMetricsProvider_shouldHaveReasonableDefaults() {
        RepositoryMetricProvider provider = new RepositoryMetricProvider(mock(RepositoryService.class));

        assertEquals("jfrt_repo", provider.getName());
        assertEquals("Artifactory Repository Metric Provider", provider.getDescription());
        assertEquals(MetricProvider.ReportType.CONTINUOUS, provider.getReportType());
        assertEquals(60000, provider.getRefreshIntervalMillis());
        assertEquals(0, provider.getLastUpdatedMillis());
        assertTrue(provider.isAllowedInSaas(), "RepositoryMetricProvider should work in SaaS");
    }

    @Test
    void repoMetricsProvider_shouldCountEmptyRepositoriesCorrectly() {
        RepositoryService config = mock(RepositoryService.class);
        when(config.getAllBasicRepoConfigsView()).thenReturn(List.of());
        RepositoryMetricProvider provider = new RepositoryMetricProvider(config);

        List<Metric> metrics = provider.collect();

        assertEquals(List.of(), metrics);
    }

    @Test
    void repoMetricsProvider_shouldCountRepositoriesCorrectly() {
        // Arrange — all data comes through lightweight BasicRepoConfig
        RepositoryService config = mock(RepositoryService.class);
        when(config.getAllBasicRepoConfigsView()).thenReturn(List.of(
                new BasicRepoConfig("local-generic-1", 0L,
                        RepoDetailsType.LOCAL, RepoType.Generic),
                new BasicRepoConfig("local-maven-1", 0L,
                        RepoDetailsType.LOCAL, RepoType.Maven),
                new BasicRepoConfig("local-maven-2", 0L,
                        RepoDetailsType.LOCAL, RepoType.Maven),
                new BasicRepoConfig("remote-maven-1", 0L,
                        RepoDetailsType.REMOTE, RepoType.Maven),
                new BasicRepoConfig("virtual-maven-1", 0L,
                        RepoDetailsType.VIRTUAL, RepoType.Maven, 2),
                new BasicRepoConfig("federated-helm-1", 0L,
                        RepoDetailsType.FEDERATED, RepoType.Helm, 0),
                new BasicRepoConfig("federated-helm-2", 0L,
                        RepoDetailsType.FEDERATED, RepoType.Helm, 3)
        ));

        RepositoryMetricProvider provider = new RepositoryMetricProvider(config);

        // Act
        List<Metric> metrics = provider.collect();

        // Assert
        Map<String, Number> actual = new HashMap<>();
        appendMetrics(actual, metrics);

        Map<String, Number> expected = Map.of(
                "GAUGE by_type{package=Generic, type=Local}", 1,
                "GAUGE by_type{package=Maven, type=Local}", 2,
                "GAUGE by_type{package=Maven, type=Remote}", 1,
                "GAUGE by_type{package=Maven, type=Virtual}", 1,
                "GAUGE by_type{package=Helm, type=Federated}", 2,
                "GAUGE avg_members{package=Maven, type=Virtual}", 2.0,
                "GAUGE max_members{package=Maven, type=Virtual}", 2,
                "GAUGE avg_members{package=Helm, type=Federated}", 1.5,
                "GAUGE max_members{package=Helm, type=Federated}", 3
        );

        assertEquals(expected.keySet(), actual.keySet(),
                "RepositoryMetricProvider did not return expected metrics");
        for (String key : expected.keySet()) {
            assertEquals(expected.get(key).doubleValue(),
                    actual.get(key).doubleValue(),
                    () -> "The " + key + " metric has wrong value");
        }
    }

    static void appendMetrics(Map<String, Number> target, List<Metric> metrics) {
        for (Metric metric : metrics) {
            if (metric.getChildren() == null || metric.getChildren().isEmpty()) {
                target.put(metricKey(metric), metric.getValue());
            } else {
                appendMetrics(target, metric.getChildren());
            }
        }

    }

    static String metricKey(Metric metric) {
        StringBuilder builder = new StringBuilder(metric.getType().toString());
        builder.append(' ').append(metric.getName());
        if (!metric.getLabels().isEmpty()) {
            builder.append('{');
            boolean addComma = false;
            for (var entry : metric.getLabels().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                if (addComma) {
                    builder.append(", ");
                } else {
                    addComma = true;
                }
                builder.append(entry.getKey())
                        .append('=')
                        .append(entry.getValue());
            }
            builder.append('}');
        }

        return builder.toString();
    }
}