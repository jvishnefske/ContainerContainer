package org.artifactory.webapp.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityScope;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.aql.model.AqlQueryTemplate;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.LoadHealerException;
import org.artifactory.logging.layout.request.RequestExtraFields;
import org.artifactory.metrics.MetricsListenerService;
import org.artifactory.request.DownloadPerfCounters;
import org.artifactory.request.RedirectInfo;
import org.artifactory.response.ResponseThreadLocal;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.qrl.service.LowPriorityRunnable;
import org.artifactory.throtlling.qrl.service.QueryRateLimiterService;
import org.artifactory.throtlling.search.service.SearchLimitRunnable;
import org.artifactory.throtlling.search.service.SearchRateLimiterService;
import org.artifactory.traffic.RequestLogger;
import org.artifactory.traffic.entry.RequestEntry;
import org.artifactory.util.HttpUtils;
import org.artifactory.storage.db.devopsagent.requestlog.entity.RequestLogRow;
import org.artifactory.storage.db.devopsagent.requestlog.service.RequestLogService;
import org.artifactory.webapp.servlet.util.ServletUtils;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.storage.binstore.ifc.RedirectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Andrey Tashlitsky
 */
@Slf4j
class RequestFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private final RequestFilter requestFilter = new RequestFilter();
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private FilterConfig filterConfig;
    @Mock
    private ServletContext servletContext;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private QueryRateLimiterService qrlService;
    @Mock
    private SearchRateLimiterService searchRateLimiterService;
    @Mock
    private MetricsListenerService metricsListenerService;
    private AutoCloseable mocks;
    private SimpleMeterRegistry metrics;
    private ActivityScope activityScope;

    @BeforeEach
    void setUp() throws ServletException, IOException {
        mocks = openMocks(this);
        this.metrics = new SimpleMeterRegistry();

        doAnswer(invocation -> {
            ((LowPriorityRunnable) invocation.getArgument(1)).run();
            return null;
        }).when(qrlService).wrapRequestWithLowPriority(any(HttpServletRequest.class), any(LowPriorityRunnable.class));
        doAnswer(invocation -> {
            ((SearchLimitRunnable) invocation.getArgument(1)).run();
            return null;
        }).when(searchRateLimiterService).wrapRequestWithSearchLimit(any(HttpServletRequest.class),
                any(SearchLimitRunnable.class));
        when(artifactoryContext.beanForType(QueryRateLimiterService.class)).thenReturn(qrlService);
        when(artifactoryContext.beanForType(SearchRateLimiterService.class)).thenReturn(searchRateLimiterService);
        when(artifactoryContext.beanForType(MetricsListenerService.class)).thenReturn(metricsListenerService);
        when(artifactoryContext.beanForType(MeterRegistry.class)).thenReturn(this.metrics);
        when(artifactoryContext.beanForType("applicationMeterRegistry", MeterRegistry.class))
                .thenReturn(this.metrics);
        ReflectionTestUtils.setField(requestFilter, "filterConfig", filterConfig);
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(artifactoryContext);
        requestFilter.delayedInit();

        MDC.clear();
        this.activityScope = CurrentActivity.newScope();
    }

    @AfterEach
    void tearDown() throws Exception {
        this.activityScope.close();
        mocks.close();
    }

    @Test
    void whenStatusIsUpdated() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            try {
                requestFilter.doFilter(request, response,
                        (req, res) ->
                        {
                            ((HttpServletResponse) res).setStatus(401);
                            throw new BadCredentialsException("Bad credentials");
                        }
                );
                fail();
            } catch (BadCredentialsException ignored) {
                assertLoggedStatus(requestLogger, 401);
            }
        }
    }

    @Test
    void whenStatusIsNotUpdated() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            try {
                requestFilter.doFilter(request, response,
                        (req, res) ->
                        {
                            throw new BadCredentialsException("Bad credentials");
                        }
                );
                fail();
            } catch (BadCredentialsException ignored) {
                assertLoggedStatus(requestLogger, 500);
            }
        }
    }

    @Test
    void testGetStatusCodeOk() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, filterChain);
            assertLoggedStatus(requestLogger, 200);
        }
    }

    @Test
    void testGetStatusCodeBlockedByClient() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            try (MockedStatic<ResponseThreadLocal> responseThreadLocal = mockStatic(ResponseThreadLocal.class)) {
                responseThreadLocal.when(ResponseThreadLocal::isAbortedByClient).thenReturn(true);
                requestFilter.doFilter(request, response, filterChain);
                assertLoggedStatus(requestLogger, 499);
            }
        }
    }

    @Test
    void testGetStatusCodeWithCustomStatus() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            try (MockedStatic<ResponseThreadLocal> responseThreadLocal = mockStatic(ResponseThreadLocal.class)) {
                responseThreadLocal.when(ResponseThreadLocal::getStatusCode).thenReturn(404);
                requestFilter.doFilter(request, response, filterChain);
                assertLoggedStatus(requestLogger, 404);
            }
        }
    }

    @Test
    void testAccessClientHttpExceptionHandling() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            doAnswer(invocation -> {
                throw new AccessClientHttpException(403, "Access denied");
            }).when(qrlService).wrapRequestWithLowPriority(any(), any());
            try {
                requestFilter.doFilter(request, response, filterChain);
                fail();
            } catch (AccessClientHttpException e) {
                assertEquals(403, e.getStatusCode());
                assertLoggedStatus(requestLogger, 403);
            }
        }
    }

    @Test
    void requestFilter_shouldRecordMetrics_whenAccessClientHttpExceptionThrown() throws Exception {
        // Arrange
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(1L)    // Time of the beginning of the requestFilter.doFilter()
                .thenReturn(50L)   // Time when recordStatusForMetrics is called (in catch block)
                .thenReturn(100L); // Time of the end of the request processing
        requestFilter.setCurrentTimeSupplier(currentTimeSupplier);
        when(request.getMethod()).thenReturn("GET");

        doAnswer(invocation -> {
            throw new AccessClientHttpException(403, "Access denied");
        }).when(qrlService).wrapRequestWithLowPriority(any(), any());

        // Act
        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            try {
                requestFilter.doFilter(request, response, filterChain);
                fail("Expected AccessClientHttpException to be thrown");
            } catch (AccessClientHttpException e) {
                // Expected
            }
        }

        // Assert - verify metrics are recorded with correct status and timing
        Tags expectedTags = Tags.of("method", "GET", "status", "403", "package", "", "group", "");

        Timer timerResolve = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(timerResolve, "'http.time.resolve' should be recorded even when exception is thrown");
        assertEquals(1L, timerResolve.count());
        assertEquals(49L, timerResolve.totalTime(TimeUnit.MILLISECONDS));

        Timer timerWrite = this.metrics.find("http.time.write").tags(expectedTags).timer();
        assertNotNull(timerWrite, "'http.time.write' should be recorded even when exception is thrown");
        assertEquals(1L, timerWrite.count());
        assertEquals(50L, timerWrite.totalTime(TimeUnit.MILLISECONDS));

        Timer timerTotal = this.metrics.find("http.time.total").tags(expectedTags).timer();
        assertNotNull(timerTotal, "'http.time.total' should be recorded");
        assertEquals(1L, timerTotal.count());
        assertEquals(99L, timerTotal.totalTime(TimeUnit.MILLISECONDS));
    }

    @Test
    void requestFilter_shouldRecordMetrics_whenGenericExceptionThrown() throws Exception {
        // Arrange
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(1L)    // Time of the beginning of the requestFilter.doFilter()
                .thenReturn(50L)   // Time when recordStatusForMetrics is called (in catch block)
                .thenReturn(100L); // Time of the end of the request processing
        requestFilter.setCurrentTimeSupplier(currentTimeSupplier);
        when(request.getMethod()).thenReturn("HEAD");

        doAnswer(invocation -> {
            throw new RuntimeException("Something went wrong");
        }).when(qrlService).wrapRequestWithLowPriority(any(), any());

        // Act
        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            try {
                requestFilter.doFilter(request, response, filterChain);
                fail("Expected RuntimeException to be thrown");
            } catch (RuntimeException e) {
                // Expected
            }
        }

        // Assert - verify metrics are recorded with 500 status (default for unhandled exceptions)
        Tags expectedTags = Tags.of("method", "HEAD", "status", "500", "package", "", "group", "");

        Timer timerResolve = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(timerResolve, "'http.time.resolve' should be recorded even when exception is thrown");
        assertEquals(1L, timerResolve.count());
        assertEquals(49L, timerResolve.totalTime(TimeUnit.MILLISECONDS));

        Timer timerWrite = this.metrics.find("http.time.write").tags(expectedTags).timer();
        assertNotNull(timerWrite, "'http.time.write' should be recorded even when exception is thrown");
        assertEquals(1L, timerWrite.count());
        assertEquals(50L, timerWrite.totalTime(TimeUnit.MILLISECONDS));

        Timer timerTotal = this.metrics.find("http.time.total").tags(expectedTags).timer();
        assertNotNull(timerTotal, "'http.time.total' should be recorded");
        assertEquals(1L, timerTotal.count());
        assertEquals(99L, timerTotal.totalTime(TimeUnit.MILLISECONDS));
    }

    @Test
    void requestFilter_shouldRecordMetricsFor5xx_whenTimeToSetStatusIsNull() throws Exception {
        // Simulates 5xx where response was committed before setStatus on wrapper, or status set only via
        // ResponseThreadLocal/underlying response. Ensures http_time_resolve_seconds_count includes these requests.
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(1L)   // Time of the beginning of the requestFilter.doFilter()
                .thenReturn(100L); // Time of the end of the request processing (in finally)
        requestFilter.setCurrentTimeSupplier(currentTimeSupplier);
        when(request.getMethod()).thenReturn("POST");

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                // Status only via ResponseThreadLocal; wrapper.setStatus() never called so timeToSetStatus stays null
                ResponseThreadLocal.setStatusCode(500);
            });
        }

        Tags expectedTags = Tags.of("method", "POST", "status", "500", "package", "", "group", "");
        Timer timerResolve = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(timerResolve,
                "'http.time.resolve' should be recorded for 5xx even when setStatus was not called on wrapper");
        assertEquals(1L, timerResolve.count());
        assertEquals(99L, timerResolve.totalTime(TimeUnit.MILLISECONDS));

        Timer timerWrite = this.metrics.find("http.time.write").tags(expectedTags).timer();
        assertNotNull(timerWrite,
                "'http.time.write' should be recorded for 5xx even when setStatus was not called on wrapper");
        assertEquals(1L, timerWrite.count());
        assertEquals(0L, timerWrite.totalTime(TimeUnit.MILLISECONDS));

        Timer timerTotal = this.metrics.find("http.time.total").tags(expectedTags).timer();
        assertNotNull(timerTotal, "'http.time.total' should be recorded");
        assertEquals(1L, timerTotal.count());
        assertEquals(99L, timerTotal.totalTime(TimeUnit.MILLISECONDS));
    }

    @Test
    void testLoadHealerExceptionHandling() throws Exception {
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            doAnswer(invocation -> {
                throw new LoadHealerException("Request blocked");
            }).when(qrlService).wrapRequestWithLowPriority(any(), any());
            PrintWriter printWriter = mock(PrintWriter.class);
            when(response.getWriter()).thenReturn(printWriter);
            try {
                requestFilter.doFilter(request, response, filterChain);
                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(printWriter).write(captor.capture());
                String writtenValue = captor.getValue();
                ObjectMapper objectMapper = new ObjectMapper();
                Object json = objectMapper.readValue(writtenValue, Object.class);
                String prettyPrintedJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                assertEquals("""
                        {
                          "errors" : [ {
                            "status" : 429,
                            "message" : "Request blocked"
                          } ]
                        }""", prettyPrintedJson);
            } catch (LoadHealerException e) {
                assertEquals("Request blocked", e.getMessage());
                assertLoggedStatus(requestLogger, 429);
            }
        }
    }

    @Test
    void requestFilter_shouldGenerateMetrics() throws ServletException, IOException {
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(1L)  // Time of the beginning of the requestFilter.doFilter()
                .thenReturn(21L)  // The time of the setStatus()
                .thenReturn(1021L); // Time of the end of the request processing (nested chain)
        requestFilter.setCurrentTimeSupplier(currentTimeSupplier);

        when(request.getMethod()).thenReturn("GET");
        requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));

        log.warn("Metrics: {}", this.metrics.getMetersAsString());
        List<String> actualMetricNames = this.metrics.getMeters().stream()
                .map(m -> m.getId().getName())
                .sorted()
                .toList();
        assertEquals(
                List.of("http.time.resolve", "http.time.resolve.external.wait", "http.time.total", "http.time.write"),
                actualMetricNames, "Unexpected metrics found");

        Tags expectedTags = Tags.of("method", "GET", "status", "200", "package", "", "group", "");
        Timer timerTotal = this.metrics.find("http.time.total").tags(expectedTags).timer();
        assertNotNull(timerTotal, "'http.time.total' is missing.");
        assertEquals(1L, timerTotal.count());
        assertEquals(1.02, timerTotal.totalTime(TimeUnit.SECONDS));
        assertEquals(1.02, timerTotal.max(TimeUnit.SECONDS));

        Timer timerResolve = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(timerResolve, "'http.time.resolve' is missing. Did we call response.setStatus()?");
        assertEquals(1L, timerResolve.count());
        assertEquals(0.02, timerResolve.totalTime(TimeUnit.SECONDS));
        assertEquals(0.02, timerResolve.max(TimeUnit.SECONDS));

        Timer timerWrite = this.metrics.find("http.time.write").tags(expectedTags).timer();
        assertNotNull(timerWrite, "'http.time.write' is missing. Did we call response.setStatus()?");
        assertEquals(1L, timerWrite.count());
        assertEquals(1.0, timerWrite.totalTime(TimeUnit.SECONDS));
        assertEquals(1.0, timerWrite.max(TimeUnit.SECONDS));
    }

    @Test
    void requestFilter_shouldUseCurrentActivityInMetrics() throws ServletException, IOException {
        when(request.getAttribute(ServletUtils.ATTR_REQUEST_START_TIME)).thenReturn(9L);

        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(10L)  // Time of the beginning of the requestFilter.doFilter()
                .thenReturn(11L)  // The time of the setStatus()
                .thenReturn(100L); // Time of the end of the request processing (nested chain)
        requestFilter.setCurrentTimeSupplier(currentTimeSupplier);

        when(request.getMethod()).thenReturn("PUT");
        try (var ignored = CurrentActivity.newScope()) {
            CurrentActivity.context().putPackageType("npm").putGroup("upload");
            requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(201));
        }

        List<String> actualMetricNames = this.metrics.getMeters().stream()
                .map(m -> m.getId().getName())
                .sorted()
                .toList();
        assertEquals(
                List.of("http.time.resolve", "http.time.resolve.external.wait", "http.time.to_start", "http.time.total",
                        "http.time.write"),
                actualMetricNames, "Unexpected metrics found");

        Tags expectedTags = Tags.of("method", "PUT", "status", "201", "package", "npm", "group", "upload");

        // Total time: 100 - 10 = 90 msec
        Timer timerTotal = this.metrics.find("http.time.total").tags(expectedTags).timer();
        assertNotNull(timerTotal, "'http.time.total' is missing.");
        assertEquals(1L, timerTotal.count());
        assertEquals(0.09, timerTotal.totalTime(TimeUnit.SECONDS));
        assertEquals(0.09, timerTotal.max(TimeUnit.SECONDS));

        // Resolve time: 11 - 10 = 1 msec
        Timer timerResolve = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(timerResolve, "'http.time.resolve' is missing. Did we call response.setStatus()?");
        assertEquals(1L, timerResolve.count());
        assertEquals(0.001, timerResolve.totalTime(TimeUnit.SECONDS));
        assertEquals(0.001, timerResolve.max(TimeUnit.SECONDS));

        // Streaming time: 100 - 11 = 89 msec
        Timer timerWrite = this.metrics.find("http.time.write").tags(expectedTags).timer();
        assertNotNull(timerWrite, "'http.time.write' is missing. Did we call response.setStatus()?");
        assertEquals(1L, timerWrite.count());
        assertEquals(0.089, timerWrite.totalTime(TimeUnit.SECONDS));
        assertEquals(0.089, timerWrite.max(TimeUnit.SECONDS));

        // Time to start (see request.getAttribute(ServletUtils.ATTR_REQUEST_START_TIME)): 10 - 9 = 1 msec
        Timer timeToStart = this.metrics.find("http.time.to_start").tags(expectedTags).timer();
        assertNotNull(timeToStart, "'http.time.to_start' is missing.");
        assertEquals(1L, timeToStart.count());
        assertEquals(0.001, timeToStart.totalTime(TimeUnit.SECONDS));
        assertEquals(0.001, timeToStart.max(TimeUnit.SECONDS));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 2gb+",
            "500, 0-200kb",
            "204879, 200kb-1mb",
            "1048577, 1mb-10mb",
            "1073741824, 100mb-1gb",
            "1073741825, 1gb-2gb",
            "-500, unknown"
    })
    void testReportRequestsWithExpectHeaders(long contentLength, String sizeCategory)
            throws ServletException, IOException {
        when(request.getHeader("Expect")).thenReturn("100-continue");
        when(request.getMethod()).thenReturn("POST");
        try (MockedStatic<HttpUtils> httpUtilsMockedStatic = Mockito.mockStatic(HttpUtils.class)) {
            httpUtilsMockedStatic.when(() -> HttpUtils.getContentLength(request)).thenReturn(contentLength);
            requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(201));
        }
        List<String> actualMetricNames = this.metrics.getMeters().stream()
                .map(m -> m.getId().getName())
                .sorted()
                .toList();
        assertTrue(actualMetricNames.containsAll(
                        List.of("http.requests.with.expect.header", "http.requests.with.expect.header.size")),
                "Expected metrics not found");
        Tags expectedTags = Tags.of("status", "201", "method", "POST");
        Counter counter = metrics.find("http.requests.with.expect.header").tags(expectedTags).counter();
        assertEquals(1.0, counter.count());
        expectedTags.and("size", sizeCategory);
        counter = metrics.find("http.requests.with.expect.header.size").tags(expectedTags).counter();
        assertEquals(sizeCategory, counter.getId().getTag("size"));
        assertEquals("201", counter.getId().getTag("status"));
        assertEquals("POST", counter.getId().getTag("method"));
        assertEquals(1.0, counter.count());
    }

    @Test
    void testReportRequestsWithExpectHeadersNoExpectHeader() throws ServletException, IOException {
        when(request.getHeader("Expect")).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");
        requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(404));
        Tags expectedTags = Tags.of("status", "404", "method", "GET", "size", "0-200kb");
        Counter counter = metrics.find("http.requests.with.expect.header").tags(expectedTags).counter();
        assertNull(counter);
        counter = metrics.find("http.requests.with.expect.header.size").tags(expectedTags).counter();
        assertNull(counter);
    }

    @Test
    void testReportRequestsWithExpectHeadersWithoutSize() throws ServletException, IOException {
        when(request.getHeader("Expect")).thenReturn("100-continue");
        when(request.getMethod()).thenReturn("PUT");
        requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(401));
        List<String> actualMetricNames = this.metrics.getMeters().stream()
                .map(m -> m.getId().getName())
                .sorted()
                .toList();
        assertTrue(actualMetricNames.contains("http.requests.with.expect.header"), "Expected metrics not found");
        assertFalse(actualMetricNames.contains("http.requests.with.expect.header.size"));
    }

    @Test
    void doFilter_shouldAddXRequestIdToLog() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("123e4567-e89b-12d3-a456-426614174000");
        Map<String, Object> metadata;

        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, filterChain);
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertEquals("123e4567-e89b-12d3-a456-426614174000", metadata.get(RequestExtraFields.EXTERNAL_ID));
    }

    @Test
    void doFilter_shouldPopulateRepoDetails_whenServletPathContainsRepoKeyAndRepoExists()
            throws ServletException, IOException {
        // Arrange - servlet path with repo key; context resolves repo as local
        String servletPathWithRepo = "/my-repo/path/to/artifact.zip";
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("my-repo")).thenReturn(mock());
        when(artifactoryContext.getRepositoryConfigService()).thenReturn(repositoryConfigService);

        try (MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any(), anyBoolean()))
                    .thenReturn(servletPathWithRepo);
            requestUtilsMock.when(() -> RequestUtils.extractRepoKey(servletPathWithRepo)).thenReturn("my-repo");
            requestUtilsMock.when(() -> RequestUtils.getArtifactoryContext(any(ServletContext.class)))
                    .thenReturn(artifactoryContext);
            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);

            // Act
            try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
                requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
            }

            // Assert - repo key and type set on CurrentActivity (for request log JSON)
            assertEquals("my-repo", CurrentActivity.context().getRepoKey());
            assertEquals("local", CurrentActivity.context().getRepoType());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "my-local, local",
            "my-fed, local",
            "my-release-bundle, local",
            "my-remote, remote",
            "my-virtual, virtual"
    })
    void doFilter_shouldPopulateRepoDetails_whenRepoIsLocalFederatedReleaseBundleRemoteOrVirtual(String repoKey,
            String expectedRepoType) throws ServletException, IOException {
        // Arrange - servlet path with repo key; context resolves repo by type
        String servletPathWithRepo = "/" + repoKey + "/path/to/artifact";
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        switch (expectedRepoType) {
            case "local" -> when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey(repoKey))
                    .thenReturn(mock());
            case "remote" -> when(repositoryConfigService.getRemoteRepoConfigByKey(repoKey)).thenReturn(mock());
            case "virtual" -> when(repositoryConfigService.getVirtualRepoConfigByKey(repoKey)).thenReturn(mock());
            default -> throw new IllegalArgumentException("Unexpected repo type: " + expectedRepoType);
        }
        when(artifactoryContext.getRepositoryConfigService()).thenReturn(repositoryConfigService);

        try (MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any(), anyBoolean()))
                    .thenReturn(servletPathWithRepo);
            requestUtilsMock.when(() -> RequestUtils.extractRepoKey(servletPathWithRepo)).thenReturn(repoKey);
            requestUtilsMock.when(() -> RequestUtils.getArtifactoryContext(any(ServletContext.class)))
                    .thenReturn(artifactoryContext);
            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);

            // Act
            try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
                requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
            }

            // Assert - repo key and type set on CurrentActivity (for request log JSON)
            assertEquals(repoKey, CurrentActivity.context().getRepoKey());
            assertEquals(expectedRepoType, CurrentActivity.context().getRepoType());
        }
    }

    @Test
    void doFilter_shouldNotPopulateRepoDetails_whenRepoDoesNotExist() throws ServletException, IOException {
        // Arrange - extractRepoKey returns a key but no config exists for it
        String servletPathWithRepo = "/nonexistent-repo/path/to/artifact";
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("nonexistent-repo")).thenReturn(null);
        when(repositoryConfigService.getRemoteRepoConfigByKey("nonexistent-repo")).thenReturn(null);
        when(repositoryConfigService.getVirtualRepoConfigByKey("nonexistent-repo")).thenReturn(null);
        when(artifactoryContext.getRepositoryConfigService()).thenReturn(repositoryConfigService);

        try (MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any(), anyBoolean()))
                    .thenReturn(servletPathWithRepo);
            requestUtilsMock.when(() -> RequestUtils.extractRepoKey(servletPathWithRepo))
                    .thenReturn("nonexistent-repo");
            requestUtilsMock.when(() -> RequestUtils.getArtifactoryContext(any(ServletContext.class)))
                    .thenReturn(artifactoryContext);
            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);

            // Act
            try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
                requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
            }

            // Assert - repo details not set when repo does not exist
            assertNull(CurrentActivity.context().getRepoKey());
            assertNull(CurrentActivity.context().getRepoType());
        }
    }

    @Test
    void doFilter_shouldNotPopulateRepoDetails_whenExceptionOccursInPopulateRepositoryInfo()
            throws ServletException, IOException {
        // Arrange - getRepoType throws (e.g. repo service fails)
        String servletPathWithRepo = "/my-repo/path/to/artifact";
        RepositoryConfigService repositoryConfigService = mock(RepositoryConfigService.class);
        when(repositoryConfigService.getLocalOrFederatedRepoConfigByKey("my-repo"))
                .thenThrow(new RuntimeException("Repository config unavailable"));
        when(artifactoryContext.getRepositoryConfigService()).thenReturn(repositoryConfigService);

        try (MockedStatic<RequestUtils> requestUtilsMock = mockStatic(RequestUtils.class);
                MockedStatic<ContextHelper> contextHelperMock = mockStatic(ContextHelper.class)) {
            requestUtilsMock.when(() -> RequestUtils.getServletPathFromRequest(any(), anyBoolean()))
                    .thenReturn(servletPathWithRepo);
            requestUtilsMock.when(() -> RequestUtils.extractRepoKey(servletPathWithRepo)).thenReturn("my-repo");
            requestUtilsMock.when(() -> RequestUtils.getArtifactoryContext(any(ServletContext.class)))
                    .thenReturn(artifactoryContext);
            contextHelperMock.when(ContextHelper::get).thenReturn(artifactoryContext);

            // Act - filter should complete without throwing (exception is caught and logged)
            try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
                requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
            }

            // Assert - repo details not set when exception occurs
            assertNull(CurrentActivity.context().getRepoKey());
            assertNull(CurrentActivity.context().getRepoType());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void doFilter_shouldLogTimeToFirstByte(boolean enabled) throws ServletException, IOException {
        Map<String, Object> metadata;
        when(request.getAttribute(ServletUtils.ATTR_REQUEST_START_TIME)).thenReturn(0);

        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(5L, 137L, 150L);
        requestFilter.setCurrentTimeSupplier(currentTimeSupplier);

        getBound().setProperty(
                ConstantValues.requestLogsExtraDetailsEnabled.getPropertyName(),
                Boolean.toString(enabled));

        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                ((HttpServletResponse)res).setStatus(200);
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        if (enabled) {
            assertEquals(132L, metadata.get(RequestExtraFields.TIME_TO_FIRST_BYTE));
        } else {
            assertFalse(metadata.containsKey(RequestExtraFields.TIME_TO_FIRST_BYTE),
                    "Expected not to log TTFB when feature is disabled");
        }
    }

    @CsvSource({
            // Range, hasBinarySize, range offset, range size
            "bytes=100-149, true, 100, 50",
            "bytes=-300, true, 700, 300",
            "bytes=600-, true, 600, 400",
            "bytes=100-149, false, 100, 50",
            "bytes=-300, false,, 300",
            "bytes=600-, false, 600,",
    })
    @SneakyThrows
    @ParameterizedTest
    void doFilter_shouldLogByteRangeRequestDetails(String range, boolean setBinarySize, Long offset, Long rangeSize) {
        Map<String, Object> metadata;
        DownloadPerfCounters counters = new DownloadPerfCounters();

        when(request.getHeader("Range")).thenReturn(range);
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);

        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                if (setBinarySize) {
                    counters.setArtifactSizeBytes(1000L);
                }
                if (rangeSize != null) {
                    res.setContentLength(rangeSize.intValue());
                }
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");

        // Artifact size (should be logged because we "streamed" less than the whole file)
        if (setBinarySize) {
            assertEquals(1000L, metadata.get(RequestExtraFields.ARTIFACT_SIZE),"Expected to log artifact size");
        } else {
            assertFalse(metadata.containsKey(RequestExtraFields.ARTIFACT_SIZE),"Expected not to log artifact size");
        }

        // Byte range details
        assertEquals(1L, metadata.get(RequestExtraFields.RANGES_COUNT), "Wrong number of byte ranges");
        if (offset != null) {
            assertEquals(offset, metadata.get(RequestExtraFields.RANGES_OFFSET), "Wrong range offset");
        } else {
            assertFalse(metadata.containsKey(RequestExtraFields.RANGES_OFFSET), "Expected not to log range offset");
        }
        if (rangeSize != null) {
            assertEquals(rangeSize, metadata.get(RequestExtraFields.RANGES_SIZE), "Wrong range size");
        } else {
            assertFalse(metadata.containsKey(RequestExtraFields.RANGES_SIZE), "Expected not to log range size");
        }
    }

    @CsvSource({
            // Status, Content Size, Should Report Size
            "200, 1000, false", // Returned the whole file, status is not error => no size reported
            "200, 999, true",   // Only part of the file was streamed => log artifact size
            "429, 1000, true",  // Returned the whole file, but status is error => log artifact size
    })
    @SneakyThrows
    @ParameterizedTest
    void doFilter_errorShouldReportArtifactSize(int status, int contentSize, boolean shouldReportSize)  {
        RequestEntry requestLogEntry;
        Map<String, Object> metadata;
        DownloadPerfCounters counters = new DownloadPerfCounters();

        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);

        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                counters.setArtifactSizeBytes(1000L);
                res.setContentLength(contentSize);
                ((HttpServletResponse)res).setStatus(status);
            });
            requestLogEntry = getRequestLogEntry(requestLogger);
        }

        assertEquals(status, requestLogEntry.getReturnCode(), "Unexpected status code");
        assertEquals(contentSize, requestLogEntry.getResContentLength(), "Unexpected response length");

        metadata = requestLogEntry.getMetadata();
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        if (shouldReportSize) {
            assertEquals(1000L, metadata.get(RequestExtraFields.ARTIFACT_SIZE), "Expected to log artifact size");
        } else {
            assertFalse(metadata.containsKey(RequestExtraFields.ARTIFACT_SIZE), "Expected not to log artifact size");
        }
    }

    @Test
    void doFilter_shouldLogRemoteDownloadTime() throws ServletException, IOException {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);

        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                counters.addRemoteDownloadTime(175L); // Simulate remote download time of 132 ms
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertEquals(175L, metadata.get(RequestExtraFields.REMOTE_DOWNLOAD_TIME));
    }

    @Test
    void doFilter_shouldLogExternalWaitTime_whenFeatureEnabled() throws ServletException, IOException {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);

        getBound().setProperty(
                ConstantValues.requestLogsWaitTimeDetailsEnabled.getPropertyName(),
                "true");

        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                counters.addExternalWaitTime("xray", 100L);
                counters.addExternalWaitTime("jfrog", 200L);
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertNotNull(metadata.get(RequestExtraFields.WAITED_TIME));
        Map<String, Long> waitedTime = (Map<String, Long>) metadata.get(RequestExtraFields.WAITED_TIME);
        assertEquals(2, waitedTime.size());
        assertEquals(100L, waitedTime.get("xray"));
        assertEquals(200L, waitedTime.get("jfrog"));
    }

    @Test
    void doFilter_shouldNotLogExternalWaitTime_whenFeatureDisabled() throws ServletException, IOException {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);

        getBound().setProperty(
                ConstantValues.requestLogsWaitTimeDetailsEnabled.getPropertyName(),
                "false");

        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                counters.addExternalWaitTime("xray", 100L);
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertFalse(metadata.containsKey(RequestExtraFields.WAITED_TIME),
                "Expected not to log external wait time when feature is disabled");
    }

    @Test
    void doFilter_shouldNotLogExternalWaitTime_whenExternalWaitTimeMsecIsNull() throws ServletException, IOException {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);

        getBound().setProperty(
                ConstantValues.requestLogsWaitTimeDetailsEnabled.getPropertyName(),
                "true");

        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                // Do not add any external wait time, so externalWaitTimeMsec remains null
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertFalse(metadata.containsKey(RequestExtraFields.WAITED_TIME),
                "Expected not to log external wait time when externalWaitTimeMsec is null");
    }

    @Test
    void doFilter_shouldNotLogExternalWaitTime_whenDownloadPerfCountersIsNull() throws ServletException, IOException {
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(null);

        getBound().setProperty(
                ConstantValues.requestLogsWaitTimeDetailsEnabled.getPropertyName(),
                "true");

        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                // DownloadPerfCounters is null
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertFalse(metadata.containsKey(RequestExtraFields.WAITED_TIME),
                "Expected not to log external wait time when DownloadPerfCounters is null");
    }

    @Test
    void requestFilter_shouldRecordExternalWaitMetricAsZero_whenDownloadPerfCountersIsNull()
            throws ServletException, IOException {
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(null);
        when(request.getMethod()).thenReturn("GET");

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
        }

        Tags expectedTags = Tags.of("method", "GET", "status", "200", "package", "", "group", "");
        Timer externalWaitTimer =
                this.metrics.find("http.time.resolve.external.wait").tags(expectedTags).timer();
        Timer resolveTimer = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(externalWaitTimer,
                "'http.time.resolve.external.wait' should be recorded even when DownloadPerfCounters is null");
        assertNotNull(resolveTimer, "'http.time.resolve' should be recorded");
        assertEquals(1L, resolveTimer.count(), "'http.time.resolve' should record exactly one sample per request");
        assertEquals(resolveTimer.count(), externalWaitTimer.count(),
                "Metric must stay aligned 1:1 with http.time.resolve (one sample per request)");
        assertEquals(0L, externalWaitTimer.totalTime(TimeUnit.MILLISECONDS),
                "Zero wait must be recorded when DownloadPerfCounters is null");
    }

    @Test
    void requestFilter_shouldRecordExternalWaitMetricAsZero_whenNoExternalWaitWasRecorded()
            throws ServletException, IOException {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);
        when(request.getMethod()).thenReturn("GET");

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> ((HttpServletResponse) res).setStatus(200));
        }

        Tags expectedTags = Tags.of("method", "GET", "status", "200", "package", "", "group", "");
        Timer externalWaitTimer =
                this.metrics.find("http.time.resolve.external.wait").tags(expectedTags).timer();
        Timer resolveTimer = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(externalWaitTimer, "'http.time.resolve.external.wait' should be recorded");
        assertNotNull(resolveTimer, "'http.time.resolve' should be recorded");
        assertEquals(1L, resolveTimer.count(), "'http.time.resolve' should record exactly one sample per request");
        assertEquals(resolveTimer.count(), externalWaitTimer.count(),
                "Metric must stay aligned 1:1 with http.time.resolve (one sample per request)");
        assertEquals(0L, externalWaitTimer.totalTime(TimeUnit.MILLISECONDS),
                "Zero wait must be recorded when no external service was waited on");
    }

    @Test
    void requestFilter_shouldRecordExternalWaitMetric_asSumAcrossAllExternalServices()
            throws ServletException, IOException {
        DownloadPerfCounters counters = new DownloadPerfCounters();
        when(request.getAttribute(DownloadPerfCounters.class.getName())).thenReturn(counters);
        when(request.getMethod()).thenReturn("GET");

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                counters.addExternalWaitTime("xray", 700L);
                counters.addExternalWaitTime("remote", 250L);
                ((HttpServletResponse) res).setStatus(200);
            });
        }

        Tags expectedTags = Tags.of("method", "GET", "status", "200", "package", "", "group", "");
        Timer externalWaitTimer =
                this.metrics.find("http.time.resolve.external.wait").tags(expectedTags).timer();
        Timer resolveTimer = this.metrics.find("http.time.resolve").tags(expectedTags).timer();
        assertNotNull(externalWaitTimer, "'http.time.resolve.external.wait' should be recorded");
        assertNotNull(resolveTimer, "'http.time.resolve' should be recorded");
        assertEquals(1L, resolveTimer.count(), "'http.time.resolve' should record exactly one sample per request");
        assertEquals(resolveTimer.count(), externalWaitTimer.count(),
                "Exactly one sample per request, regardless of how many external calls happened");
        assertEquals(950L, externalWaitTimer.totalTime(TimeUnit.MILLISECONDS),
                "Recorded value must equal the sum of all external service wait times for this request");
    }

    @Test
    void doFilter_shouldIgnoreRequestIdHeader_whenFeatureDisabled() throws ServletException, IOException {
        when(request.getHeader("X-Request-ID")).thenReturn("123e4567-e89b-12d3-a456-426614174000");
        getBound().setProperty(ConstantValues.requestLogsExternalRequestIdEnabled.getPropertyName(), "false");
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestLogger.when(() -> RequestLogger.request(any()))
                    .thenAnswer(invocation -> {
                        // Assert that MDC contains the expected xid value at the time of logging
                        assertNull(MDC.get("xid"), "MDC should NOT contain the 'xid' when feature is disabled");
                        return null;
                    });
            requestFilter.doFilter(request, response, filterChain);
            assertLoggedStatus(requestLogger, 200);
        }
    }

    @Test
    void doFilter_shouldLogRedirectDetails() throws ServletException, IOException {
        RedirectInfo redirectInfo = new RedirectInfo();
        when(request.getAttribute(RedirectInfo.class.getName())).thenReturn(redirectInfo);

        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {
                redirectInfo.setRedirectTarget(RedirectType.GCS.name());
            });
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata, "The RequestEntry supposed to have metadata attached");
        assertEquals(RedirectType.GCS.name(), metadata.get(RequestExtraFields.REDIRECT_TARGET));
    }

    @Test
    void doFilter_shouldLogAqlQueryChecksum_whenTemplatePresent() throws ServletException, IOException {
        when(request.getAttribute(AqlQueryTemplate.REQ_ATTR_KEY))
                .thenReturn(new AqlQueryTemplate("items.find()", "abc123"));
        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {});
            metadata = getMetadata(requestLogger);
        }
        assertNotNull(metadata);
        assertEquals("abc123", metadata.get(RequestExtraFields.AQL_QUERY_CHECKSUM));
    }

    @Test
    void doFilter_shouldNotLogAqlQueryChecksum_whenNoTemplateAttribute() throws ServletException, IOException {
        when(request.getAttribute(AqlQueryTemplate.REQ_ATTR_KEY)).thenReturn(null);
        Map<String, Object> metadata;
        try (MockedStatic<RequestLogger> requestLogger = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, (req, res) -> {});
            metadata = getMetadata(requestLogger);
        }
        if (metadata != null) {
            assertNull(metadata.get(RequestExtraFields.AQL_QUERY_CHECKSUM));
        }
    }

    private static RequestEntry getRequestLogEntry(MockedStatic<RequestLogger> requestLogger) {
        ArgumentCaptor<RequestEntry> argumentCaptor = ArgumentCaptor.forClass(RequestEntry.class);
        requestLogger.verify(() -> RequestLogger.request(argumentCaptor.capture()));
        return argumentCaptor.getValue();
    }

    private static void assertLoggedStatus(MockedStatic<RequestLogger> requestLogger, int expected) {
        assertEquals(expected, getRequestLogEntry(requestLogger).getReturnCode());
    }

    private static Map<String,Object> getMetadata(MockedStatic<RequestLogger> requestLogger) {
        return getRequestLogEntry(requestLogger).getMetadata();
    }

    @Test
    void doFilter_enqueuesInboundWhenServiceEnabled() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        RequestLogService requestLogService = mock(RequestLogService.class);
        when(requestLogService.isFeatureEnabled()).thenReturn(true);
        ReflectionTestUtils.setField(requestFilter, "requestLogService", requestLogService);

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, filterChain);
        }

        verify(requestLogService).enqueueInbound(any(RequestLogRow.class));
    }

    @Test
    void doFilter_noEnqueueWhenServiceDisabled() throws Exception {
        RequestLogService requestLogService = mock(RequestLogService.class);
        when(requestLogService.isFeatureEnabled()).thenReturn(false);
        ReflectionTestUtils.setField(requestFilter, "requestLogService", requestLogService);

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, filterChain);
        }

        verify(requestLogService, never()).enqueueInbound(any());
    }

    @Test
    void doFilter_noEnqueueWhenServiceNull() throws Exception {
        ReflectionTestUtils.setField(requestFilter, "requestLogService", null);

        try (MockedStatic<RequestLogger> ignored = mockStatic(RequestLogger.class)) {
            requestFilter.doFilter(request, response, filterChain);
        }
        // no NPE expected
    }
}
