package org.artifactory.metrics.framework.providers;

import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.http.HttpConnectionPoolMonitorService;
import org.artifactory.repo.http.mbean.HTTPConnectionPool;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.metrics.model.Metric;
import org.jfrog.metrics.model.MetricProvider;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class HttpConnectionsMetricProviderTest extends ArtifactoryHomeBoundTest {

    private HttpConnectionsMetricProvider httpConnectionsMetricProvider;
    private HttpConnectionPoolMonitorService httpConnectionPoolMonitorService;

    @BeforeMethod
    public void setUp() {
        httpConnectionPoolMonitorService = mock(HttpConnectionPoolMonitorService.class);
        httpConnectionsMetricProvider = new HttpConnectionsMetricProvider(httpConnectionPoolMonitorService);
    }

    @Test
    public void testGetName() {
        assertEquals(httpConnectionsMetricProvider.getName(), "jfrt_http_connections");
    }

    @Test
    public void testGetDescription() {
        assertEquals(httpConnectionsMetricProvider.getDescription(), "Artifactory HTTP Connection Metrics Provider");
    }

    @Test
    public void testGetRefreshIntervalMillis() {
        assertEquals(httpConnectionsMetricProvider.getRefreshIntervalMillis(), 0L);
    }

    private ConnPoolControl<?> mockConnPoolControl(int available, int pending, int leased, int max) {
        ConnPoolControl<?> connPoolControl = mock(ConnPoolControl.class);
        PoolStats poolStats = mock(PoolStats.class);
        when(connPoolControl.getTotalStats()).thenReturn(poolStats);
        when(poolStats.getAvailable()).thenReturn(available);
        when(poolStats.getPending()).thenReturn(pending);
        when(poolStats.getLeased()).thenReturn(leased);
        when(poolStats.getMax()).thenReturn(max);
        return connPoolControl;
    }

    @Test
    public void testCollect() {
        HTTPConnectionPool pool = new HTTPConnectionPool(mockConnPoolControl(10, 5, 2, 100));
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", new WeakReference<>(pool));
        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);


        List<String> allowedMetrics = new ArrayList<>();
        allowedMetrics.add("available");
        allowedMetrics.add("max");
        allowedMetrics.add("leased");
        allowedMetrics.add("pending");
        List<Metric> metricList = httpConnectionsMetricProvider.collect();

        assertEquals(metricList.size(), 4);
        assertTrue(metricList.stream().allMatch(metric -> allowedMetrics.contains(metric.getName())));
        Metric availableMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("available"))
                .findFirst()
                .get();
        assertEquals(availableMetric.getValue().doubleValue(), 10);
        assertEquals(availableMetric.getType(), Metric.MetricType.GAUGE);

        Metric pendingMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("pending"))
                .findFirst()
                .get();
        assertEquals(pendingMetric.getValue().doubleValue(), 5);
        assertEquals(pendingMetric.getType(), Metric.MetricType.GAUGE);

        Metric leasedMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("leased"))
                .findFirst()
                .get();
        assertEquals(leasedMetric.getValue().doubleValue(), 2);
        assertEquals(leasedMetric.getType(), Metric.MetricType.GAUGE);

        Metric maxMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("max"))
                .findFirst()
                .get();
        assertEquals(maxMetric.getValue().doubleValue(), 100);
        assertEquals(maxMetric.getType(), Metric.MetricType.GAUGE);
    }

    @Test
    public void testCollectNullMetrics() {
        HTTPConnectionPool pool = mock(HTTPConnectionPool.class);
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", null);
        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);

        List<Metric> metricList = httpConnectionsMetricProvider.collect();
        assertEquals(metricList.size(), 0);
    }

    @Test
    public void testCollectZeroAvailableMetrics() {
        HTTPConnectionPool pool = mock(HTTPConnectionPool.class);
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", new WeakReference<>(pool));
        when(pool.getAvailable()).thenReturn(0);
        when(pool.getPending()).thenReturn(5);
        when(pool.getLeased()).thenReturn(2);
        when(pool.getMax()).thenReturn(100);
        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);

        List<Metric> metricList = httpConnectionsMetricProvider.collect();
        assertEquals(metricList.size(), 0);
    }

    @Test
    public void testCollectMultiPools() {
        HTTPConnectionPool pool = new HTTPConnectionPool(mockConnPoolControl(40, 15, 20, 50));
        HTTPConnectionPool pool2 = new HTTPConnectionPool(mockConnPoolControl(10, 15, 20, 100));
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", new WeakReference<>(pool));
        poolsMap.put("two", new WeakReference<>(pool2));
        poolsMap.put("twos", null);
        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);

        List<String> allowedMetrics = new ArrayList<>();
        allowedMetrics.add("available");
        allowedMetrics.add("leased");
        allowedMetrics.add("pending");
        allowedMetrics.add("max");
        List<Metric> metricList = httpConnectionsMetricProvider.collect();
        assertEquals(metricList.size(), 8);
        List<Metric> poolOneMetric =
                metricList.stream()
                        .filter(metric -> metric.getLabels().get("pool").toString().equalsIgnoreCase("one"))
                        .toList();
        assertTrue(poolOneMetric.stream().allMatch(metric -> allowedMetrics.contains(metric.getName())));
        assertEquals(poolOneMetric.size(), 4);

        Metric availableMetricOne =
                poolOneMetric.stream().filter(metric -> metric.getName().equalsIgnoreCase("available"))
                        .findFirst().get();
        assertEquals(Double.valueOf(40), availableMetricOne.getValue().doubleValue());
        Metric pendingMetricOne =
                poolOneMetric.stream().filter(metric -> metric.getName().equalsIgnoreCase("pending"))
                        .findFirst().get();
        assertEquals(Double.valueOf(15), pendingMetricOne.getValue().doubleValue());
        Metric leasedMetricOne =
                poolOneMetric.stream().filter(metric -> metric.getName().equalsIgnoreCase("leased"))
                        .findFirst().get();
        assertEquals(Double.valueOf(20), leasedMetricOne.getValue().doubleValue());
        List<Metric> poolTwoMetric =
                metricList.stream()
                        .filter(metric -> metric.getLabels().get("pool").toString().equalsIgnoreCase("two"))
                        .toList();
        assertTrue(poolTwoMetric.stream().allMatch(metric -> allowedMetrics.contains(metric.getName())));
        assertEquals(poolTwoMetric.size(), 4);

        Metric availableMetric =
                poolTwoMetric.stream().filter(metric -> metric.getName().equalsIgnoreCase("available"))
                        .findFirst().get();
        assertEquals(Double.valueOf(10), availableMetric.getValue().doubleValue());
        Metric pendingMetric =
                poolTwoMetric.stream().filter(metric -> metric.getName().equalsIgnoreCase("pending"))
                        .findFirst().get();
        assertEquals(Double.valueOf(15), pendingMetric.getValue().doubleValue());
        Metric leasedMetric =
                poolTwoMetric.stream().filter(metric -> metric.getName().equalsIgnoreCase("leased"))
                        .findFirst().get();
        assertEquals(Double.valueOf(20), leasedMetric.getValue().doubleValue());
    }

    @Test
    public void testCollectMultiPoolsMorethan10() {
        HTTPConnectionPool pool = new HTTPConnectionPool(mockConnPoolControl(40, 15, 20, 50));
        HTTPConnectionPool pool2 = new HTTPConnectionPool(mockConnPoolControl(90, 15, 20, 100));
        HTTPConnectionPool pool3 = new HTTPConnectionPool(mockConnPoolControl(70, 15, 20, 100));
        HTTPConnectionPool pool4 = new HTTPConnectionPool(mockConnPoolControl(50, 15, 20, 100));
        HTTPConnectionPool pool5 = new HTTPConnectionPool(mockConnPoolControl(20, 15, 20, 100));
        HTTPConnectionPool pool6 = new HTTPConnectionPool(mockConnPoolControl(10, 15, 20, 100));
        HTTPConnectionPool pool7 = new HTTPConnectionPool(mockConnPoolControl(80, 15, 20, 100));
        HTTPConnectionPool pool8 = new HTTPConnectionPool(mockConnPoolControl(30, 15, 20, 100));
        HTTPConnectionPool pool9 = new HTTPConnectionPool(mockConnPoolControl(100, 15, 20, 100));
        HTTPConnectionPool pool10 = new HTTPConnectionPool(mockConnPoolControl(60, 15, 20, 100));
        HTTPConnectionPool pool11 = new HTTPConnectionPool(mockConnPoolControl(90, 15, 20, 100));
        HTTPConnectionPool pool12 = new HTTPConnectionPool(mockConnPoolControl(5, 15, 20, 100));
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", new WeakReference<>(pool));
        poolsMap.put("two", new WeakReference<>(pool2));
        poolsMap.put("three", new WeakReference<>(pool3));
        poolsMap.put("four", new WeakReference<>(pool4));
        poolsMap.put("five", new WeakReference<>(pool5));
        poolsMap.put("six", new WeakReference<>(pool6));
        poolsMap.put("seven", new WeakReference<>(pool7));
        poolsMap.put("eight", new WeakReference<>(pool8));
        poolsMap.put("nine", new WeakReference<>(pool9));
        poolsMap.put("ten", new WeakReference<>(pool10));
        poolsMap.put("eleven", new WeakReference<>(pool11));
        poolsMap.put("twelve", new WeakReference<>(pool12));

        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);

        List<String> allowedMetrics = new ArrayList<>();
        allowedMetrics.add("available");
        allowedMetrics.add("leased");
        allowedMetrics.add("pending");
        List<Metric> metricList = httpConnectionsMetricProvider.collect();
        assertEquals(metricList.size(), 40);

        assertFalse(metricList.stream()
                .anyMatch(metric -> metric.getLabels().get("pool").toString().equalsIgnoreCase("six")));
        assertFalse(metricList.stream()
                .anyMatch(metric -> metric.getLabels().get("pool").toString().equalsIgnoreCase("twelve")));


    }

    @Test
    public void testCollectMultiPoolsMorethanConfiguredValue() {
        int configuredRepos = 5;
        homeStub.getArtifactoryProperties()
                .setProperty(ConstantValues.totalRepositoriesForHttpConnectionMetrics.getPropertyName(),
                        String.valueOf(configuredRepos));
        HTTPConnectionPool pool = new HTTPConnectionPool(mockConnPoolControl(40, 15, 20, 50));
        HTTPConnectionPool pool2 = new HTTPConnectionPool(mockConnPoolControl(90, 15, 20, 100));
        HTTPConnectionPool pool3 = new HTTPConnectionPool(mockConnPoolControl(70, 15, 20, 100));
        HTTPConnectionPool pool4 = new HTTPConnectionPool(mockConnPoolControl(50, 15, 20, 100));
        HTTPConnectionPool pool5 = new HTTPConnectionPool(mockConnPoolControl(20, 15, 20, 100));
        HTTPConnectionPool pool6 = new HTTPConnectionPool(mockConnPoolControl(10, 15, 20, 100));
        HTTPConnectionPool pool7 = new HTTPConnectionPool(mockConnPoolControl(80, 15, 20, 100));
        HTTPConnectionPool pool8 = new HTTPConnectionPool(mockConnPoolControl(30, 15, 20, 100));
        HTTPConnectionPool pool9 = new HTTPConnectionPool(mockConnPoolControl(100, 15, 20, 100));
        HTTPConnectionPool pool10 = new HTTPConnectionPool(mockConnPoolControl(60, 15, 20, 100));
        HTTPConnectionPool pool11 = new HTTPConnectionPool(mockConnPoolControl(90, 15, 20, 100));
        HTTPConnectionPool pool12 = new HTTPConnectionPool(mockConnPoolControl(5, 15, 20, 100));
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", new WeakReference<>(pool));
        poolsMap.put("two", new WeakReference<>(pool2));
        poolsMap.put("three", new WeakReference<>(pool3));
        poolsMap.put("four", new WeakReference<>(pool4));
        poolsMap.put("five", new WeakReference<>(pool5));
        poolsMap.put("six", new WeakReference<>(pool6));
        poolsMap.put("seven", new WeakReference<>(pool7));
        poolsMap.put("eight", new WeakReference<>(pool8));
        poolsMap.put("nine", new WeakReference<>(pool9));
        poolsMap.put("ten", new WeakReference<>(pool10));
        poolsMap.put("eleven", new WeakReference<>(pool11));
        poolsMap.put("twelve", new WeakReference<>(pool12));

        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);

        List<String> allowedMetrics = new ArrayList<>();
        allowedMetrics.add("available");
        allowedMetrics.add("leased");
        allowedMetrics.add("pending");
        List<Metric> metricList = httpConnectionsMetricProvider.collect();
        assertEquals(metricList.size(), configuredRepos * 4);
    }

    @Test
    public void testCollectNullPool() {
        HTTPConnectionPool pool = new HTTPConnectionPool(mockConnPoolControl(10, 5, 2, 100));
        Map<String, WeakReference<HTTPConnectionPool>> poolsMap = new HashMap<>();
        poolsMap.put("one", new WeakReference<>(pool));
        poolsMap.put("two", null);
        when(httpConnectionPoolMonitorService.getConnectionPools()).thenReturn(poolsMap);

        List<String> allowedMetrics = new ArrayList<>();
        allowedMetrics.add("available");
        allowedMetrics.add("max");
        allowedMetrics.add("leased");
        allowedMetrics.add("pending");
        List<Metric> metricList = httpConnectionsMetricProvider.collect();
        assertEquals(metricList.size(), 4);
        assertTrue(metricList.stream().allMatch(metric -> allowedMetrics.contains(metric.getName())));
        Metric availableMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("available"))
                .findFirst()
                .get();
        assertEquals(availableMetric.getValue().doubleValue(), 10);
        Metric pendingMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("pending"))
                .findFirst()
                .get();
        assertEquals(pendingMetric.getValue().doubleValue(), 5);
        Metric leasedMetric = metricList.stream()
                .filter(metric -> metric.getName().equalsIgnoreCase("leased"))
                .findFirst()
                .get();
        assertEquals(leasedMetric.getValue().doubleValue(), 2);
    }

    @Test
    public void testGetReportType() {
        assertEquals(httpConnectionsMetricProvider.getReportType(), MetricProvider.ReportType.CONTINUOUS);
    }

    @Test
    public void testGetLastUpdatedMillis() {
        assertEquals(httpConnectionsMetricProvider.getLastUpdatedMillis(), 0L);
    }
}