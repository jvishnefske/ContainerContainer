package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.repo.RepoPath;
import org.artifactory.traffic.TrafficEntry;
import org.artifactory.traffic.TrafficService;
import org.artifactory.traffic.context.TrafficContextThreadLocal;
import org.artifactory.util.HttpUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class TrafficFilterTest {

    @Mock
    private FilterConfig filterConfig;
    @Mock
    private ServletContext servletContext;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private TrafficService trafficService;
    @Mock
    private FilterChain filterChain;
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private HttpServletResponse httpServletResponse;
    @Mock
    private RepoPath repoPath;

    private MockedStatic<RequestUtils> requestUtils;
    private MockedStatic<TrafficContextThreadLocal> trafficContext;
    private MockedStatic<HttpUtils> httpUtils;

    private TrafficFilter trafficFilter;

    @BeforeEach
    void setUp() {
        trafficFilter = new TrafficFilter();
        requestUtils = mockStatic(RequestUtils.class);
        trafficContext = mockStatic(TrafficContextThreadLocal.class);
        httpUtils = mockStatic(HttpUtils.class);
    }

    @AfterEach
    void tearDown() {
        requestUtils.close();
        trafficContext.close();
        httpUtils.close();
    }

    @Test
    void testInitLater() throws ServletException {
        // Given
        when(filterConfig.getServletContext()).thenReturn(servletContext);
        requestUtils.when(() -> RequestUtils.getArtifactoryContext(servletContext)).thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(TrafficService.class)).thenReturn(trafficService);

        // When
        trafficFilter.initLater(filterConfig);

        // Then
        verify(filterConfig).getServletContext();
        requestUtils.verify(() -> RequestUtils.getArtifactoryContext(servletContext));
        verify(artifactoryContext).beanForType(TrafficService.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullServletContext", "nullArtifactoryContext"})
    void testInitLaterWithNullDependencies(String scenario) {
        // Given
        when(filterConfig.getServletContext()).thenReturn(
                "nullServletContext".equals(scenario) ? null : servletContext);
        if (!"nullServletContext".equals(scenario)) {
            requestUtils.when(() -> RequestUtils.getArtifactoryContext(servletContext)).thenReturn(null);
        }

        // When & Then
        assertThrows(NullPointerException.class, () -> trafficFilter.initLater(filterConfig));
    }

    @Test
    void testDoFilterWithHeadMethodSkipsProcessing() throws IOException, ServletException {
        // Given
        initializeTrafficFilter();
        when(httpServletRequest.getMethod()).thenReturn(HEAD.name());

        // When
        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        trafficContext.verify(TrafficContextThreadLocal::bind, never());
        trafficContext.verify(TrafficContextThreadLocal::unbind, never());
        verify(trafficService, never()).handleTrafficEntryV3(any());
    }

    @Test
    void testDoFilterWithInactiveTrafficServiceSkipsProcessing() throws IOException, ServletException {
        // Given
        initializeTrafficFilter();
        when(httpServletRequest.getMethod()).thenReturn(GET.name());
        when(trafficService.isV3Active()).thenReturn(false);

        // When
        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(trafficService).isV3Active();
        verify(filterChain).doFilter(httpServletRequest, httpServletResponse);
        trafficContext.verify(TrafficContextThreadLocal::bind, never());
        trafficContext.verify(TrafficContextThreadLocal::unbind, never());
    }

    @Test
    void testDoFilterActiveTrafficServiceWithoutV3Flow() throws IOException, ServletException {
        // Given
        initializeTrafficFilter();
        when(trafficService.isV3Active()).thenReturn(true);
        when(httpServletRequest.getMethod()).thenReturn(GET.name());

        // When
        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(trafficService).isV3Active();
        trafficContext.verify(TrafficContextThreadLocal::bind);
        verify(filterChain).doFilter(eq(httpServletRequest), any(CapturingHttpServletResponseWrapper.class));
        trafficContext.verify(TrafficContextThreadLocal::unbind);
    }

    @Test
    void testDoFilterWithTrafficLogV3FlowCreatesEntry() throws IOException, ServletException {
        // Test with valid repoPath to ensure traffic entries are created
        initializeTrafficFilter();
        String pathInfo = "/artifactory/test-repo/path/file.jar";
        String remoteAddress = "192.168.1.100";

        when(trafficService.isV3Active()).thenReturn(true);
        when(httpServletRequest.getMethod()).thenReturn(GET.name());
        when(httpServletRequest.getPathInfo()).thenReturn(pathInfo);
        trafficContext.when(TrafficContextThreadLocal::isTrafficLogV3Flow).thenReturn(true);
        httpUtils.when(HttpUtils::getRemoteClientAddress).thenReturn(remoteAddress);

        // Test with valid repoPath - should create traffic entry
        when(repoPath.getId()).thenReturn("test-repo:path/file.jar");
        trafficContext.when(TrafficContextThreadLocal::getRepoPath).thenReturn(repoPath);

        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        ArgumentCaptor<TrafficEntry> trafficEntryCaptor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService).handleTrafficEntryV3(trafficEntryCaptor.capture());

        TrafficEntry capturedEntry = trafficEntryCaptor.getValue();
        assertEquals(TrafficEntry.TrafficAction.DOWNLOAD, capturedEntry.getAction());
        assertEquals(TrafficEntry.TrafficActor.USER, capturedEntry.getActor());
        assertEquals(TrafficEntry.TrafficTarget.DIRECT, capturedEntry.getRedirectTarget());
        assertEquals(remoteAddress, capturedEntry.getUserAddress());
        assertEquals("test-repo:path/file.jar", capturedEntry.getRepoPath());
        assertEquals(0L, capturedEntry.getSize());
        assertEquals(0L, capturedEntry.getRequestedSize());
        assertEquals(0L, capturedEntry.getTransferredSize());

        trafficContext.verify(TrafficContextThreadLocal::bind);
        trafficContext.verify(() -> TrafficContextThreadLocal.fillOriginFields("test-repo"));
        trafficContext.verify(TrafficContextThreadLocal::unbind);
    }

    @Test
    void testDoFilterWithCompleteTrafficEntryDetails() throws IOException, ServletException {
        // Given
        initializeTrafficFilter();
        String repoKey = "test-repo";
        String path = "com/example/artifact.jar";
        String pathInfo = "/artifactory/" + repoKey + "/" + path;
        String remoteAddress = "192.168.1.100";

        when(trafficService.isV3Active()).thenReturn(true);
        trafficContext.when(TrafficContextThreadLocal::isTrafficLogV3Flow).thenReturn(true);
        when(repoPath.getId()).thenReturn(repoKey + ":" + path);
        trafficContext.when(TrafficContextThreadLocal::getRepoPath).thenReturn(repoPath);
        when(httpServletRequest.getPathInfo()).thenReturn(pathInfo);
        when(httpServletRequest.getMethod()).thenReturn(GET.name());
        httpUtils.when(HttpUtils::getRemoteClientAddress).thenReturn(remoteAddress);

        // When
        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        ArgumentCaptor<TrafficEntry> trafficEntryCaptor = ArgumentCaptor.forClass(TrafficEntry.class);
        verify(trafficService).handleTrafficEntryV3(trafficEntryCaptor.capture());

        TrafficEntry capturedEntry = trafficEntryCaptor.getValue();
        assertEquals(TrafficEntry.TrafficAction.DOWNLOAD, capturedEntry.getAction());
        assertEquals(TrafficEntry.TrafficActor.USER, capturedEntry.getActor());
        assertEquals(TrafficEntry.TrafficTarget.DIRECT, capturedEntry.getRedirectTarget());
        assertEquals(remoteAddress, capturedEntry.getUserAddress());
        assertEquals(repoKey + ":" + path, capturedEntry.getRepoPath());
        assertEquals(0L, capturedEntry.getSize());
        assertEquals(0L, capturedEntry.getRequestedSize());
        assertEquals(0L, capturedEntry.getTransferredSize());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "/short", "httpUtilsException", "filterChainException"})
    void testDoFilterExceptionScenarios(String scenario) throws IOException, ServletException {
        // Given
        initializeTrafficFilter();
        when(trafficService.isV3Active()).thenReturn(true);
        when(httpServletRequest.getMethod()).thenReturn(GET.name());
        trafficContext.when(TrafficContextThreadLocal::isTrafficLogV3Flow).thenReturn(true);
        trafficContext.when(TrafficContextThreadLocal::getRepoPath).thenReturn(repoPath);

        switch (scenario) {
            case "null" -> {
                when(httpServletRequest.getPathInfo()).thenReturn(null);
                assertThrows(NullPointerException.class, () ->
                        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain));
                trafficContext.verify(() -> TrafficContextThreadLocal.fillOriginFields(any()), never());
            }
            case "/short" -> {
                when(httpServletRequest.getPathInfo()).thenReturn("/short");
                assertThrows(ArrayIndexOutOfBoundsException.class, () ->
                        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain));
            }
            case "httpUtilsException" -> {
                when(httpServletRequest.getPathInfo()).thenReturn("/artifactory/test-repo/path");
                httpUtils.when(HttpUtils::getRemoteClientAddress)
                        .thenThrow(new RuntimeException("HttpUtils exception"));
                assertThrows(RuntimeException.class, () ->
                        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain));
                trafficContext.verify(() -> TrafficContextThreadLocal.fillOriginFields("test-repo"));
            }
            case "filterChainException" -> {
                doThrow(new RuntimeException("Test exception")).when(filterChain)
                        .doFilter(eq(httpServletRequest), any(CapturingHttpServletResponseWrapper.class));
                assertThrows(RuntimeException.class, () ->
                        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain));
            }
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }

        // Common verifications for all exception scenarios
        verify(trafficService, never()).handleTrafficEntryV3(any());
        // With the fixed implementation, unbind() is always called due to nested try-finally
        trafficContext.verify(TrafficContextThreadLocal::unbind);
    }

    @Test
    void testWrapResponseHandling() throws IOException, ServletException {
        // Test both new wrapper creation and existing wrapper reuse
        initializeTrafficFilter();
        when(trafficService.isV3Active()).thenReturn(true);
        when(httpServletRequest.getMethod()).thenReturn(GET.name());

        // Test with existing CapturingHttpServletResponseWrapper
        CapturingHttpServletResponseWrapper existingWrapper =
                new CapturingHttpServletResponseWrapper(httpServletResponse, System::currentTimeMillis);

        trafficFilter.doFilter(httpServletRequest, existingWrapper, filterChain);

        verify(filterChain).doFilter(httpServletRequest, existingWrapper);
        trafficContext.verify(TrafficContextThreadLocal::bind);
        trafficContext.verify(TrafficContextThreadLocal::unbind);
    }

    @Test
    void testDoFilterWithNonHttpServletRequest() {
        // Given
        initializeTrafficFilter();
        ServletRequest nonHttpRequest = mock(ServletRequest.class);
        ServletResponse nonHttpResponse = mock(ServletResponse.class);

        // When & Then
        assertThrows(ClassCastException.class, () ->
                trafficFilter.doFilter(nonHttpRequest, nonHttpResponse, filterChain));
    }

    private void initializeTrafficFilter() {
        try {
            when(filterConfig.getServletContext()).thenReturn(servletContext);
            requestUtils.when(() -> RequestUtils.getArtifactoryContext(servletContext)).thenReturn(artifactoryContext);
            when(artifactoryContext.beanForType(TrafficService.class)).thenReturn(trafficService);
            trafficFilter.initLater(filterConfig);
        } catch (ServletException e) {
            throw new RuntimeException("Failed to initialize TrafficFilter", e);
        }
    }

    @Test
    void testDoFilterWithTrafficLogV3FlowButNoRepoPath() throws IOException, ServletException {
        // Given
        initializeTrafficFilter();

        when(trafficService.isV3Active()).thenReturn(true);
        when(httpServletRequest.getMethod()).thenReturn(GET.name());
        trafficContext.when(TrafficContextThreadLocal::isTrafficLogV3Flow).thenReturn(true);
        trafficContext.when(TrafficContextThreadLocal::getRepoPath).thenReturn(null);

        // When
        trafficFilter.doFilter(httpServletRequest, httpServletResponse, filterChain);

        // Then
        verify(trafficService).isV3Active();
        trafficContext.verify(TrafficContextThreadLocal::isTrafficLogV3Flow);
        trafficContext.verify(TrafficContextThreadLocal::getRepoPath);

        // With the new implementation, no traffic entry should be created when repoPath is null
        verify(trafficService, never()).handleTrafficEntryV3(any());
        // fillOriginFields should not be called because method returns early
        trafficContext.verify(() -> TrafficContextThreadLocal.fillOriginFields(any()), never());

        trafficContext.verify(TrafficContextThreadLocal::bind);
        trafficContext.verify(TrafficContextThreadLocal::unbind);
    }

    @ParameterizedTest
    @MethodSource("originRepoKeyTestCaseProvider")
    void extractFromRequestPath_shouldReturnExpectedRepoKey_whenGivenRequestUri(
            String requestUri, String expectedRepoKey) {
        String actualRepoKey = TrafficFilter.RepoKeyExtractor.extractFromRequestPath(requestUri);
        assertEquals(expectedRepoKey, actualRepoKey, "Failed for URI: " + requestUri);
    }

    static Stream<Arguments> originRepoKeyTestCaseProvider() {
        return Stream.of(
                Arguments.of("/terraform/v1/modules/terraform-remote-repo__cloudposse/label/null/0.25.0/download",
                        "terraform-remote-repo"),
                Arguments.of("/terraform/v1/providers/terraform-provider-repo-rt-ns-namespace/1.0.0/download",
                        "terraform-provider-repo"),
                Arguments.of("/terraform/remote/v2/state-versions/terraform-remote-repo__workspace__id",
                        "terraform-remote-repo"),
                Arguments.of("/terraform/remote/v2/workspaces/terraform-remote-repo__workspace/",
                        "terraform-remote-repo"),
                Arguments.of("/terraform/remote/v2/organizations/terraform-org-repo/",
                        "terraform-org-repo"),
                Arguments.of("/docker/topgolf-docker-dev/v2/topgolf/games-topgolf-block-party/manifests/2.46.0",
                        "topgolf-docker-dev"),
                Arguments.of("/terraform/unexpected/structure/", "")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/", "incorrectRequestPath"})
    void extractFromRequestPath_shouldThrowException_whenGivenInvalidRequestUri(String requestUri) {
        assertThrows(IllegalArgumentException.class,
                () -> TrafficFilter.RepoKeyExtractor.extractFromRequestPath(requestUri));
    }
}
