package org.artifactory.metrics.framework;

import com.google.common.collect.Lists;
import com.jfrog.sysconf.SysConfig;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.TestUtils;
import org.jfrog.metrics.model.MetricProvider;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class MetricsServiceImplTest extends ArtifactoryHomeBoundTest {
    @Mock
    private AccessService accessService;

    MetricsServiceImpl metricsService;
    private SysConfig originalSysConfig;
    private SysConfig sysConfigMock;

    @BeforeClass
    public void setup() {
        List<MetricProvider> metricProviderList = new ArrayList<>();
        metricsService = new MetricsServiceImpl(accessService, new MetricsExecutorServiceHelper(),
                metricProviderList, List.of());
    }

    @BeforeMethod
    void beforeEach() {
        this.originalSysConfig = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);

        this.sysConfigMock = mock(SysConfig.class);
        // when(this.sysConfigMock.getBoolean("shared.metrics.filebeat.enabled", false))
        //         .thenReturn(false);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", this.sysConfigMock);
    }

    @AfterMethod
    void afterEach() {
        getBound().setProperty(ConstantValues.metricsEnabled.getPropertyName(), null);
        if (this.originalSysConfig != null) {
            TestUtils.setField(ArtifactoryHome.get(), "sysConfig", this.originalSysConfig);
            this.originalSysConfig = null;
        }
    }

    @Test
    public void testMetricsService() {
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

        when(sysConfig.get("artifactory.database.type", "derby")).thenReturn("derby");
        getBound().setProperty(ConstantValues.metricsEnabled.getPropertyName(), "FALSE");
        when(sysConfig.getBoolean("artifactory.metrics.enabled", false)).thenReturn(false);
        metricsService.onContextCreated();
        assertFalse(metricsService.isMetricServiceRunning());

        getBound().setProperty(ConstantValues.metricsEnabled.getPropertyName(), "TRUE");
        when(sysConfig.getBoolean("artifactory.metrics.enabled", false)).thenReturn(true);
        when(sysConfig.get("artifactory.metrics.exclude", "")).thenReturn("");
        metricsService.onContextCreated();
        assertTrue(metricsService.isMetricServiceRunning());
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    @Test
    public void testMetricsServiceWithCollisionProviders() {

        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);
        when(sysConfig.getBoolean("artifactory.metrics.enabled", false)).thenReturn(true);
        when(sysConfig.get("artifactory.metrics.exclude", "")).thenReturn("");

        //check 1: provider with different name - valid
        MetricsServiceImpl metricsService = createMetricsService("name1", "name2");
        metricsService.onContextCreated();
        verify(metricsService, never()).printValidationErrorMessage(anyString(), any());
        //check 2: provider with same name - not valid
        MetricsServiceImpl metricsService2 = createMetricsService("same_name", "same_name");
        metricsService2.onContextCreated();
        verify(metricsService2, atLeastOnce()).printValidationErrorMessage(anyString(), any());

        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    private MetricsServiceImpl createMetricsService(String name1, String name2) {
        List<MetricProvider> providers = Lists.newArrayList();
        MetricProvider provider = mock(MetricProvider.class);
        MetricProvider provider2 = mock(MetricProvider.class);
        when(provider.getName()).thenReturn(name1);
        when(provider2.getName()).thenReturn(name2);
        providers.add(provider);
        providers.add(provider2);
        return spy(new MetricsServiceImpl(accessService, new MetricsExecutorServiceHelper(), providers, List.of()));
    }

    @Test
    public void testNoESUrlForFilebeat() {
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);
        String esUrl = metricsService.getESUrlFromSysConfig(sysConfig);
        assertNull(esUrl);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    @Test
    public void testESUrlForFilebeatWithSlash() {
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

        when(sysConfig.get("shared.metrics.filebeat.elasticsearch.url")).thenReturn(
                Optional.of("http://localhost:8082/elasticsearch/"));
        String esUrl = metricsService.getESUrlFromSysConfig(sysConfig);
        assertNotNull(esUrl);
        assertEquals(esUrl, "http://localhost:8082/elasticsearch/");
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    @Test
    public void testESUrlForFilebeatWithoutSlash() {
        SysConfig sysConfig = mock(SysConfig.class);
        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

        when(sysConfig.get("shared.metrics.filebeat.elasticsearch.url")).thenReturn(
                Optional.of("http://localhost:8082/elasticsearch"));
        String esUrl = metricsService.getESUrlFromSysConfig(sysConfig);
        assertNotNull(esUrl);
        assertEquals(esUrl, "http://localhost:8082/elasticsearch/");
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    @Test
    void getOpenMetrics_shouldReturnEmptyString_whenMetricsDisabled() {
        getBound().setProperty(ConstantValues.metricsEnabled.getPropertyName(), "FALSE");
        when(this.sysConfigMock.getBoolean("artifactory.metrics.enabled", false)).thenReturn(false);

        MetricsExecutorServiceHelper helperMock = mock(MetricsExecutorServiceHelper.class, Mockito.RETURNS_DEEP_STUBS);
        MetricsServiceImpl service = new MetricsServiceImpl(accessService, helperMock, List.of(), List.of());
        service.onContextCreated();

        String actual = service.getOpenMetrics(false);
        assertTrue(actual.isEmpty(), "getOpenMetrics() should be empty when metrics are disabled");
    }

    @Test
    void getOpenMetrics_shouldHaveValues_whenMetricsEnabled() {
        when(this.sysConfigMock.getBoolean("artifactory.metrics.enabled", false)).thenReturn(true);

        MetricsExecutorServiceHelper helperMock = mock(MetricsExecutorServiceHelper.class, Mockito.RETURNS_DEEP_STUBS);
        MetricsServiceImpl service = new MetricsServiceImpl(accessService, helperMock, List.of(), List.of());
        service.onContextCreated();

        String actual = service.getOpenMetrics(false);
        assertTrue(actual.contains("# TYPE app_disk_used_bytes gauge"),
                "When metrics are enabled, OpenMetrics should contain standard metrics (from JFrog)");
    }

    @Test
    void getOpenMetrics_shouldIncludeAdditionalMetrics_whenProvided() {
        when(this.sysConfigMock.getBoolean("artifactory.metrics.enabled", false)).thenReturn(true);

        OpenMetricsWriter fixedWriter = (writer, onlyApplicationMetrics) -> {
            writer.write("# TYPE jfrog_test_value gauge");
            writer.write(System.lineSeparator());
            writer.write("jfrog_test_value 1.0");
            writer.write(System.lineSeparator());
        };

        MetricsExecutorServiceHelper helperMock = mock(MetricsExecutorServiceHelper.class, Mockito.RETURNS_DEEP_STUBS);
        MetricsServiceImpl service = new MetricsServiceImpl(accessService, helperMock, List.of(), List.of(fixedWriter));
        service.onContextCreated();

        String actual = service.getOpenMetrics(false);
        assertTrue(actual.contains("# TYPE jfrog_test_value gauge"),
                "The metric definition line is missing.  Spring metrics will be missing.");
        assertTrue(actual.contains("jfrog_test_value 1.0"),
                "The metric value line is missing.  Spring metrics will be missing.");
    }

    @Test
    void getOpenMetrics_exceptionInCustomWriter_shouldBeSwallowed() {
        when(this.sysConfigMock.getBoolean("artifactory.metrics.enabled", false)).thenReturn(true);

        OpenMetricsWriter badWriter = (writer, onlyApplicationMetrics) -> {
            throw new IOException("Simulated error");
        };

        MetricsExecutorServiceHelper helperMock = mock(MetricsExecutorServiceHelper.class, Mockito.RETURNS_DEEP_STUBS);
        MetricsServiceImpl service = new MetricsServiceImpl(accessService, helperMock, List.of(), List.of(badWriter));
        service.onContextCreated();

        String actual = service.getOpenMetrics(false);
        assertTrue(actual.contains("# TYPE app_disk_used_bytes gauge"),
                "OpenMetrics should be there, even when custom writer throws exception");
    }
}