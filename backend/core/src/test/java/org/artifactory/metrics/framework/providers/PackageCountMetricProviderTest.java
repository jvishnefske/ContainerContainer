package org.artifactory.metrics.framework.providers;

import org.artifactory.api.repo.storage.RepoStorageSummaryInfo;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.storage.CacheUnAvailableException;
import org.artifactory.storage.StorageSummaryInfo;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.jfrog.storage.binstore.BinariesInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class PackageCountMetricProviderTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private StorageSummaryService storageSummaryService;
    private PackageCountMetricProvider provider;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        provider = Mockito.spy(new PackageCountMetricProvider(storageSummaryService));
        getBound();
    }

    @AfterEach
    void tearDown() throws Exception {
        setLongSystemProperty(ConstantValues.consumptionUsageMetricsPackagesCountIntervalMinutes,
                              TimeUnit.HOURS.toMinutes(6));
        unbindArtifactoryHome();
        autoCloseable.close();
    }

    @Test
    void testGetName() {
        Assert.assertEquals(provider.getName(), "jfrt_package_count");
    }

    @Test
    void testGetDescription() {
        Assert.assertEquals(provider.getDescription(), "Packages count consumption metrics");
    }

    @Test
    void testGetReportType() {
        Assert.assertEquals(provider.getReportType(), MetricProvider.ReportType.CONTINUOUS);
    }

    @Test
    void testGetLastUpdatedMillisWhenStorageSummaryWasNotCalculated() {
        Assert.assertEquals(provider.getLastUpdatedMillis(), 0);
    }

    @Test
    void testGetLastUpdatedMillis() {
        Mockito.when(storageSummaryService.getLocalCache()).thenReturn(createStorageSummary(RepoType.Generic));
        Assert.assertTrue(provider.getLastUpdatedMillis() > 0);
    }

    @Test
    void testIsAllowedInSaas() {
        Assert.assertTrue(provider.isAllowedInSaas());
    }

    @Test
    void testCollectWhenStorageSummaryWasNotCalculated() {
        Mockito.doThrow(new CacheUnAvailableException()).when(storageSummaryService).getLocalCache();
        List<Metric> metrics = provider.collect();
        Assertions.assertTrue(metrics.isEmpty());
    }

    @Test
    void testCollectWithGenericRepo() {
        Mockito.when(storageSummaryService.getLocalCache()).thenReturn(createStorageSummary(RepoType.Generic));
        List<Metric> metrics = provider.collect();
        Assert.assertEquals(metrics.size(), 1);
        Metric metric = metrics.get(0);
        Assert.assertEquals(metric.getLabels().get("package_type"), "generic");
        Assert.assertEquals(metric.getType(), Metric.MetricType.GAUGE);
        Assert.assertEquals(metric.getValue(), 2L);
    }

    @Test
    void testCollectTwiceInLessThan6Hours() {
        Mockito.when(storageSummaryService.getLocalCache()).thenReturn(createStorageSummary(RepoType.Generic));
        List<Metric> metrics = provider.collect();
        Assert.assertEquals(metrics.size(), 1);
        metrics = provider.collect();
        Assert.assertEquals(metrics.size(), 0);
    }

    @Test
    void testCollectTwiceWhenTimeFramePassed() {
        Mockito.when(storageSummaryService.getLocalCache()).thenReturn(createStorageSummary(RepoType.Generic));
        List<Metric> metrics = provider.collect();
        Assert.assertEquals(metrics.size(), 1);
        setLongSystemProperty(ConstantValues.consumptionUsageMetricsPackagesCountIntervalMinutes, 0);
        metrics = provider.collect();
        Assert.assertEquals(metrics.size(), 1);
    }

    @Test
    void testCollectWithDockerRepo() {
        Mockito.when(storageSummaryService.getLocalCache()).thenReturn(createStorageSummary(RepoType.Docker));
        List<Metric> metrics = provider.collect();
        Assert.assertEquals(metrics.size(), 0);
    }

    private StorageSummaryInfo createStorageSummary(RepoType repoType) {
        return new StorageSummaryInfo(Map.of("repoKey", new RepoStorageSummaryInfo("repoKey",
                RepoStorageSummaryInfo.RepositoryType.LOCAL, 1, 2, 3, repoType.getDisplayName(), "projectKey")),
                new BinariesInfo(), Map.of(), Map.of());
    }
}