package org.artifactory.metrics.framework.providers;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.jfrog.common.ClockUtils;
import org.jfrog.metrics.MetricsCollector;
import org.jfrog.metrics.MetricsHelper;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertTrue;

@Slf4j
class StorageSummaryMetricProviderTest {
    @Test
    void storageSummaryMetricProvider_shouldHaveReasonableDefaults() {
        StorageSummaryMetricProvider provider = new StorageSummaryMetricProvider(mock(StorageSummaryService.class));
        assertEquals("jfrt_stg_summary", provider.getName());
        assertEquals("Artifactory Storage Summary Metrics (cached)", provider.getDescription());
        assertEquals(MetricProvider.ReportType.DEDUPED, provider.getReportType());
        assertEquals(60_000, provider.getRefreshIntervalMillis());
        assertEquals(0, provider.getLastUpdatedMillis());
        assertTrue(provider.isAllowedInSaas(), "StorageSummaryMetricProvider should work in SaaS");
    }

    @Test
    void storageSummaryMetricProvider_shouldReturnZeroUpdateTime_whenNoDataCached() {
        // Arrange
        StorageSummaryService serviceMock = mock(StorageSummaryService.class);
        when(serviceMock.getLocalCache()).thenThrow(CacheUnAvailableException.class);
        StorageSummaryMetricProvider provider = new StorageSummaryMetricProvider(serviceMock);
        // Act
        long actual = provider.getLastUpdatedMillis();
        // Assert
        assertEquals(0, actual, "Provider should return zero timestamp when there is no data cached");
    }

    @Test
    void storageSummaryMetricProvider_collectShouldReturnEmptyList_whenNoDataCached() {
        // Arrange
        StorageSummaryService serviceMock = mock(StorageSummaryService.class);
        when(serviceMock.getLocalCache()).thenThrow(CacheUnAvailableException.class);
        StorageSummaryMetricProvider provider = new StorageSummaryMetricProvider(serviceMock);
        // Act
        List<Metric> actual = provider.collect();
        // Assert
        assertTrue(actual.isEmpty(), "Provider should return empty metrics list when there is no data cached");
    }

    @Test
    void storageSummaryMetricProvider_collector() {
        // Arrange
        StorageSummaryInfo info;
        try (MockedStatic<ClockUtils> clockUtilsMock = mockStatic(ClockUtils.class)) {
            // We must mock the ClockUtils.epochMillis() to check the jfrt_stg_summary_updated_total metric value
            clockUtilsMock.when(ClockUtils::epochMillis).thenReturn(1699676638000L);
            info = new StorageSummaryInfo(
                    Map.of(
                            "test-npm-remote-cache", new RepoStorageSummaryInfo(
                                    "test-npm-remote-cache", RepoStorageSummaryInfo.RepositoryType.CACHE,
                                    86, 185, 5_389_959,
                                    "npm", "default"
                            ),
                            "test-npm", new RepoStorageSummaryInfo(
                                    "test-npm", RepoStorageSummaryInfo.RepositoryType.VIRTUAL,
                                    1, 2, 2,
                                    "npm", "default"
                            ),
                            "test-npm-local", new RepoStorageSummaryInfo(
                                    "test-npm-local", RepoStorageSummaryInfo.RepositoryType.LOCAL,
                                    6, 4, 5341,
                                    "npm", "default"
                            )
                    ), // Map<String, RepoStorageSummaryInfo > repoStorageSummaries,
                    new BinariesInfo(193, 5_395_302),
                    Map.of("default", 5_395_302L),
                    Map.of("default", 193L)
            );
        }

        StorageSummaryService storageSummaryServiceMock = mock(StorageSummaryService.class);
        when(storageSummaryServiceMock.getLocalCache()).thenReturn(info);

        StorageSummaryMetricProvider provider = new StorageSummaryMetricProvider(storageSummaryServiceMock);

        List<MetricProvider> providers = new ArrayList<>();
        providers.add(provider);
        MetricsHelper helper = new MetricsHelper(providers, List.of());

        MetricsCollector collector = new MetricsCollector(helper, log, log, Executors.newSingleThreadExecutor());

        String actual;
        Instant fixedTime = Instant.ofEpochMilli(1699676749000L);
        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class)) {
            // We must mock the Instant.now() to be able comparing the text output of OpenMetrics
            instantMock.when(Instant::now).thenReturn(fixedTime);

            // Act
            actual = collector.openMetricsCollect();
        }

        // Assert
        assertEquals(
                // language=sh
                """
                # TYPE jfrt_stg_summary_checkpoint_binaries_total gauge
                # HELP jfrt_stg_summary_checkpoint_binaries_total The number of all binaries at last checkpoint
                jfrt_stg_summary_checkpoint_binaries_total 193 1699676749000
                # TYPE jfrt_stg_summary_checkpoint_binaries_size_bytes gauge
                # HELP jfrt_stg_summary_checkpoint_binaries_size_bytes The size of all binaries at last checkpoint
                jfrt_stg_summary_checkpoint_binaries_size_bytes 5395302 1699676749000
                # TYPE jfrt_stg_summary_checkpoint_time_total gauge
                # HELP jfrt_stg_summary_checkpoint_time_total The UNIX timestamp of the last storage summary checkpoint
                jfrt_stg_summary_checkpoint_time_total 1699676638000 1699676749000
                # TYPE jfrt_stg_summary_files_total gauge
                # HELP jfrt_stg_summary_files_total The total number of files stored in all repositories
                jfrt_stg_summary_files_total 191 1699676749000
                # TYPE jfrt_stg_summary_folders_total gauge
                # HELP jfrt_stg_summary_folders_total The total number of folders stored in all repositories
                jfrt_stg_summary_folders_total 93 1699676749000
                # TYPE jfrt_stg_summary_project_count_total gauge
                # HELP jfrt_stg_summary_project_count_total The total number of projects
                jfrt_stg_summary_project_count_total 1 1699676749000
                # TYPE jfrt_stg_summary_project_files_total gauge
                # HELP jfrt_stg_summary_project_files_total The total number of files in all projects
                jfrt_stg_summary_project_files_total 193 1699676749000
                # TYPE jfrt_stg_summary_project_max_files_total gauge
                # HELP jfrt_stg_summary_project_max_files_total The maximal number of files in a single project
                jfrt_stg_summary_project_max_files_total 193.0 1699676749000
                # TYPE jfrt_stg_summary_project_max_size_bytes gauge
                # HELP jfrt_stg_summary_project_max_size_bytes The maximal size of a project (in bytes)
                jfrt_stg_summary_project_max_size_bytes 5395302 1699676749000
                # TYPE jfrt_stg_summary_project_size_bytes gauge
                # HELP jfrt_stg_summary_project_size_bytes The total size of files in all projects
                jfrt_stg_summary_project_size_bytes 5395302 1699676749000
                # TYPE jfrt_stg_summary_repo_count_total gauge
                # HELP jfrt_stg_summary_repo_count_total The number of repositories (from storage stats)
                jfrt_stg_summary_repo_count_total{package="npm",type="CACHE"} 1 1699676749000
                jfrt_stg_summary_repo_count_total{package="npm",type="VIRTUAL"} 1 1699676749000
                jfrt_stg_summary_repo_count_total{package="npm",type="LOCAL"} 1 1699676749000
                # TYPE jfrt_stg_summary_repo_files_total gauge
                # HELP jfrt_stg_summary_repo_files_total The total number of files per repository type
                jfrt_stg_summary_repo_files_total{package="npm",type="CACHE"} 185 1699676749000
                jfrt_stg_summary_repo_files_total{package="npm",type="VIRTUAL"} 2 1699676749000
                jfrt_stg_summary_repo_files_total{package="npm",type="LOCAL"} 4 1699676749000
                # TYPE jfrt_stg_summary_repo_folders_total gauge
                # HELP jfrt_stg_summary_repo_folders_total The total number of folders per repository type
                jfrt_stg_summary_repo_folders_total{package="npm",type="CACHE"} 86 1699676749000
                jfrt_stg_summary_repo_folders_total{package="npm",type="VIRTUAL"} 1 1699676749000
                jfrt_stg_summary_repo_folders_total{package="npm",type="LOCAL"} 6 1699676749000
                # TYPE jfrt_stg_summary_repo_max_files_total gauge
                # HELP jfrt_stg_summary_repo_max_files_total The maximum number of files per repository type
                jfrt_stg_summary_repo_max_files_total{package="npm",type="CACHE"} 185 1699676749000
                jfrt_stg_summary_repo_max_files_total{package="npm",type="VIRTUAL"} 2 1699676749000
                jfrt_stg_summary_repo_max_files_total{package="npm",type="LOCAL"} 4 1699676749000
                # TYPE jfrt_stg_summary_repo_max_folders_total gauge
                # HELP jfrt_stg_summary_repo_max_folders_total The maximum number of folders per repository type
                jfrt_stg_summary_repo_max_folders_total{package="npm",type="CACHE"} 86 1699676749000
                jfrt_stg_summary_repo_max_folders_total{package="npm",type="VIRTUAL"} 1 1699676749000
                jfrt_stg_summary_repo_max_folders_total{package="npm",type="LOCAL"} 6 1699676749000
                # TYPE jfrt_stg_summary_repo_max_size_bytes gauge
                # HELP jfrt_stg_summary_repo_max_size_bytes The maximum storage size (in bytes) per repository type
                jfrt_stg_summary_repo_max_size_bytes{package="npm",type="CACHE"} 5389959 1699676749000
                jfrt_stg_summary_repo_max_size_bytes{package="npm",type="VIRTUAL"} 2 1699676749000
                jfrt_stg_summary_repo_max_size_bytes{package="npm",type="LOCAL"} 5341 1699676749000
                # TYPE jfrt_stg_summary_repo_size_bytes gauge
                # HELP jfrt_stg_summary_repo_size_bytes The total storage size (in bytes), per repository type
                jfrt_stg_summary_repo_size_bytes{package="npm",type="CACHE"} 5389959 1699676749000
                jfrt_stg_summary_repo_size_bytes{package="npm",type="VIRTUAL"} 2 1699676749000
                jfrt_stg_summary_repo_size_bytes{package="npm",type="LOCAL"} 5341 1699676749000
                # TYPE jfrt_stg_summary_size_bytes gauge
                # HELP jfrt_stg_summary_size_bytes The total size of all artifacts stored in all repositories
                jfrt_stg_summary_size_bytes 5395302 1699676749000
                """, actual);
    }
}