package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.probes.ProbesService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Hussein Alshqerat
 */
class ArtifactoryProbesFilterTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String LIVENESS_URL = "/artifactory/api/v1/system/liveness";
    private static final String READINESS_URL = "/artifactory/api/v1/system/readiness";
    private static final String DEFAULT_TEST_URL = "/artifactory/api/v1/system/someUrl";
    private static final String REQUEST_BLOCKED_NOT_READY_RESPONSE = """
            {"code": "UNAVAILABLE", "message": "Request was blocked because Artifactory is not in a ready state"}""";
    private static final String FALLBACK_BLOCK_REQUESTS_ON_READINESS_PROPERTY_KEY =
            "artifactory.block.requests.until.ready";

    private ArtifactoryProbesFilter probesFilter;
    private ProbesService probesServiceMock;
    private AutoCloseable activityScope;
    private HttpServletRequest servletRequestMock;
    private HttpServletResponse servletResponseMock;
    private PrintWriter outputPrintWriterMock;
    private FilterChain filterChainMock;
    private FilterConfig filterConfigMock;

    @BeforeEach
    void setup() throws IOException, ServletException {
        probesServiceMock = mock(ProbesService.class);
        when(probesServiceMock.isReady()).thenReturn(true);
        ArtifactoryContext contextMock = mock(ArtifactoryContext.class);
        when(contextMock.beanForType(ProbesService.class)).thenReturn(probesServiceMock);
        probesFilter = new ArtifactoryProbesFilter();
        filterConfigMock = mock(FilterConfig.class);
        ServletContext servletContextMock = mock(ServletContext.class);
        when(filterConfigMock.getServletContext()).thenReturn(servletContextMock);
        when(servletContextMock.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(contextMock);
        ReflectionTestUtils.setField(probesFilter, "filterConfig", filterConfigMock);
        probesFilter.initLater(filterConfigMock);
        activityScope = CurrentActivity.newScope();
        servletRequestMock = mock(HttpServletRequest.class);
        servletResponseMock = mock(HttpServletResponse.class);
        outputPrintWriterMock = mock(PrintWriter.class);
        filterChainMock = mock(FilterChain.class);
        when(servletRequestMock.getRequestURI()).thenReturn(DEFAULT_TEST_URL);
        when(servletResponseMock.getWriter()).thenReturn(outputPrintWriterMock);
        homeStub.setProperty(ConstantValues.blockRequestsWhenApplicationNotReady.getPropertyName(), "true");
    }

    @AfterEach
    void cleanup() throws Exception {
        activityScope.close();
    }

    @Test
    void testDoFilterReadinessRequest() throws ServletException, IOException {
        when(servletRequestMock.getRequestURI()).thenReturn(READINESS_URL);
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(probesServiceMock).checkReadinessStatus(false);
        verifyNoInteractions(filterChainMock);
        assertEquals(RestApiGroup.PROBES_API, CurrentActivity.GROUP.get());
    }

    @Test
    void testSkipFilterOnFilterShortcutAttr() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        when(filterConfigMock.getFilterName()).thenReturn("ArtifactoryProbesFilter");
        when(servletRequestMock.getAttribute(DelayedInit.FILTER_SHORTCUT_ATTR)).thenReturn("someOtherFilterName");
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verifyFilterWasBypassed();
        when(servletRequestMock.getAttribute(DelayedInit.FILTER_SHORTCUT_ATTR)).thenReturn("ArtifactoryProbesFilter");
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(probesServiceMock).isReady();
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
        verifyNoMoreInteractions(filterChainMock);
    }

    @Test
    void testDoFilterLivenessRequest() throws ServletException, IOException {
        when(servletRequestMock.getRequestURI()).thenReturn(LIVENESS_URL);
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(probesServiceMock).checkLivenessStatus();
        verifyNoInteractions(filterChainMock);
        assertEquals(RestApiGroup.PROBES_API, CurrentActivity.GROUP.get());
    }

    @Test
    void testSuccessfulRequestWhenAppIsReady() throws ServletException, IOException {
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(probesServiceMock).isReady();
        verifyFilterWasBypassed();
    }

    @Test
    void testBypassBlockWhenNotReadyAndBearerServiceTokenSubject() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        when(servletRequestMock.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer s2s-token-value");
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getSubject()).thenReturn("jffs@platformsvc01");
        try (MockedStatic<JwtAccessTokenImpl> jwtAccessTokenMock = Mockito.mockStatic(JwtAccessTokenImpl.class)) {
            jwtAccessTokenMock.when(() -> JwtAccessTokenImpl.parseTokenValue("s2s-token-value"))
                    .thenReturn(jwtAccessToken);
            probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        }
        verify(probesServiceMock).isReady();
        verifyFilterWasBypassed();
    }

    @Test
    void testBlockWhenNotReadyAndBearerUserScopedServiceSubject() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        when(servletRequestMock.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer user-token-value");
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getSubject()).thenReturn("jfrt@svcinstance123/users/admin");
        try (MockedStatic<JwtAccessTokenImpl> jwtAccessTokenMock = Mockito.mockStatic(JwtAccessTokenImpl.class)) {
            jwtAccessTokenMock.when(() -> JwtAccessTokenImpl.parseTokenValue("user-token-value"))
                    .thenReturn(jwtAccessToken);
            probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        }
        verify(probesServiceMock).isReady();
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
        verifyNoInteractions(filterChainMock);
    }

    @Test
    void testBlockWhenNotReadyAndNoAuthorizationHeader() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(probesServiceMock).isReady();
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
        verifyNoInteractions(filterChainMock);
    }

    @Test
    void testBlockWhenNotReadyAndBasicAuth() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        when(servletRequestMock.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(probesServiceMock).isReady();
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
        verifyNoInteractions(filterChainMock);
    }

    @Test
    void testBlockWhenNotReadyAndBearerTokenParseFails() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        when(servletRequestMock.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad-token");
        try (MockedStatic<JwtAccessTokenImpl> jwtAccessTokenMock = Mockito.mockStatic(JwtAccessTokenImpl.class)) {
            jwtAccessTokenMock.when(() -> JwtAccessTokenImpl.parseTokenValue(anyString()))
                    .thenThrow(new IllegalArgumentException("cannot parse"));
            probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        }
        verify(probesServiceMock).isReady();
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
        verifyNoInteractions(filterChainMock);
    }

    @Test
    void testFeatureFlagOnAndOff() throws ServletException, IOException {
        when(probesServiceMock.isReady()).thenReturn(false);
        // set the feature flag to "false" and verify that the request is not blocked
        homeStub.setProperty(ConstantValues.blockRequestsWhenApplicationNotReady.getPropertyName(), "false");
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verifyFilterWasBypassed();
        // now change the feature flag to true and make sure the request is blocked
        homeStub.setProperty(ConstantValues.blockRequestsWhenApplicationNotReady.getPropertyName(), "true");
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verifyNoMoreInteractions(filterChainMock);
        verify(probesServiceMock).isReady();
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
    }

    @Test
    void testBlockRequestsWhenNotInitialized() throws ServletException, IOException {
        ReflectionTestUtils.setField(probesFilter, "probesService", null);
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
    }

    @ParameterizedTest
    @MethodSource("fallbackSystemPropertyTestDataProvider")
    void testFallbackSystemPropertyIsUsedWhenConstantValueNotLoaded(String propertyValue, boolean expectToBlockRequest)
            throws ServletException, IOException {
        // set up the mocks to simulate system not ready state
        when(probesServiceMock.isReady()).thenReturn(false);
        try (MockedStatic<ArtifactoryHome> artifactoryHomeMockedStatic = Mockito.mockStatic(ArtifactoryHome.class)) {
            // set up mock to throw exception when ConstantValues is read to imitate it's not yet initialized
            artifactoryHomeMockedStatic.when(ArtifactoryHome::get).thenThrow(new RuntimeException("Test"));
            if (StringUtils.isNotBlank(propertyValue)) {
                System.setProperty(FALLBACK_BLOCK_REQUESTS_ON_READINESS_PROPERTY_KEY, propertyValue);
            }
            probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
            if (expectToBlockRequest) {
                verify(outputPrintWriterMock).println(REQUEST_BLOCKED_NOT_READY_RESPONSE);
            } else {
                verifyFilterWasBypassed();
            }
        } finally {
            System.clearProperty(FALLBACK_BLOCK_REQUESTS_ON_READINESS_PROPERTY_KEY);
        }
    }

    @Test
    void testReadinessFails() throws IOException, ServletException {
        when(servletRequestMock.getRequestURI()).thenReturn(READINESS_URL);
        doThrow(new ServiceUnavailableException("Readiness failed")).when(probesServiceMock)
                .checkReadinessStatus(false);
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(outputPrintWriterMock).println("""
                {"code": "UNAVAILABLE", "message": "Readiness failed"}""");
    }

    @Test
    void testLivenessFails() throws IOException, ServletException {
        when(servletRequestMock.getRequestURI()).thenReturn(LIVENESS_URL);
        doThrow(new ServiceUnavailableException("Liveness failed")).when(probesServiceMock).checkLivenessStatus();
        probesFilter.doFilter(servletRequestMock, servletResponseMock, filterChainMock);
        verify(outputPrintWriterMock).println("""
                {"code": "UNAVAILABLE", "message": "Liveness failed"}""");
    }

    static List<Arguments> fallbackSystemPropertyTestDataProvider() {
        return List.of(Arguments.of(null, true),
                Arguments.of("true", true),
                Arguments.of("false", false)
        );
    }

    private void verifyFilterWasBypassed() throws ServletException, IOException {
        verify(probesServiceMock, times(0)).checkLivenessStatus();
        verify(probesServiceMock, times(0)).checkReadinessStatus(anyBoolean());
        verify(filterChainMock).doFilter(servletRequestMock, servletResponseMock);
        verifyNoInteractions(outputPrintWriterMock);
    }
}