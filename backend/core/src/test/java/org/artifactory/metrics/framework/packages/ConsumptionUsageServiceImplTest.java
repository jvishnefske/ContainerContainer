package org.artifactory.metrics.framework.packages;

import org.artifactory.addon.AddonsManager;
import org.artifactory.common.ConstantValues;
import org.artifactory.metrics.framework.MetricsExecutorServiceHelper;
import org.artifactory.storage.StorageSummaryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.metrics.MetricsCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ConsumptionUsageServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    ConsumptionUsageServiceImpl packageUsageService;
    @Mock
    AddonsManager addonsManager;
    @Mock
    StorageSummaryService storageSummaryService;
    @Mock
    MetricsExecutorServiceHelper executorServiceHelper;
    @Mock
    ScheduledExecutorService scheduledExecutorService;
    AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        packageUsageService = Mockito.spy(new ConsumptionUsageServiceImpl());
        packageUsageService.setStorageSummaryService(storageSummaryService);
        packageUsageService.setAddonsManager(addonsManager);
        packageUsageService.setExecutorHelper(executorServiceHelper);
        Mockito.when(executorServiceHelper.createMetricsScheduledExecutorService())
                .thenReturn(scheduledExecutorService);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testServiceEnabled() {
        getBound().setProperty(ConstantValues.metricsEnabled.getPropertyName(), "FALSE");
        getBound().setProperty(ConstantValues.consumptionUsageMetricsEnabled.getPropertyName(), "TRUE");
        getBound().setProperty(ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds.getPropertyName(),
                "3600");
        packageUsageService.onContextCreated();
        Mockito.verify(scheduledExecutorService)
                .scheduleWithFixedDelay(any(MetricsCollector.class), eq(0L), eq(3600L), eq(TimeUnit.SECONDS));
        Mockito.verifyNoMoreInteractions(scheduledExecutorService);
    }

    @Test
    void testServiceDisabled() {
        getBound().setProperty(ConstantValues.metricsEnabled.getPropertyName(), "TRUE");
        getBound().setProperty(ConstantValues.consumptionUsageMetricsEnabled.getPropertyName(), "FALSE");
        getBound().setProperty(ConstantValues.consumptionUsageMetricsCollectorIntervalSeconds.getPropertyName(),
                "3600");
        packageUsageService.onContextCreated();
        Mockito.verifyNoMoreInteractions(scheduledExecutorService);
    }
}