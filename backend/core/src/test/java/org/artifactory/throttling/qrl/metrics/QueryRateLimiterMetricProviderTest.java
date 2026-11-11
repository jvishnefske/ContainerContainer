package org.artifactory.throttling.qrl.metrics;

import org.artifactory.metrics.core.CoreMetricsRegistry;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.qrl.enums.QueryRateLimiterType;
import org.artifactory.throtlling.qrl.model.QueryRateLimiterMetrics;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider.ReportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.HashMap;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
class QueryRateLimiterMetricProviderTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private QueryRateLimiterService qrlService;
    private CoreMetricsRegistry coreMetricsRegistry;
    private QueryRateLimiterMetricProvider qrlMetricsProvider;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = openMocks(this);
        coreMetricsRegistry = new CoreMetricsRegistry();
        qrlMetricsProvider = new QueryRateLimiterMetricProvider(qrlService, coreMetricsRegistry);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testProviderFeatureDisabled() {
        when(qrlService.isFeatureDisabled()).thenReturn(true);
        assertTrue(qrlMetricsProvider.collect().isEmpty());
    }

    @Test
    void testProvider() {
        when(qrlService.isFeatureDisabled()).thenReturn(false);
        assertTrue(qrlMetricsProvider.isAllowedInSaas());
        assertEquals("jfrt_qrl", qrlMetricsProvider.getName());
        assertEquals("Artifactory Query Rate Limiter Metric Provider", qrlMetricsProvider.getDescription());
        assertEquals(SECONDS.toMillis(60), qrlMetricsProvider.getRefreshIntervalMillis());

        var metricsMap = new HashMap<QueryRateLimiterType, QueryRateLimiterMetrics>();
        metricsMap.put(QueryRateLimiterType.DEFAULT, new QueryRateLimiterMetrics());
        metricsMap.put(QueryRateLimiterType.LOW_PRIORITY, new QueryRateLimiterMetrics());
        coreMetricsRegistry.setQueryRateLimiterMetrics(metricsMap);
        List<Metric> metricsList = qrlMetricsProvider.collect();

        assertEquals(10, metricsList.size());
        assertTrue(metricsList.stream().anyMatch(metric -> "charged_query_time".equals(metric.getName())));
        assertEquals(ReportType.CONTINUOUS, qrlMetricsProvider.getReportType());
        assertEquals(0, qrlMetricsProvider.getLastUpdatedMillis());
    }

    @Test
    void testProviderChargedQueryTimeValueAndReset() {
        when(qrlService.isFeatureDisabled()).thenReturn(false);
        QueryRateLimiterMetrics qrlMetrics = new QueryRateLimiterMetrics();
        qrlMetrics.addTotalChargedQueryTime(321);
        var metricsMap = new HashMap<QueryRateLimiterType, QueryRateLimiterMetrics>();
        metricsMap.put(QueryRateLimiterType.DEFAULT, qrlMetrics);
        coreMetricsRegistry.setQueryRateLimiterMetrics(metricsMap);

        List<Metric> firstCollect = qrlMetricsProvider.collect();
        Metric chargedMetric = firstCollect.stream()
                .filter(metric -> "charged_query_time".equals(metric.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(321L, chargedMetric.getValue().longValue());

        List<Metric> secondCollect = qrlMetricsProvider.collect();
        Metric chargedMetricAfterReset = secondCollect.stream()
                .filter(metric -> "charged_query_time".equals(metric.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, chargedMetricAfterReset.getValue().longValue());
    }
}
