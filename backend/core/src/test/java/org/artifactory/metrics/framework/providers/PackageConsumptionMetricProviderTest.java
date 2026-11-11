package org.artifactory.metrics.framework.providers;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.analytics.AnalyticsAddon;
import org.artifactory.addon.analytics.PackageUsageCounter;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.artifactory.descriptor.repo.RepoType.Docker;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.ACTION_DOWNLOAD;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.ACTION_HEAD;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.ACTION_REDIRECT;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.ACTION_TYPE;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.ACTION_UPLOAD;
import static org.artifactory.metrics.framework.packages.PackagesConsumptionMetricsHelper.PACKAGE_TYPE;

class PackageConsumptionMetricProviderTest extends ArtifactoryHomeBoundJUnitTest {

    private PackageConsumptionMetricProvider metricProvider;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private AnalyticsAddon analyticsAddon;
    private PackageUsageCounter packageUsageData;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        Mockito.when(addonsManager.addonByType(AnalyticsAddon.class)).thenReturn(analyticsAddon);
        packageUsageData = new PackageUsageCounter();
        Mockito.when(analyticsAddon.getPackageUsageData()).thenReturn(packageUsageData);
        metricProvider = Mockito.spy(new PackageConsumptionMetricProvider(addonsManager));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testEmptyData() {
        List<Metric> metrics = metricProvider.collect();
        Assertions.assertNotNull(metrics);
        Assertions.assertTrue(metrics.isEmpty());
    }

    @Test
    void testPackageDownload() {
        packageUsageData.getSuccessfulPackagesDownloads().get(Docker).set(2);
        List<Metric> metrics = metricProvider.collect();
        for (Metric metric : metrics) {
            Assertions.assertEquals(2, metric.getValue().intValue());
            Map<String, Object> labels = metric.getLabels();
            Assertions.assertTrue(labels.containsKey(PACKAGE_TYPE));
            Assertions.assertEquals(labels.get(PACKAGE_TYPE), Docker.getType());
            Assertions.assertTrue(labels.containsKey(ACTION_TYPE));
            Assertions.assertEquals(ACTION_DOWNLOAD, labels.get(ACTION_TYPE));
        }
    }

    @Test
    void testPackageUpload() {
        packageUsageData.getSuccessfulPackagesUploads().get(Docker).set(5);
        List<Metric> metrics = metricProvider.collect();
        for (Metric metric : metrics) {
            Assertions.assertEquals(5, metric.getValue().intValue());
            Map<String, Object> labels = metric.getLabels();
            Assertions.assertTrue(labels.containsKey(PACKAGE_TYPE));
            Assertions.assertEquals(labels.get(PACKAGE_TYPE), Docker.getType());
            Assertions.assertTrue(labels.containsKey(ACTION_TYPE));
            Assertions.assertEquals(ACTION_UPLOAD, labels.get(ACTION_TYPE));
        }
    }

    @Test
    void testPackageHeadRequest() {
        packageUsageData.getSuccessfulPackagesHeadRequests().get(Docker).set(1);
        List<Metric> metrics = metricProvider.collect();
        for (Metric metric : metrics) {
            Assertions.assertEquals(1, metric.getValue().intValue());
            Map<String, Object> labels = metric.getLabels();
            Assertions.assertTrue(labels.containsKey(PACKAGE_TYPE));
            Assertions.assertEquals(labels.get(PACKAGE_TYPE), Docker.getType());
            Assertions.assertTrue(labels.containsKey(ACTION_TYPE));
            Assertions.assertEquals(ACTION_HEAD, labels.get(ACTION_TYPE));
        }
    }

    @Test
    void testPackageRedirect() {
        packageUsageData.getRedirectedPackagesDownloads().get(Docker).set(185);
        List<Metric> metrics = metricProvider.collect();
        for (Metric metric : metrics) {
            Assertions.assertEquals(185, metric.getValue().intValue());
            Map<String, Object> labels = metric.getLabels();
            Assertions.assertTrue(labels.containsKey(PACKAGE_TYPE));
            Assertions.assertEquals(labels.get(PACKAGE_TYPE), Docker.getType());
            Assertions.assertTrue(labels.containsKey(ACTION_TYPE));
            Assertions.assertEquals(ACTION_REDIRECT, labels.get(ACTION_TYPE));
        }
    }

    @Test
    void testDescription() {
        Assertions.assertEquals("Packages consumption metrics", metricProvider.getDescription());
    }

    @Test
    void testGetName() {
        Assertions.assertEquals("jfrt_package_event", metricProvider.getName());
    }

    @Test
    void testGetReportType() {
        Assertions.assertEquals(MetricProvider.ReportType.CONTINUOUS, metricProvider.getReportType());
    }

    @Test
    void testLastUpdatedMillis() {
        Assertions.assertEquals(0, metricProvider.getLastUpdatedMillis());
    }

    @Test
    void testIsAllowedInSaas() {
        Assertions.assertTrue(metricProvider.isAllowedInSaas());
    }

    @Test
    void testGetRefreshIntervalMillis() {
        setStringSystemProperty(ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds,
                String.valueOf(TimeUnit.MINUTES.toSeconds(15)));
        long refreshIntervalMillis = metricProvider.getRefreshIntervalMillis();
        Assertions.assertEquals(899000, refreshIntervalMillis);
    }

    @Test
    void testGetRefreshIntervalMillisWithLowNumber() {
        setStringSystemProperty(ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds, String.valueOf(0));
        long refreshIntervalMillis = metricProvider.getRefreshIntervalMillis();
        Assertions.assertEquals(60000, refreshIntervalMillis);
    }
}