package org.artifactory.repo.remote.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.artifactory.activity.ActivityScope;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.metrics.ExternalClientsServiceMetrics;
import org.artifactory.remote.stats.RemoteRepoStatsService;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.storage.db.devopsagent.requestlog.entity.OutboundRequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.service.OutboundRequestLogService;
import org.artifactory.traffic.OutboundRequestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class RemoteRepoRequestResponseInterceptorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private Appender<ILoggingEvent> mockAppender;
    @Mock
    private JFrogUserAgentService jfrogUserAgentService;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private RemoteRepoStatsService remoteRepoStatsService;
    @Mock
    private RemoteRepoOutboundHeaderAuditService headerAuditService;

    @Captor
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;
    private SimpleMeterRegistry metrics;
    private RemoteRepoRequestResponseInterceptor interceptor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Logger logger = (Logger) LoggerFactory.getLogger(OutboundRequestLogger.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(mockAppender);
        when(artifactoryContext.beanForType(JFrogUserAgentService.class)).thenReturn(jfrogUserAgentService);
        this.metrics = new SimpleMeterRegistry();
        when(artifactoryContext.beanForType(MeterRegistry.class)).thenReturn(this.metrics);
        when(artifactoryContext.beanForType("applicationMeterRegistry", MeterRegistry.class))
                .thenReturn(this.metrics);
        when(jfrogUserAgentService.getUserAgent()).thenReturn("JFrogUserAgent");
        when(artifactoryContext.beanForType(RemoteRepoStatsService.class)).thenReturn(remoteRepoStatsService);
        when(artifactoryContext.beanForType(RemoteRepoOutboundHeaderAuditService.class))
                .thenReturn(headerAuditService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        interceptor = new RemoteRepoRequestResponseInterceptor("repo1", "user1", true, "docker",
                "https://upstream.example/artifactory/my-remote");
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
        MDC.clear();
    }

    @Test
    void testProcessRequest() {
        HttpContext context = new HttpCoreContext();
        HttpRequest request = mock(HttpRequest.class);
        interceptor.process(request, context);
        assertNotNull(context.getAttribute("request_start_time"));
    }

    @Test
    void testProcessResponse() throws URISyntaxException {
        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(protocolVersion, 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 234L); // The duration will be 134 msec
        interceptor.process(response, context);
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent loggingEvent = captorLoggingEvent.getValue();
        String logMessage = loggingEvent.getFormattedMessage();
        assertTrue(logMessage.contains("repo1|user1|GET|http://example.com|200|0|0"));

        String metricsText = this.metrics.getMetersAsString();
        assertEquals(
                "http.external.requests(TIMER)[group='', package='docker', target='remote'];" +
                        " count=1.0, total_time=0.134 seconds, max=0.134 seconds\n" +
                        "http.time.remote(TIMER)[group='', method='GET', package='docker', status='200']; count=1.0, " +
                        "total_time=0.134 seconds, max=0.134 seconds", metricsText);
    }

    @Test
    void testDownloadTimeMetrics() throws URISyntaxException {
        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("HEAD");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(protocolVersion, 404, ""));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 2100L); // The duration will be 2 seconds
        try (ActivityScope ignored = CurrentActivity.newScope()) {
            CurrentActivity.GROUP.put("test");
            CurrentActivity.PACKAGE.put("docker");
            interceptor.process(response, context);
        }
        String metricsText = this.metrics.getMetersAsString();
        assertEquals("http.external.requests(TIMER)[group='test', package='docker', target='remote']; count=1.0, " +
                "total_time=2.0 seconds, max=2.0 seconds\n" +
                "http.time.remote(TIMER)[group='test', method='HEAD', package='docker', status='404']; " +
                "count=1.0, total_time=2.0 seconds, max=2.0 seconds", metricsText);
    }

    @Test
    void process_shouldRecordDownloadTime_whenDownloadPerfCountersAvailable() throws URISyntaxException {
        try {
            DownloadPerfCounters counters = new DownloadPerfCounters();
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(httpRequest.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);
            RequestWrapper servletRequestWrapper = mock(RequestWrapper.class);
            when(servletRequestWrapper.getRequest()).thenReturn(httpRequest);
            RequestThreadLocal.bind(servletRequestWrapper);

            HttpContext context = new HttpCoreContext();
            HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
            when(requestWrapper.getMethod()).thenReturn("GET");
            when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
            context.setAttribute("http.request", requestWrapper);
            context.setAttribute("request_start_time", 0L);
            HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                    new ProtocolVersion("HTTP", 1, 1),
                    200, "OK")
            );
            context.setAttribute("http.response", response);
            interceptor.setTimeSupplier(() -> 2718L);

            // Act
            interceptor.process(response, context);

            // Assert
            assertEquals(2718, counters.getRemoteDownloadDurationMillis());
        } finally {
            RequestThreadLocal.unbind();
        }
    }

    @Test
    void process_shouldCalculateRemoteDownloadDuration_whenRequestBodyConsumed()
            throws URISyntaxException, IOException {
        try {
            DownloadPerfCounters counters = new DownloadPerfCounters();
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(httpRequest.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);
            RequestWrapper servletRequestWrapper = mock(RequestWrapper.class);
            when(servletRequestWrapper.getRequest()).thenReturn(httpRequest);
            RequestThreadLocal.bind(servletRequestWrapper);

            HttpContext context = new HttpCoreContext();
            HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
            when(requestWrapper.getMethod()).thenReturn("GET");
            when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
            context.setAttribute("http.request", requestWrapper);
            context.setAttribute("request_start_time", 0L);
            HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                    new ProtocolVersion("HTTP", 1, 1),
                    200, "OK")
            );
            interceptor.setTimeSupplier(() -> 12345L);
            // Add response entity to emulate a remote download
            InputStream inputStreamMock = mock(InputStream.class);
            BasicHttpEntity originalHttpEntity = new BasicHttpEntity();
            originalHttpEntity.setContent(inputStreamMock);
            response.setEntity(originalHttpEntity);
            // Set response object to the context
            context.setAttribute("http.response", response);
            // Mock calls to OutboundRequestLogger
            try (MockedStatic<OutboundRequestLogger> mockedStatic = Mockito.mockStatic(OutboundRequestLogger.class)) {
                mockedStatic.when(() -> OutboundRequestLogger.log(anyString(), anyString(), anyString(), anyString(),
                                anyInt(), anyLong(), anyLong(), anyLong(), anyBoolean()))
                        .thenAnswer(invocation -> null);
                // Call the interceptor
                interceptor.process(response, context);
                // Assert null, because "download is still in progress"
                assertNull(counters.getRemoteDownloadDurationMillis());
                // Emulate reading the stream, and the "close()" method will be called after the try-finally
                try (InputStream respStream = response.getEntity().getContent()) {
                    respStream.read();
                    // Assert the download time is still null, because the stream hasn't been closed yet
                    assertNull(counters.getRemoteDownloadDurationMillis());
                    // Verify that the OutboundRequestLogger was not called
                    mockedStatic.verifyNoInteractions();
                }
                // Assert the time supplier was called after reading the stream and the correct download time returned
                assertEquals(12345L, counters.getRemoteDownloadDurationMillis());
                // Verify that OutboundRequestLogger was called with correct value of duration
                mockedStatic.verify(() -> OutboundRequestLogger.log(anyString(), anyString(), anyString(), anyString(),
                        anyInt(), anyLong(), anyLong(), eq(12345L), anyBoolean()), Mockito.times(1));
            }
        } finally {
            RequestThreadLocal.unbind();
        }
    }

    @Test
    void processShouldRecordExternalClientMetricsForRemoteDownload() throws URISyntaxException {
        try (MockedStatic<ExternalClientsServiceMetrics> mockedMetrics = Mockito.mockStatic(
                ExternalClientsServiceMetrics.class)) {
            HttpContext context = new HttpCoreContext();
            context.setAttribute("request_start_time", 100L);
            HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
            when(requestWrapper.getMethod()).thenReturn("GET");
            when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
            context.setAttribute("http.request", requestWrapper);
            ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
            HttpResponse response = new BasicHttpResponse(new BasicStatusLine(protocolVersion, 200, "OK"));
            context.setAttribute("http.response", response);
            interceptor.setTimeSupplier(() -> 350L);
            interceptor.process(response, context);
            mockedMetrics.verify(() -> ExternalClientsServiceMetrics.recordRequest(
                    ExternalClientsServiceMetrics.TARGET_REMOTE, 250L));
        }
    }

    @Test
    void process_shouldCreateDownloadPerfCountersAndRecordDtime_whenFailedRequestAndCountersNotPreCreated()
            throws URISyntaxException {
        try {
            // Setup request context without pre-creating DownloadPerfCounters
            // Use a Map to store attributes so setAttribute/getAttribute work correctly
            Map<String, Object> attributes = new HashMap<>();
            HttpServletRequest httpRequest = mock(HttpServletRequest.class);
            when(httpRequest.getAttribute(anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return attributes.get(key);
            });
            doAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Object value = invocation.getArgument(1);
                attributes.put(key, value);
                return null;
            }).when(httpRequest).setAttribute(anyString(), any());
            RequestWrapper servletRequestWrapper = mock(RequestWrapper.class);
            when(servletRequestWrapper.getRequest()).thenReturn(httpRequest);
            RequestThreadLocal.bind(servletRequestWrapper);

            HttpContext context = new HttpCoreContext();
            HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
            when(requestWrapper.getMethod()).thenReturn("GET");
            when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
            context.setAttribute("http.request", requestWrapper);
            context.setAttribute("request_start_time", 0L);
            // Simulate a 404 response (failed remote request)
            HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                    new ProtocolVersion("HTTP", 1, 1),
                    404, "Not Found")
            );
            context.setAttribute("http.response", response);
            interceptor.setTimeSupplier(() -> 5000L);

            // Act
            interceptor.process(response, context);

            // Assert - DownloadPerfCounters should be created and dtime should be recorded
            DownloadPerfCounters counters = RequestThreadLocal.getDownloadPerfCounters();
            assertNotNull(counters, "DownloadPerfCounters should be created automatically");
            assertEquals(5000L, counters.getRemoteDownloadDurationMillis(),
                    "dtime should be recorded for failed remote requests (404)");
        } finally {
            RequestThreadLocal.unbind();
        }
    }

    @Test
    void process_shouldLogFullUrl_whenUriIsAbsoluteAndTargetIsNull() throws URISyntaxException {
        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI(
                "https://plugins.gradle.org/m2/com/fasterxml/jackson/jackson-parent/2.6.2/jackson-parent-2.6.2.jar"));
        when(requestWrapper.getTarget()).thenReturn(null);
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 200L);

        interceptor.process(response, context);

        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        String logMessage = captorLoggingEvent.getValue().getFormattedMessage();
        String expectedFullUrl = "https://plugins.gradle.org/m2/com/fasterxml/jackson/jackson-parent/2.6.2/"
                + "jackson-parent-2.6.2.jar";
        assertTrue(logMessage.contains("|GET|" + expectedFullUrl + "|200|"),
                "Log should contain full absolute URL when target is null: " + logMessage);
    }

    @Test
    void process_shouldLogFullUrl_whenUriIsRelativeAndTargetIsSet() throws URISyntaxException {
        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        HttpHost target = new HttpHost("repo.example.com", 443, "https");
        when(requestWrapper.getMethod()).thenReturn("HEAD");
        when(requestWrapper.getURI()).thenReturn(
                new URI("/maven2/com/fasterxml/jackson/jackson-parent/2.6.2/jackson-parent-2.6.2.jar"));
        when(requestWrapper.getTarget()).thenReturn(target);
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 404, "Not Found"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 300L);

        interceptor.process(response, context);

        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        String logMessage = captorLoggingEvent.getValue().getFormattedMessage();
        String expectedUrl = "https://repo.example.com:443/maven2/com/fasterxml/jackson/jackson-parent/2.6.2/"
                + "jackson-parent-2.6.2.jar";
        assertTrue(logMessage.contains("|HEAD|" + expectedUrl + "|404|"),
                "Log should contain target + path when URI is relative: " + logMessage);
    }

    @Test
    void process_shouldLogFullUrl_whenUriIsRelativeAndTargetFromContext() throws URISyntaxException {
        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpHost contextTarget = new HttpHost("redirect.example.com", 443, "https");
        context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, contextTarget);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("/maven2/some/path.jar"));
        when(requestWrapper.getTarget()).thenReturn(null);
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 150L);

        interceptor.process(response, context);

        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        String logMessage = captorLoggingEvent.getValue().getFormattedMessage();
        assertTrue(logMessage.contains("|GET|https://redirect.example.com:443/maven2/some/path.jar|200|"),
                "Log should use target from context when request target is null: " + logMessage);
    }

    @Test
    void process_shouldLogPathOnly_whenUriIsRelativeAndNoTargetAvailable() throws URISyntaxException {
        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("HEAD");
        when(requestWrapper.getURI()).thenReturn(
                new URI("/maven2/com/fasterxml/jackson/jackson-parent/2.6.2/jackson-parent-2.6.2.jar"));
        when(requestWrapper.getTarget()).thenReturn(null);
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 404, "Not Found"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 250L);

        interceptor.process(response, context);

        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        String logMessage = captorLoggingEvent.getValue().getFormattedMessage();
        String expectedPath = "/maven2/com/fasterxml/jackson/jackson-parent/2.6.2/jackson-parent-2.6.2.jar";
        assertTrue(logMessage.contains("|HEAD|" + expectedPath + "|404|"),
                "Log should contain path only when URI is relative and no target: " + logMessage);
    }

    @Test
    void process_shouldUseConstructorPackageType_whenRecordingStats() throws URISyntaxException {
        RemoteRepoRequestResponseInterceptor interceptorWithPkgType =
                new RemoteRepoRequestResponseInterceptor("repo1", "user1", true, "npm",
                        "https://upstream.example/artifactory/npm-remote");
        interceptorWithPkgType.setTimeSupplier(() -> 200L);

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);

        interceptorWithPkgType.process(response, context);

        verify(remoteRepoStatsService).recordRequest(
                eq("repo1"), eq("npm"), eq("GET"), eq(200),
                anyLong(), anyLong(), anyLong());
    }

    @Test
    void process_shouldPassCorrectPackageType_whenDifferentRepoTypes() throws URISyntaxException {
        RemoteRepoRequestResponseInterceptor dockerInterceptor =
                new RemoteRepoRequestResponseInterceptor("repo2", "user1", true, "Docker",
                        "https://upstream.example/docker-remote");
        dockerInterceptor.setTimeSupplier(() -> 200L);

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);

        dockerInterceptor.process(response, context);

        verify(remoteRepoStatsService).recordRequest(
                eq("repo2"), eq("Docker"), eq("GET"), eq(200),
                anyLong(), anyLong(), anyLong());
    }

    @Test
    void processRequest_shouldNotRecordHeaderAudit_evenWhenSamplingEnabled() {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditEnabled.getPropertyName(), "true");

        HttpContext context = new HttpCoreContext();
        HttpGet get = new HttpGet("https://cdn.husseien.dev/repo/pkg.tgz");
        get.addHeader("X-Huss-L", "list-me");
        get.addHeader("Authorization", "Bearer secret-token");

        interceptor.process(get, context);

        verify(headerAuditService, never()).record(anyString(), anyString(), any());
    }

    @Test
    void processRequest_shouldNotRecordHeaderAudit_whenSamplingDisabled() {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditEnabled.getPropertyName(), "false");

        HttpContext context = new HttpCoreContext();
        HttpGet get = new HttpGet("https://example.com/pkg.tgz");
        get.addHeader("X-Huss-L", "nope");

        interceptor.process(get, context);

        verify(headerAuditService, never()).record(anyString(), anyString(), any());
    }

    @Test
    void processResponse_shouldRecordHeaderAudit_whenSamplingEnabled() throws URISyntaxException {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditEnabled.getPropertyName(), "true");

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("https://other-host.example/art.jar"));
        when(requestWrapper.getTarget()).thenReturn(null);
        context.setAttribute("http.request", requestWrapper);
        ProtocolVersion protocolVersion = new ProtocolVersion("HTTP", 1, 1);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(protocolVersion, 200, "OK"));
        response.addHeader("X-Upstream-Trace", "abc123");
        response.addHeader("Set-Cookie", "session=secret");
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 200L);

        interceptor.process(response, context);

        verify(headerAuditService, times(1)).record(eq("repo1"),
                eq("https://upstream.example/artifactory/my-remote"),
                eq(response));
    }

    @Test
    void processResponse_shouldNotRecordHeaderAudit_whenSamplingDisabled() throws URISyntaxException {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditEnabled.getPropertyName(), "false");

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        response.addHeader("X-Upstream-Trace", "abc123");
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 200L);

        interceptor.process(response, context);

        verify(headerAuditService, never()).record(anyString(), anyString(), any());
    }

    @Test
    void processResponse_shouldStillLogOutboundRequest_whenAuditServiceThrows() throws URISyntaxException {
        homeStub.setProperty(ConstantValues.remoteRepoOutboundHeadersAuditEnabled.getPropertyName(), "true");
        Mockito.doThrow(new RuntimeException("audit failed"))
                .when(headerAuditService).record(anyString(), anyString(), any());

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(new BasicStatusLine(
                new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 234L);

        // Must not propagate the audit failure to the outbound request flow.
        interceptor.process(response, context);

        // OutboundRequestLogger still wrote a line for the request.
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        String logMessage = captorLoggingEvent.getValue().getFormattedMessage();
        assertTrue(logMessage.contains("repo1|user1|GET|http://example.com|200|"), logMessage);
    }

    @Test
    void processResponse_enqueuesWhenServiceEnabled() throws URISyntaxException {
        OutboundRequestLogService outboundSvc = mock(OutboundRequestLogService.class);
        when(outboundSvc.isFeatureEnabled()).thenReturn(true);
        when(artifactoryContext.beanForType(OutboundRequestLogService.class)).thenReturn(outboundSvc);

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 234L);

        interceptor.process(response, context);

        ArgumentCaptor<OutboundRequestLogRow> captor = ArgumentCaptor.forClass(OutboundRequestLogRow.class);
        verify(outboundSvc).enqueueOutbound(captor.capture());
        assertEquals("GET", captor.getValue().httpMethod());
        assertEquals(200, captor.getValue().statusCode());
    }

    @Test
    void processResponse_noEnqueueWhenServiceDisabled() throws URISyntaxException {
        OutboundRequestLogService outboundSvc = mock(OutboundRequestLogService.class);
        when(outboundSvc.isFeatureEnabled()).thenReturn(false);
        when(artifactoryContext.beanForType(OutboundRequestLogService.class)).thenReturn(outboundSvc);

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 234L);

        interceptor.process(response, context);

        verify(outboundSvc, never()).enqueueOutbound(any());
    }

    @Test
    void processResponse_exceptionInEnqueueIsSwallowed() throws URISyntaxException {
        OutboundRequestLogService outboundSvc = mock(OutboundRequestLogService.class);
        when(outboundSvc.isFeatureEnabled()).thenReturn(true);
        doThrow(new RuntimeException("enqueue failed")).when(outboundSvc).enqueueOutbound(any());
        when(artifactoryContext.beanForType(OutboundRequestLogService.class)).thenReturn(outboundSvc);

        HttpContext context = new HttpCoreContext();
        context.setAttribute("request_start_time", 100L);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getMethod()).thenReturn("GET");
        when(requestWrapper.getURI()).thenReturn(new URI("http://example.com"));
        context.setAttribute("http.request", requestWrapper);
        HttpResponse response = new BasicHttpResponse(
                new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "OK"));
        context.setAttribute("http.response", response);
        interceptor.setTimeSupplier(() -> 234L);

        // Must not propagate the enqueue failure to the response processing flow.
        interceptor.process(response, context);
    }
}