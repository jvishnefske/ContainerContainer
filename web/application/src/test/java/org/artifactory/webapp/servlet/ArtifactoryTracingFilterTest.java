package org.artifactory.webapp.servlet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import lombok.SneakyThrows;
import org.artifactory.activity.ActivityContext;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.opentracing.ArtifactoryTracer;
import org.artifactory.opentracing.ArtifactoryTracerConfiguration;
import org.artifactory.opentracing.ServerTracingScope;
import org.artifactory.opentracing.ServerTracingSpan;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.enumeration;
import static org.artifactory.api.context.ArtifactoryContext.APPLICATION_CONTEXT_KEY;
import static org.artifactory.opentracing.ArtifactoryTracer.UBER_TRACE_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Uriah Levy
 */
class ArtifactoryTracingFilterTest {

    @Test
    void testFilterWithIncomingTraceHeader() throws IOException, ServletException {
        ArtifactoryTracingFilter artifactoryTracingFilter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer =
                spy(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));
        artifactoryTracingFilter.setArtifactoryTracer(tracer);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of(UBER_TRACE_ID)));
        String fullTraceId = "63fdd1725a3c5673:276415bdb255cfe1:62c7e48c60a7afba:0";
        when(request.getHeader(UBER_TRACE_ID)).thenReturn(fullTraceId);
        String requestPath = "artifactory/api/system/ping";
        when(request.getPathInfo()).thenReturn(requestPath);

        FilterChain filterChain = mock(FilterChain.class);
        List<String> shortTraceIdHolder = new ArrayList<>();
        doAnswer(x -> shortTraceIdHolder.add(MDC.get(UBER_TRACE_ID))).when(filterChain).doFilter(any(), any());

        artifactoryTracingFilter.doFilter(request, mock(ServletResponse.class), filterChain);
        verify(tracer).startServerSpan(argThat(requestPath::equals),
                argThat(arg -> arg.values().stream().anyMatch(fullTraceId::equals)));
        // Original trace ID preserved
        assertEquals("000000000000000063fdd1725a3c5673", shortTraceIdHolder.getFirst());
        // Span is finished
        assertNull(tracer.getCurrentThreadActiveSpan());
        assertNull(MDC.get(UBER_TRACE_ID));
    }

    @Test
    void testFilterWithIncomingTraceHeader_canonicalizedCasing() throws IOException, ServletException {
        ArtifactoryTracingFilter artifactoryTracingFilter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer =
                spy(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));
        artifactoryTracingFilter.setArtifactoryTracer(tracer);

        String canonicalizedHeader = "Uber-Trace-Id";
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of(canonicalizedHeader)));
        String fullTraceId = "63fdd1725a3c5673:276415bdb255cfe1:62c7e48c60a7afba:0";
        when(request.getHeader(canonicalizedHeader)).thenReturn(fullTraceId);
        String requestPath = "artifactory/api/system/ping";
        when(request.getPathInfo()).thenReturn(requestPath);

        FilterChain filterChain = mock(FilterChain.class);
        List<String> shortTraceIdHolder = new ArrayList<>();
        doAnswer(x -> shortTraceIdHolder.add(MDC.get(UBER_TRACE_ID))).when(filterChain).doFilter(any(), any());

        artifactoryTracingFilter.doFilter(request, mock(ServletResponse.class), filterChain);
        verify(tracer).startServerSpan(argThat(requestPath::equals),
                argThat(arg -> arg.values().stream().anyMatch(fullTraceId::equals)));
        assertEquals("000000000000000063fdd1725a3c5673", shortTraceIdHolder.getFirst());
        assertNull(tracer.getCurrentThreadActiveSpan());
        assertNull(MDC.get(UBER_TRACE_ID));
    }

    @Test
    void testFilterWithoutIncomingTraceHeader() throws IOException, ServletException {
        ArtifactoryTracingFilter artifactoryTracingFilter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer =
                spy(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));
        artifactoryTracingFilter.setArtifactoryTracer(tracer);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of("Host")));
        when(request.getHeader("Host")).thenReturn("foo.com");
        String requestPath = "artifactory/api/system/ping";
        when(request.getPathInfo()).thenReturn(requestPath);

        FilterChain filterChain = mock(FilterChain.class);
        List<String> shortTraceIdHolder = new ArrayList<>();
        List<ServerTracingSpan> tracingSpanHolder = new ArrayList<>();
        doAnswer(x -> {
                    shortTraceIdHolder.add(MDC.get(UBER_TRACE_ID));
                    return tracingSpanHolder.add(tracer.getCurrentThreadActiveSpan());
                }
        ).when(filterChain).doFilter(any(), any());

        artifactoryTracingFilter.doFilter(request, mock(ServletResponse.class), filterChain);
        verify(tracer).startServerSpan(argThat(requestPath::equals),
                argThat(arg -> arg.values().stream().anyMatch(x -> true)));
        // Span is null
        assertNull(tracer.getCurrentThreadActiveSpan());
        // A new span was created
        assertNotNull(tracingSpanHolder.getFirst());
        assertEquals(tracingSpanHolder.getFirst().getTraceId(), shortTraceIdHolder.getFirst());
    }

    @Test
    void testArtifactoryTracerNull() {
        ArtifactoryTracingFilter artifactoryTracingFilter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer = spy(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));

        FilterConfig filterConfig = mock(FilterConfig.class);
        ServletContext servletContext = mock(ServletContext.class);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        ArtifactoryContext artifactoryContext = mock(ArtifactoryContext.class);
        when(servletContext.getAttribute(APPLICATION_CONTEXT_KEY)).thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(ArtifactoryTracer.class)).thenReturn(tracer);
        setField(artifactoryTracingFilter, "filterConfig", filterConfig);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of("Host")));
        when(request.getHeader("Host")).thenReturn("foo.com");
        FilterChain filterChain = mock(FilterChain.class);
        assertDoesNotThrow(() -> artifactoryTracingFilter.doFilter(request, mock(ServletResponse.class), filterChain));
        verify(tracer).startServerSpan(any(), any());
    }

    @Test
    void testActivityPropertiesAreSet() throws IOException, ServletException {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        filter.setArtifactoryTracer(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of()));
        when(request.getPathInfo()).thenReturn("artifactory/api/system/ping");
        when(request.getMethod()).thenReturn("POST");

        FilterChain chain = (ServletRequest req, ServletResponse resp) ->
                assertEquals("http/post", CurrentActivity.KIND.get());

        filter.doFilter(request, mock(ServletResponse.class), chain);
    }

    @Test
    void ArtifactoryTracingFilter_shouldSaveActivityContextOnServletRequest() throws IOException, ServletException {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        filter.setArtifactoryTracer(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of()));
        when(request.getPathInfo()).thenReturn("artifactory/api/system/ping");
        when(request.getMethod()).thenReturn("POST");

        filter.doFilter(request, mock(ServletResponse.class), (ServletRequest req, ServletResponse resp) -> {});

        // The filter is supposed to save the ActivityContext on the servlet request:
        verify(request).setAttribute(eq(ActivityContext.class.getName()), any(ActivityContext.class));
    }

    @Test
    @SneakyThrows
    void startServerSpan_shouldNotFail_whenNoContext() {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        FilterConfig filterConfig = mock(FilterConfig.class);
        when(filterConfig.getServletContext()).thenReturn(mock());
        filter.init(filterConfig);
        HttpServletRequest request = mock();

        ServerTracingScope actual = filter.startServerSpan(request);
        assertEquals(ServerTracingScope.NOOP, actual);
    }

    @Test
    void testStartServerSpan_NoHeaders() {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer = spy(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer()));
        filter.setArtifactoryTracer(tracer);
        HttpServletRequest request = mock();
        when(request.getHeaderNames()).thenReturn(null);
        String requestPath = "test/no-headers";
        when(request.getPathInfo()).thenReturn(requestPath);

        try (ServerTracingScope ignored = filter.startServerSpan(request)) {
            // There are no headers, so tracer supposed to start a new span without additional context
            verify(tracer).startServerSpan(requestPath, Map.of());
        }
    }

    @Test
    void shouldNotCallStartServerSpan_whenJavaAgentIsActive() {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer = spy(new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer(), true));
        filter.setArtifactoryTracer(tracer);
        HttpServletRequest request = mock();
        when(request.getHeaderNames()).thenReturn(null);
        String requestPath = "test/no-headers";
        when(request.getPathInfo()).thenReturn(requestPath);

        try (ServerTracingScope ignored = filter.startServerSpan(request)) {
            // There are no headers, so tracer supposed to start a new span without additional context
            verify(tracer).openScopeForCurrentSpan();
        }
    }

    @Test
    void doFilter_shouldRenameSpan_whenNonJerseyRequest() throws IOException, ServletException {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer = new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer());
        filter.setArtifactoryTracer(tracer);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of()));
        when(request.getPathInfo()).thenReturn("artifactory/libs-release/org/foo/bar.jar");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release/org/foo/bar.jar");

        List<Span> spanHolder = new ArrayList<>();
        FilterChain chain = (ServletRequest req, ServletResponse resp) -> {
            CurrentActivity.GROUP.put("download");
            spanHolder.add(Span.current());
        };

        filter.doFilter(request, mock(ServletResponse.class), chain);

        Span span = spanHolder.getFirst();
        SpanData data = ((ReadableSpan) span).toSpanData();
        assertEquals("http/get download", data.getName());
    }

    @Test
    void doFilter_shouldNotRenameSpan_whenJerseyRequest() throws IOException, ServletException {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer = new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer());
        filter.setArtifactoryTracer(tracer);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of()));
        when(request.getPathInfo()).thenReturn("artifactory/api/npm/npm-remote/lodash");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/artifactory/api/npm/npm-remote/lodash");

        List<Span> spanHolder = new ArrayList<>();
        FilterChain chain = (ServletRequest req, ServletResponse resp) -> {
            CurrentActivity.GROUP.put("api");
            CurrentActivity.NAME.put("/api/npm/{repoKey}/{path: .+}");
            spanHolder.add(Span.current());
        };

        filter.doFilter(request, mock(ServletResponse.class), chain);

        Span span = spanHolder.getFirst();
        SpanData data = ((ReadableSpan) span).toSpanData();
        // Span name should NOT have been renamed by the filter since NAME is set (Jersey handled it)
        assertNotNull(data.getName());
        // The original span name should be preserved (not replaced with activity-based name)
        assertEquals("artifactory/api/npm/npm-remote/lodash", data.getName());
    }

    @Test
    void doFilter_shouldEnrichSpanWithActivityAttributes() throws IOException, ServletException {
        ArtifactoryTracingFilter filter = new ArtifactoryTracingFilter();
        ArtifactoryTracer tracer = new ArtifactoryTracer(ArtifactoryTracerConfiguration.testTracer());
        filter.setArtifactoryTracer(tracer);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaderNames()).thenReturn(enumeration(List.of()));
        when(request.getPathInfo()).thenReturn("artifactory/api/npm/npm-remote/lodash");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/artifactory/api/npm/npm-remote/lodash");

        List<Span> spanHolder = new ArrayList<>();
        FilterChain chain = (ServletRequest req, ServletResponse resp) -> {
            CurrentActivity.PACKAGE.put("npm");
            CurrentActivity.REPO.put("npm-remote");
            CurrentActivity.REPO_TYPE.put("remote");
            spanHolder.add(Span.current());
        };

        filter.doFilter(request, mock(ServletResponse.class), chain);

        // After the filter completes, the span should have been enriched with activity attributes
        Span span = spanHolder.getFirst();
        assertNotNull(span);
        SpanData data = ((ReadableSpan) span).toSpanData();
        assertEquals("http/get", data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.kind")));
        assertEquals("npm", data.getAttributes().get(AttributeKey.stringKey("artifactory.activity.package")));
        assertEquals("npm-remote", data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.key")));
        assertEquals("remote", data.getAttributes().get(AttributeKey.stringKey("artifactory.repo.type")));
    }

}
