package org.artifactory.sh.service;

import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author annakv
 */
class StorageClientMetricsProviderTest {
    private StorageClientMetricsProvider metricsProvider;
    private HTTPConnectionPool mockPool;

    @BeforeEach
    public void setUp() {
        metricsProvider = new StorageClientMetricsProvider();
        mockPool = Mockito.mock(HTTPConnectionPool.class);
    }

    @Test
    void testGetName() {
        Assertions.assertEquals("jfrt_storage_client_http_connections", metricsProvider.getName());
    }

    @Test
    void testGetDescription() {
        Assertions.assertEquals("Artifactory Storage Client HTTP Connection Metrics Provider",
                metricsProvider.getDescription());
    }

    @Test
    void testGetRefreshIntervalMillis() {
        Assertions.assertEquals(0, metricsProvider.getRefreshIntervalMillis());
    }

    @Test
    void testCollect() {
        metricsProvider.monitorPool(mockPool);
        List<Metric> metrics = metricsProvider.collect();
        Assertions.assertNotNull(metrics);
        verify(mockPool, times(1)).registerMetrics(anyList(), anyMap());
    }

    @Test
    void testGetReportType() {
        Assertions.assertEquals(MetricProvider.ReportType.CONTINUOUS, metricsProvider.getReportType());
    }

    @Test
    void testGetLastUpdatedMillis() {
        Assertions.assertEquals(0L, metricsProvider.getLastUpdatedMillis());
    }

    @Test
    void testIsAllowedInSaas() {
        Assertions.assertFalse(metricsProvider.isAllowedInSaas());
    }

}
