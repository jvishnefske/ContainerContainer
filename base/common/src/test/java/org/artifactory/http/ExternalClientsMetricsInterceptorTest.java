package org.artifactory.http;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.slf4j.MDC;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.artifactory.http.ExternalClientsMetricsInterceptor.TARGET_SERVICE_ATTR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class ExternalClientsMetricsInterceptorTest {

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
    void processShouldRecordLatencyBetweenRequestAndResponse() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsMetricsInterceptor interceptor = new ExternalClientsMetricsInterceptor("xray");

            AtomicLong currentTime = new AtomicLong(1000L);
            interceptor.setTimeSupplier(currentTime::get);

            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            HttpContext httpContext = new BasicHttpContext();

            interceptor.process(request, httpContext);
            currentTime.set(1150L);
            interceptor.process(response, httpContext);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals(1, timer.count());
            assertEquals(150, timer.totalTime(TimeUnit.MILLISECONDS));
            assertEquals("xray", timer.getId().getTag("target"));
        }
    }

    @Test
    void processShouldHandleMissingStartTime() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsMetricsInterceptor interceptor = new ExternalClientsMetricsInterceptor("xray");

            HttpResponse response = mock(HttpResponse.class);
            HttpContext httpContext = new BasicHttpContext();

            // Process response without prior request (no start time)
            interceptor.process(response, httpContext);

            // No metrics should be recorded
            Timer timer = registry.find("http.external.requests").timer();
            assertNull(timer);
        }
    }

    @Test
    void processShouldUseCorrectTargetService() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsMetricsInterceptor interceptor = new ExternalClientsMetricsInterceptor("access");
            interceptor.setTimeSupplier(() -> 1000L);

            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            HttpContext httpContext = new BasicHttpContext();

            interceptor.process(request, httpContext);
            interceptor.process(response, httpContext);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals("access", timer.getId().getTag("target"));
        }
    }

    @Test
    void processShouldRecordMultipleRequests() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsMetricsInterceptor interceptor = new ExternalClientsMetricsInterceptor("xray");

            AtomicLong currentTime = new AtomicLong(0L);
            interceptor.setTimeSupplier(currentTime::get);

            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);

            // First request: 100ms
            HttpContext context1 = new BasicHttpContext();
            currentTime.set(0L);
            interceptor.process(request, context1);
            currentTime.set(100L);
            interceptor.process(response, context1);

            // Second request: 200ms
            HttpContext context2 = new BasicHttpContext();
            currentTime.set(100L);
            interceptor.process(request, context2);
            currentTime.set(300L);
            interceptor.process(response, context2);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals(2, timer.count());
            assertEquals(300, timer.totalTime(TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void processShouldUseTargetServiceFromContext() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsMetricsInterceptor interceptor = new ExternalClientsMetricsInterceptor("xray");
            interceptor.setTimeSupplier(() -> 1000L);

            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            HttpContext httpContext = new BasicHttpContext();
            httpContext.setAttribute(TARGET_SERVICE_ATTR, "curation");

            interceptor.process(request, httpContext);
            interceptor.process(response, httpContext);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals("curation", timer.getId().getTag("target"));
        }
    }

    @Test
    void processShouldUseDefaultTargetWhenNoContextAttribute() {
        try (MockedStatic<ContextHelper> contextHelper = mockStatic(ContextHelper.class)) {
            ArtifactoryContext context = mock(ArtifactoryContext.class);
            when(context.beanForType(MeterRegistry.class)).thenReturn(registry);
            contextHelper.when(ContextHelper::get).thenReturn(context);

            ExternalClientsMetricsInterceptor interceptor = new ExternalClientsMetricsInterceptor("xray");
            interceptor.setTimeSupplier(() -> 1000L);

            HttpRequest request = mock(HttpRequest.class);
            HttpResponse response = mock(HttpResponse.class);
            HttpContext httpContext = new BasicHttpContext();

            interceptor.process(request, httpContext);
            interceptor.process(response, httpContext);

            Timer timer = registry.find("http.external.requests").timer();
            assertNotNull(timer);
            assertEquals("xray", timer.getId().getTag("target"));
        }
    }
}
