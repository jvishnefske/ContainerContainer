package org.artifactory.metrics.framework.providers;

import org.artifactory.shadow.ShadowRequestService;
import org.jfrog.metrics.model.MetricProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class ShadowMetricProviderTest {

    ShadowMetricProvider shadowMetricProvider;

    @Mock
    ShadowRequestService shadowRequestService;

    AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = openMocks(this);
        shadowMetricProvider = new ShadowMetricProvider(shadowRequestService);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void getName() {
        assertEquals("jfrt_shadow", shadowMetricProvider.getName());
    }

    @Test
    void getDescription() {
        assertEquals("Artifactory Shadow testing Metric Provider", shadowMetricProvider.getDescription());
    }

    @Test
    void collect() {
        when(shadowRequestService.collectMetrics()).thenReturn(List.of());
        assertEquals(List.of(), shadowMetricProvider.collect());
    }

    @Test
    void getRefreshIntervalMillis() {
        assertEquals(60000, shadowMetricProvider.getRefreshIntervalMillis());
    }

    @Test
    void getReportType() {
        assertEquals(MetricProvider.ReportType.CONTINUOUS, shadowMetricProvider.getReportType());
    }

    @Test
    void getLastUpdatedMillis() {
        assertEquals(0, shadowMetricProvider.getLastUpdatedMillis());
    }

    @Test
    void isAllowedInSaas() {
        assertTrue(shadowMetricProvider.isAllowedInSaas());
    }
}