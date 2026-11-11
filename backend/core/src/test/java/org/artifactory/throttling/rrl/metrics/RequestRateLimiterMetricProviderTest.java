package org.artifactory.throttling.rrl.metrics;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider.ReportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Igor Gumush
 */
class RequestRateLimiterMetricProviderTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private IRequestRateLimiterService rlService;
    @InjectMocks
    private RequestRateLimiterMetricProvider rlMetricsProvider;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testProviderFeatureDisabled() {
        when(rlService.isFeatureDisabled()).thenReturn(true);
        assertTrue(rlMetricsProvider.collect().isEmpty());
    }

    @Test
    void testProvider() {
        when(rlService.isFeatureDisabled()).thenReturn(false);
        assertTrue(rlMetricsProvider.isAllowedInSaas());
        assertEquals("jfrt_rrl", rlMetricsProvider.getName());
        assertEquals("Artifactory Request Rate Limiter Metric Provider", rlMetricsProvider.getDescription());
        assertEquals(SECONDS.toMillis(60), rlMetricsProvider.getRefreshIntervalMillis());

        var metricsMap = new HashMap<String, Map<String, Long>>();
        metricsMap.put(
                "docker.io", new HashMap<>() {{put("Dropped", 30L);}});
        metricsMap.put(
                "quay.io", new HashMap<>() {{put("Dropped", 50L);}});
        when(rlService.getMetrics()).thenReturn(metricsMap);
        List<Metric> metricsList = rlMetricsProvider.collect();

        assertEquals(2, metricsList.size());
        assertEquals(ReportType.CONTINUOUS, rlMetricsProvider.getReportType());
        assertEquals(0, rlMetricsProvider.getLastUpdatedMillis());
    }
}
