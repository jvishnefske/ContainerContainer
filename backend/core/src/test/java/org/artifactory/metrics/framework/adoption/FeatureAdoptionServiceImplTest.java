package org.artifactory.metrics.framework.adoption;

import com.jfrog.sysconf.SysConfig;
import org.artifactory.api.metrics.FeatureAdoptionAction;
import org.artifactory.api.metrics.FeatureAdoptionFeature;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.metrics.framework.MetricsExecutorServiceHelper;
import org.artifactory.metrics.framework.adoption.coldstorage.ColdStorageLabels;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.TestUtils;
import org.jfrog.metrics.MetricsHelper;
import org.jfrog.metrics.model.TrackingSchema;
import org.jfrog.metrics.providers.FeatureAdoptionProvider;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/**
 * @author Oleksandr Legenkiy
 */
public class FeatureAdoptionServiceImplTest extends ArtifactoryHomeBoundTest {

    private final TrackingSchema dummySchema =
            new TrackingSchema("someFeatureName", "someDomain", "someAction", 1.2, true);

    private FeatureAdoptionServiceImpl service;
    private FeatureAdoptionProvider provider;

    @BeforeMethod
    public void beforeMethod() {
        MetricsExecutorServiceHelper helperMock = mock(MetricsExecutorServiceHelper.class);
        when(helperMock.createMetricsScheduledExecutorService()).thenReturn(mock(ScheduledExecutorService.class));
        service = new FeatureAdoptionServiceImpl(helperMock);
        provider = mock(FeatureAdoptionProvider.class);
        TestUtils.setField(service, "provider", provider);
        service.onContextCreated();
    }

    @AfterMethod
    public void afterMethod() {
        service.destroy();
    }

    @Test
    public void shouldNotStartHelperIfCallHomeDisabled() {
        getBound().setProperty(ConstantValues.versionQueryEnabled, "false"); // means Call Home is disabled
        MetricsHelper metricsHelperMock = mock(MetricsHelper.class);
        TestUtils.setField(service, "metricsHelper", metricsHelperMock);
        service.onContextCreated();
        verifyNoInteractions(metricsHelperMock);
    }

    @Test
    public void shouldNotStartHelperIfFeatureAdoptionDisabled() {
        // Feature Adoption is disabled via a property that is hidden from customer
        System.setProperty("artifactory.platform.adoption.disabled", "true");
        try {
            MetricsHelper metricsHelperMock = mock(MetricsHelper.class);
            TestUtils.setField(service, "metricsHelper", metricsHelperMock);
            service.onContextCreated();
            verifyNoInteractions(metricsHelperMock);
        } finally {
            System.setProperty("artifactory.platform.adoption.disabled", "false"); // rollback the changes
        }
    }

    @Test
    public void shouldNotCallProviderIfFaDisabled() {
        System.setProperty("artifactory.platform.adoption.disabled", "true");
        try {
            FeatureAdoptionProvider providerMock = mock(FeatureAdoptionProvider.class);
            TestUtils.setField(service, "provider", providerMock);
            MetricsHelper metricsHelperMock = mock(MetricsHelper.class);
            TestUtils.setField(service, "metricsHelper", metricsHelperMock);
            service.onContextCreated();

            service.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                    FeatureAdoptionAction.ARCHIVED, true, ColdStorageLabels.RESOURCE_VIA_ARCHIVING_POLICY);
            service.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                    FeatureAdoptionAction.ARCHIVED, false, ColdStorageLabels.RESOURCE_VIA_ARCHIVING_POLICY);

            service.trackIncrease(dummySchema);
            service.trackDecrease(dummySchema);
            service.trackSet(dummySchema);
            service.trackUnset(dummySchema);
            service.trackAverage(dummySchema);

            verifyNoInteractions(metricsHelperMock);
            verifyNoInteractions(providerMock);
        } finally {
            System.setProperty("artifactory.platform.adoption.disabled", "false"); // rollback the changes
        }
    }

    @Test
    public void shouldStartHelperProperly() {
        SysConfig sysConfig = mock(SysConfig.class);
        when(sysConfig.getLong(FeatureAdoptionServiceImpl.ARTIFACTORY_FA_METRICS_INTERVAL,
                FeatureAdoptionServiceImpl.DEFAULT_FA_METRIC_INTERVAL_SECONDS)).thenReturn(123L);

        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

        MetricsHelper metricsHelperMock = mock(MetricsHelper.class);
        TestUtils.setField(service, "metricsHelper", metricsHelperMock);

        service.onContextCreated();
        verify(metricsHelperMock)
                .startAdoption(eq(LoggerFactory.getLogger("USAGE_METRICS")), eq(123L),
                        any(ScheduledExecutorService.class));

        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }

    @Test
    public void shouldUseCorrectParamsOnTrackAdoption() throws Exception {
        service.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                FeatureAdoptionAction.ARCHIVED, true, ColdStorageLabels.RESOURCE_VIA_ARCHIVING_POLICY);
        Mockito.verify(provider).trackIncrease(new TrackingSchema(FeatureAdoptionFeature.COLD_STORAGE.featureName(),
                FeatureAdoptionServiceImpl.FEATURE_ADOPTION_DOMAIN,
                FeatureAdoptionAction.ARCHIVED.action(), 1, true,
                ColdStorageLabels.RESOURCE_VIA_ARCHIVING_POLICY.labels()));
        service.trackAdoption(FeatureAdoptionFeature.COLD_STORAGE,
                FeatureAdoptionAction.ARCHIVED, false, ColdStorageLabels.RESOURCE_VIA_ARCHIVING_POLICY);
        Mockito.verify(provider).trackIncrease(new TrackingSchema(FeatureAdoptionFeature.COLD_STORAGE.featureName(),
                FeatureAdoptionServiceImpl.FEATURE_ADOPTION_DOMAIN,
                FeatureAdoptionAction.ARCHIVED.action(), 1, false,
                ColdStorageLabels.RESOURCE_VIA_ARCHIVING_POLICY.labels()));
    }

    @Test
    public void shouldCallProviderOnTrackAverage() throws Exception {
        service.trackAverage(dummySchema);
        Mockito.verify(provider).trackAverage(dummySchema);
    }

    @Test
    public void shouldHandleExceptionOnTrackAverage() throws Exception {
        doThrow(new Exception()).when(provider).trackAverage(dummySchema);
        service.trackAverage(dummySchema);
    }

    @Test
    public void shouldCallProviderOnTrackSet() throws Exception {
        service.trackSet(dummySchema);
        Mockito.verify(provider).trackSet(dummySchema);
    }

    @Test
    public void shouldHandleExceptionOnTrackSet() throws Exception {
        doThrow(new Exception()).when(provider).trackSet(dummySchema);
        service.trackSet(dummySchema);
    }

    @Test
    public void shouldCallProviderOnTrackUnset() throws Exception {
        service.trackUnset(dummySchema);
        Mockito.verify(provider).trackUnset(dummySchema);
    }

    @Test
    public void shouldHandleExceptionOnTrackUnset() throws Exception {
        doThrow(new Exception()).when(provider).trackUnset(dummySchema);
        service.trackUnset(dummySchema);
    }

    @Test
    public void shouldCallProviderOnTrackIncrease() throws Exception {
        service.trackIncrease(dummySchema);
        Mockito.verify(provider).trackIncrease(dummySchema);
    }

    @Test
    public void shouldHandleExceptionOnTrackIncrease() throws Exception {
        doThrow(new Exception()).when(provider).trackIncrease(dummySchema);
        service.trackIncrease(dummySchema);
    }

    @Test
    public void shouldCallProviderOnTrackDecrease() throws Exception {
        service.trackDecrease(dummySchema);
        Mockito.verify(provider).trackDecrease(dummySchema);
    }

    @Test
    public void shouldHandleExceptionOnTrackDecrease() throws Exception {
        doThrow(new Exception()).when(provider).trackDecrease(dummySchema);
        service.trackDecrease(dummySchema);
    }

    @Test
    public void shouldShutdownExecutorService() {
        ScheduledExecutorService scheduledExecutorServiceMock = mock(ScheduledExecutorService.class);
        TestUtils.setField(service, "scheduledExecutorService", scheduledExecutorServiceMock);
        service.destroy();
        verify(scheduledExecutorServiceMock).shutdown();
    }

    @Test
    public void shouldUseDefaultIntervalOnNumberFormatException() {
        SysConfig sysConfig = mock(SysConfig.class);
        when(sysConfig.getLong(FeatureAdoptionServiceImpl.ARTIFACTORY_FA_METRICS_INTERVAL,
                FeatureAdoptionServiceImpl.DEFAULT_FA_METRIC_INTERVAL_SECONDS))
                .thenThrow(new NumberFormatException());

        SysConfig sysConfigBackUp = TestUtils.getField(ArtifactoryHome.get(), "sysConfig", SysConfig.class);
        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfig);

        MetricsHelper metricsHelperMock = mock(MetricsHelper.class);
        TestUtils.setField(service, "metricsHelper", metricsHelperMock);

        service.onContextCreated();
        verify(metricsHelperMock).startAdoption(eq(LoggerFactory.getLogger("USAGE_METRICS")),
                eq(FeatureAdoptionServiceImpl.DEFAULT_FA_METRIC_INTERVAL_SECONDS),
                any(ScheduledExecutorService.class));

        TestUtils.setField(ArtifactoryHome.get(), "sysConfig", sysConfigBackUp);
    }
}
