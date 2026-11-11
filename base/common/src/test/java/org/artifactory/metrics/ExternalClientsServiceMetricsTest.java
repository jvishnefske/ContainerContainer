package org.artifactory.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RequestThreadLocal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class ExternalClientsServiceMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        MDC.clear();
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void recordRequestShouldIncrementCounterAndRecordTimer() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsServiceMetrics.recordRequest("xray", 100);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
            assertEquals(100, timer.totalTime(TimeUnit.MILLISECONDS));
            assertEquals("xray", timer.getId().getTag("target"));
        }
    }

    @Test
    void recordRequestShouldIncludeGroupAndPackageTags() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            CurrentActivity.GROUP.put("download");
            CurrentActivity.PACKAGE.put("npm");

            ExternalClientsServiceMetrics.recordRequest("xray", 50);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals("download", timer.getId().getTag("group"));
            assertEquals("npm", timer.getId().getTag("package"));
        }
    }

    @Test
    void recordRequestShouldHandleNegativeLatency() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsServiceMetrics.recordRequest("access", -10);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals(0, timer.totalTime(TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void recordRequestShouldHandleEmptyGroupAndPackage() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsServiceMetrics.recordRequest("curation", 200);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals("", timer.getId().getTag("group"));
            assertEquals("", timer.getId().getTag("package"));
        }
    }

    @Test
    void recordRequestShouldAccumulateMultipleCalls() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsServiceMetrics.recordRequest("xray", 100);
            ExternalClientsServiceMetrics.recordRequest("xray", 200);
            ExternalClientsServiceMetrics.recordRequest("xray", 150);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals(3, timer.count());
            assertEquals(450, timer.totalTime(TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void recordRequest_shouldUpdateDownloadPerfCounters_whenDownloadPerfCountersExists() {
        try (
                MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class);
                MockedStatic<RequestThreadLocal> requestThreadLocal = mockStatic(RequestThreadLocal.class)
        ) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            DownloadPerfCounters downloadPerfCounters = new DownloadPerfCounters();
            requestThreadLocal.when(() -> RequestThreadLocal.getDownloadPerfCounters(true))
                    .thenReturn(downloadPerfCounters);

            ExternalClientsServiceMetrics.recordRequest("xray", 100);

            Map<String, Long> waitTimeMap = downloadPerfCounters.getExternalWaitTimeMsec();
            assertNotNull(waitTimeMap);
            assertEquals(1, waitTimeMap.size());
            assertEquals(100L, waitTimeMap.get("xray"));
        }
    }

    @Test
    void recordRequest_shouldNotFail_whenDownloadPerfCountersIsNull() {
        try (
                MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class);
                MockedStatic<RequestThreadLocal> requestThreadLocal = mockStatic(RequestThreadLocal.class)
        ) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            requestThreadLocal.when(() -> RequestThreadLocal.getDownloadPerfCounters(true)).thenReturn(null);

            ExternalClientsServiceMetrics.recordRequest("xray", 100);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
        }
    }

    @Test
    void recordRequest_shouldUseCorrectedLatencyForDownloadPerfCounters_whenNegativeLatencyProvided() {
        try (
                MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class);
                MockedStatic<RequestThreadLocal> requestThreadLocal = mockStatic(RequestThreadLocal.class)
        ) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            DownloadPerfCounters downloadPerfCounters = new DownloadPerfCounters();
            requestThreadLocal.when(() -> RequestThreadLocal.getDownloadPerfCounters(true))
                    .thenReturn(downloadPerfCounters);

            ExternalClientsServiceMetrics.recordRequest("access", -10);

            Map<String, Long> waitTimeMap = downloadPerfCounters.getExternalWaitTimeMsec();
            assertNotNull(waitTimeMap);
            assertEquals(1, waitTimeMap.size());
            assertEquals(0L, waitTimeMap.get("access"));
        }
    }

    @Test
    void recordRequest_shouldAccumulateMultipleCallsInDownloadPerfCounters() {
        try (
                MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class);
                MockedStatic<RequestThreadLocal> requestThreadLocal = mockStatic(RequestThreadLocal.class)
        ) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            DownloadPerfCounters downloadPerfCounters = new DownloadPerfCounters();
            requestThreadLocal.when(() -> RequestThreadLocal.getDownloadPerfCounters(true))
                    .thenReturn(downloadPerfCounters);

            ExternalClientsServiceMetrics.recordRequest("xray", 100);
            ExternalClientsServiceMetrics.recordRequest("xray", 200);
            ExternalClientsServiceMetrics.recordRequest("jfrog", 50);

            Map<String, Long> waitTimeMap = downloadPerfCounters.getExternalWaitTimeMsec();
            assertNotNull(waitTimeMap);
            assertEquals(2, waitTimeMap.size());
            assertEquals(300L, waitTimeMap.get("xray"));
            assertEquals(50L, waitTimeMap.get("jfrog"));
        }
    }
}

