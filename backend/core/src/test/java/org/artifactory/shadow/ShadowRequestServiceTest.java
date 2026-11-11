package org.artifactory.shadow;

import ch.qos.logback.classic.Level;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.http.DenyListCacheHolderService;
import org.artifactory.repo.http.IdleConnectionMonitorService;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.version.ArtifactoryServiceVersion;
import org.glassfish.jersey.server.ContainerRequest;
import org.jfrog.client.http.CloseableObserver;
import org.jfrog.metrics.model.Metric;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.net.HttpHeaders.STRICT_TRANSPORT_SECURITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class ShadowRequestServiceTest extends ArtifactoryHomeBoundJUnitTest {

    ShadowRequestService shadowRequestService;
    ShadowMetricRegistry shadowMetricRegistry;
    @Mock
    CloseableHttpClient httpClient;

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    MemoryAppender memoryAppender;


    private AutoCloseable closable;

    @BeforeEach
    void setUp() {
        closable = openMocks(this);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(ContextHelper.get().beanForType(IdleConnectionMonitorService.class)).thenReturn(
                mock(IdleConnectionMonitorServiceForTest.class));
        when(ContextHelper.get().beanForType(DenyListCacheHolderService.class)).thenReturn(
                mock(DenyListCacheHolderService.class));

        homeStub.setProperty(ConstantValues.artifactoryProductVersion,
                ArtifactoryServiceVersion.getCurrent().getVersion());

        shadowMetricRegistry = spy(new ShadowMetricRegistry());
        shadowRequestService = new ShadowRequestService(shadowMetricRegistry, httpClient);
        setupLoggingAssertions();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        closable.close();
    }

    @ParameterizedTest
    @MethodSource("testShadowUrlData")
    void testIsShadowPath(String requestUri) {
        ContainerRequest request = mock(ContainerRequest.class);
        when(request.getRequestUri()).thenReturn(URI.create(requestUri));
        ShadowRequestService.UrlPair urlPair = shadowRequestService.calculateRequestAndShadowedUrls(
                new ShadowRequestContext(null, null, requestUri, null, null, null));
        assertEquals(urlPair.requestUrl().substring(5), urlPair.shadowedUrl().substring(12));
    }

    @Test
    void collectMetrics() {
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests, Map.of("packageType", "npm", "result", "match"), 7L);
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "npm", "result", "error"), 3L);
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "npm", "result", "headers-diff"), 2L);
        shadowMetricRegistry.inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "npm", "result", "status-diff"), 1L);

        List<Metric> metricList = shadowRequestService.collectMetrics();
        assertEquals(4, metricList.size());
        for (Map.Entry<String, Long> resultToValue : Map.of("match", 7L, "error", 3L, "headers-diff", 2L,
                "status-diff", 1L).entrySet()) {
            List<Metric> metrics = metricList.stream()
                    .filter(metric -> metric.getLabels().get("result").toString().equals(resultToValue.getKey()))
                    .toList();
            Assertions.assertEquals(1, metrics.size(), "Failed with " + resultToValue.getKey() + " key");
            assertEquals(resultToValue.getValue(), metrics.get(0).getValue());
            assertEquals("npm", metrics.get(0).getLabels().get("packageType").toString());
            assertEquals("requests", metrics.get(0).getName());
        }
    }

    @Test
    void testHttpRequestCustomClass() {
        ShadowRequestService.HttpRequest httpRequest =
                new ShadowRequestService.HttpRequest("http://localhost:8080", "GET");

        assertEquals("http://localhost:8080", httpRequest.getURI().toString());
        assertEquals("GET", httpRequest.getMethod());

        httpRequest = new ShadowRequestService.HttpRequest(URI.create("http://localhost:8080"), "GET");

        assertEquals("http://localhost:8080", httpRequest.getURI().toString());
        assertEquals("GET", httpRequest.getMethod());

        ContainerRequestContext containerRequestContext = mock(ContainerRequestContext.class);
        UriInfo uriInfo = mock(UriInfo.class);
        when(containerRequestContext.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost:8080"));
        when(containerRequestContext.getMethod()).thenReturn("GET");
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Some-Header", "Some-Value");
        when(containerRequestContext.getHeaders()).thenReturn(headers);

        httpRequest = new ShadowRequestService.HttpRequest(containerRequestContext);

        assertEquals("http://localhost:8080", httpRequest.getURI().toString());
        assertEquals("GET", httpRequest.getMethod());

        httpRequest.copyHeaders(containerRequestContext.getHeaders());
        assertEquals("Some-Value", httpRequest.getFirstHeader("Some-Header").getValue());
        assertEquals("Some-Header", httpRequest.getAllHeaders()[0].getName());

        assertEquals("HttpRequest{method='GET', headers=[Some-Header: Some-Value]}", httpRequest.toString());
    }

    @Test
    void testProcessShadowCatchException() throws IOException {
        when(httpClient.execute(any())).thenThrow(new RuntimeException("exception"));
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());
        ShadowResponseContext responseContext = new ShadowResponseContext(200, new MultivaluedHashMap<>());
        Assertions.assertEquals(ShadowResponseType.FAILED,
                shadowRequestService.executeShadow(requestContext, responseContext));
        verify(shadowMetricRegistry, times(1)).inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "vcs", "result", "error"));
        assertMetricSize();
    }

    @Test
    void executeShadow_shouldReturnFailed_whenResponseContextIsNull() {
        // Arrange
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());

        // Act
        ShadowResponseType result = shadowRequestService.executeShadow(requestContext, null);

        // Assert
        Assertions.assertEquals(ShadowResponseType.FAILED, result);
        // Verify no metrics are updated when response is null (early return)
        verify(shadowMetricRegistry, never()).inc(any(), any());
    }

    @Test
    void testNonImplementedNotUpdatedMetrics() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(501);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any())).thenReturn(response);
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ShadowResponseContext responseContext = new ShadowResponseContext(200, new MultivaluedHashMap<>());
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());
        Assertions.assertEquals(ShadowResponseType.NOT_IMPLEMENTED,
                shadowRequestService.executeShadow(requestContext, responseContext));
        verify(shadowMetricRegistry, never()).inc(any(), any());
        assertEquals(1,
                memoryAppender.search("Shadowing for path: 'vcs/vcs-local' is not implemented yet.", Level.DEBUG)
                        .size());
    }

    @Test
    void testMetricUpdateOnlyOnceIfStatusIsDifferent() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(404);
        when(response.getAllHeaders()).thenReturn(new Header[] {new BasicHeader("Some-Header", "Some-Another-Value")});
        when(response.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any())).thenReturn(response);
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ShadowResponseContext responseContext = new ShadowResponseContext(200, new MultivaluedHashMap<>());
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());
        Assertions.assertEquals(ShadowResponseType.FAILED,
                shadowRequestService.executeShadow(requestContext, responseContext));
        verify(shadowMetricRegistry, times(1)).inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "vcs", "result", "status-diff"));
        verify(shadowMetricRegistry, never()).inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "vcs", "result", "headers-diff"));
        assertEquals(1, memoryAppender.search("Shadowing failed in status", Level.WARN).size());
        assertEquals(0, memoryAppender.search("Shadowing failed in headers", Level.WARN).size());
        assertMetricSize();
    }

    @Test
    void testMetricUpdateInSuccessfulComparison() throws IOException {
        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getAllHeaders()).thenReturn(new Header[] {});
        when(httpClient.execute(any())).thenReturn(response);
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        ShadowResponseContext responseContext = new ShadowResponseContext(200, new MultivaluedHashMap<>());
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());
        Assertions.assertEquals(ShadowResponseType.SUCCEEDED,
                shadowRequestService.executeShadow(requestContext, responseContext));
        verify(shadowMetricRegistry, times(1)).inc(ShadowMetric.shadowedRequests,
                Map.of("packageType", "vcs", "result", "match"));
        assertMetricSize();
    }

    @ParameterizedTest
    @MethodSource("testHeaderDiffData")
    void testHeaderDiff(String headerName, String legacyValue, String shadowValue, boolean isDifferent)
            throws IOException {
        CloseableHttpResponse shadowResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(404);
        when(shadowResponse.getAllHeaders()).thenReturn(new Header[] {new BasicHeader(headerName, shadowValue)});
        when(shadowResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any())).thenReturn(shadowResponse);
        URI uri = URI.create("http://localhost:8080/artifactory/api/vcs/vcs-local");
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.add(headerName, legacyValue);
        ShadowResponseContext responseContext = new ShadowResponseContext(404, headers);
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());
        ShadowResponseType res = shadowRequestService.executeShadow(requestContext, responseContext);
        if (isDifferent) {
            Assertions.assertEquals(ShadowResponseType.FAILED, res);
            verify(shadowMetricRegistry, times(1)).inc(ShadowMetric.shadowedRequests,
                    Map.of("packageType", "vcs", "result", "headers-diff"));
            assertEquals(1, memoryAppender.search("Shadowing failed in headers", Level.WARN).size());
            assertMetricSize();
        } else {
            Assertions.assertEquals(ShadowResponseType.SUCCEEDED, res);
            verify(shadowMetricRegistry, never()).inc(ShadowMetric.shadowedRequests,
                    Map.of("packageType", "vcs", "result", "headers-diff"));
            int logAppearanceCount =
                    headerName.equals(HttpHeaders.LOCATION) && !legacyValue.equals(shadowValue) ? 1 : 0;
            assertEquals(logAppearanceCount, memoryAppender.search(
                    "Shadowing location header is different in query parameters for path: 'vcs/vcs-local'.",
                    Level.DEBUG).size());
            assertEquals(0, memoryAppender.search("Shadowing failed in headers", Level.WARN).size());
        }
    }


    @ParameterizedTest
    @MethodSource("testHeaderToAggregate")
    void testAggregationDuplicateHeader(String headerName, int duplication, boolean isDifferent, String expectedValue,
            String... shadowValue) throws IOException {
        CloseableHttpResponse shadowResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(404);
        Header[] shadowHeaders = new Header[duplication];
        for (int i = 0; i < duplication; i++) {
            shadowHeaders[i] = new BasicHeader(headerName, shadowValue[i]);
        }
        when(shadowResponse.getAllHeaders()).thenReturn(shadowHeaders);
        when(shadowResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any())).thenReturn(shadowResponse);
        URI uri = URI.create("http://localhost:8080/artifactory/api/swift/swift-local");
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        if (expectedValue != null) {
            String[] expected = expectedValue.split(",");
            for (String value : expected) {
                headers.add(headerName, value);
            }
        }
        ShadowResponseContext responseContext = new ShadowResponseContext(404, headers);
        ShadowRequestContext requestContext =
                new ShadowRequestContext(null, "traceId",
                        uri.toString(), null, HttpMethod.GET, new MultivaluedHashMap<>());
        ShadowResponseType res = shadowRequestService.executeShadow(requestContext, responseContext);
        if (isDifferent) {
            Assertions.assertEquals(ShadowResponseType.FAILED, res);
            assertEquals(1, memoryAppender.search("Shadowing failed in headers", Level.WARN).size());
            assertMetricSize();
        } else {
            Assertions.assertEquals(ShadowResponseType.SUCCEEDED, res);
            verify(shadowMetricRegistry, never()).inc(ShadowMetric.shadowedRequests,
                    Map.of("packageType", "swift", "result", "headers-diff"));
            assertEquals(0, memoryAppender.search("Shadowing failed in headers", Level.WARN).size());
        }
    }

    @Test
    void testCleanup() throws IOException {
        doThrow(new IOException("test")).when(httpClient).close();
        Assertions.assertDoesNotThrow(shadowRequestService::cleanup);
        verify(httpClient, times(1)).close();
        ShadowRequestService shadowRequestServiceWithoutClient = new ShadowRequestService(shadowMetricRegistry, null);
        Assertions.assertDoesNotThrow(shadowRequestServiceWithoutClient::cleanup);
    }

    @ParameterizedTest
    @MethodSource("logsMessages")
    void testMessagesFollowExpectedFormat(String messageName, String message, Set<String> ignoreTokens) {
        String[] tokens = {"path:", "header:", "expected:", "actual:", "from:"};
        for (String token : tokens) {
            if (ignoreTokens.contains(token)) {
                continue;
            }
            assertTrue(message.contains(token), String.format("%s does not contain token: %s", messageName, token));
        }
    }

    private static Stream<Arguments> logsMessages() {
        return Stream.of(
                Arguments.of("HEADER_COMPARISON_IS_NULL_MSG", ShadowRequestService.HEADER_COMPARISON_IS_NULL_MSG,
                        Set.of()),
                Arguments.of("HEADER_COMPARISON_NON_EQUAL_MSG", ShadowRequestService.HEADER_COMPARISON_NON_EQUAL_MSG,
                        Set.of()),
                Arguments.of("RESPONSE_STATUS_COMPARISON_MSG", ShadowRequestService.RESPONSE_STATUS_COMPARISON_MSG,
                        Set.of("header:")),
                Arguments.of("LOCATION_HEADER_COMPARISON_MSG", ShadowRequestService.LOCATION_HEADER_COMPARISON_MSG,
                        Set.of("header:"))
        );
    }

    private void setupLoggingAssertions() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(ShadowRequestService.class, Level.DEBUG);
    }

    private void assertMetricSize() {
        List<Metric> metrics = shadowMetricRegistry.collectAndMap(List.of(ShadowMetric.shadowedRequests));
        Assertions.assertEquals(1, metrics.size());
    }

    private static Stream<Arguments> testHeaderDiffData() {
        return Stream.of(
                Arguments.of(HttpHeaders.LOCATION, "https://url?param=admin&param2=local",
                        "https://url?param=admin&param2=remote", false),
                Arguments.of(HttpHeaders.CONTENT_LOCATION, "https://url?param=admin&param2=local",
                        "https://url?param=admin&param2=remote", true),
                Arguments.of(HttpHeaders.LOCATION, "https://url?param=admin&param2=local",
                        "https://url2?param=admin&param2=local", true),
                Arguments.of(HttpHeaders.LOCATION, "https://url?param=admin&param2=local",
                        "https://url?param=admin&param2=local", false),
                Arguments.of(HttpHeaders.LOCATION, null,
                        "https://url?param=admin&param2=local", true),
                Arguments.of(HttpHeaders.LAST_MODIFIED, "value",
                        "value2", false),
                Arguments.of(STRICT_TRANSPORT_SECURITY, "value",
                        "value2", false)
        );
    }

    private static Stream<Arguments> testHeaderToAggregate() {

        String expected =
                "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; rel=\"latest-version\"," +
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                        "rel=\"successor-version\"," +
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1137.0>; " +
                        "rel=\"predecessor-version\"";

        return Stream.of(
                Arguments.of("Link", 3, false, expected, new String[] {
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"successor-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1137.0>; " +
                                "rel=\"predecessor-version\""
                }),
                Arguments.of("Link", 3, false, expected, new String[] {
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1137.0>; " +
                                "rel=\"predecessor-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"successor-version\""
                }),
                Arguments.of("Link", 3, true, expected, new String[] {
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1137.0>; " +
                                "rel=\"predecessor-version\""
                }),
                Arguments.of("Link", 2, true, expected, new String[] {
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"successor-version\""
                }),
                Arguments.of("Link", 2, true, null, new String[] {
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"successor-version\""
                }),
                Arguments.of("Link", 2, true, "", new String[] {
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"latest-version\"",
                        "<http://jfrog.com/artifactory/api/swift/swift-local/scope/pkg/7.1138.1>; " +
                                "rel=\"successor-version\""
                })
        );
    }

    private static Stream<Arguments> testShadowUrlData() {
        return Stream.of(
                Arguments.of("/api/npm/npm-local/@scope%2Fpackage"),
                Arguments.of("/api/npm/npm-local/@scope%2Fpackage/-/package-1.0.0.tgz"),
                Arguments.of("/api/npm/npm-local/@scope%2Fpackage/-/package-1.0.0.tgz/-rev/1-0"),
                Arguments.of("/api/npm/npm-local/@scope%2Fpackage/-/package-1.0.0.tgz/-rev/1-0/-tag/latest"),
                Arguments.of("/api/npm/siteminder-npm/api-schema-builder/-/api-schema-builder-2.0.11.tgz"),
                Arguments.of("/api/npm/siteminder-npm/api-schema-builder/-/api-schema-builder-2.0.11.tgz/-rev/1-0"),
                Arguments.of(
                        "/api/npm/siteminder-npm/api-schema-builder/-/api-schema-builder-2.0.11" +
                                ".tgz/-rev/1-0/-tag/latest")
        );
    }

    public interface IdleConnectionMonitorServiceForTest extends CloseableObserver, IdleConnectionMonitorService {
    }
}