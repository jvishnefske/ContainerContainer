package org.artifactory.webapp.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.http.HttpHeaders;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author Dvir Twina
 */
class RequestUtilsTest extends ArtifactoryHomeBoundJUnitTest {
    @Mock
    private HttpServletRequest request;
    @Mock
    private ArtifactoryContext context;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void beforeMethod() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        autoCloseable.close();
    }

    @ParameterizedTest
    @CsvSource({"Basic YWRtaW46cGFzc3dvcmQ=,admin", "'',''"})
    void testExtractUsernameFromRequest(String header, String expected) {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(header);

        assertEquals(expected, RequestUtils.extractUsernameFromRequest(request));
    }

    @Test
    void testExtractPasswordFromRequest() {
        String header = "Basic YWRtaW46cGFzc3dvcmQ=";
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(header);

        assertEquals("password", RequestUtils.extractPasswordFromRequest(request));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetServletPathWithoutQuery(boolean query) {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/system/ping");
        when(request.getQueryString()).thenReturn(null);

        String result = RequestUtils.getServletPathFromRequest(request, query);

        assertEquals("/system/ping", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGetServletPathWithQuery(boolean query) {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/repodata");
        when(request.getQueryString()).thenReturn("packageSearch=true");

        String result = RequestUtils.getServletPathFromRequest(request, query);

        assertEquals(query ? "/repodata?packageSearch=true" : "/repodata", result);
    }

    @ParameterizedTest
    @MethodSource("blank")
    void testGetServletPathWithBlankContextPath(String contextPath, boolean includeQuery) {
        when(request.getContextPath()).thenReturn(contextPath);
        when(request.getRequestURI()).thenReturn("/resource");
        when(request.getQueryString()).thenReturn("id=1");

        String result = RequestUtils.getServletPathFromRequest(request, includeQuery);

        assertEquals(includeQuery ? "/resource?id=1" : "/resource", result);
    }

    @ParameterizedTest
    @MethodSource("blank")
    void testGetServletPathWithNoQueryString(String query, boolean includeQuery) {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/resource");
        when(request.getQueryString()).thenReturn(query);

        String result = RequestUtils.getServletPathFromRequest(request, includeQuery);

        assertEquals("/resource", result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/artifactory"})
    @NullAndEmptySource
    void testGetServletPathFromRequestWithSensitiveParams(String contextPath) {
        when(request.getContextPath()).thenReturn(contextPath);
        when(request.getRequestURI()).thenReturn(contextPath != null ? contextPath + "/api/resource" : "/api/resource");
        when(request.getQueryString()).thenReturn("token=abc123&password=secret&id=456");

        String result = RequestUtils.getServletPathFromRequest(request, true);
        assertEquals("/api/resource?token=****&password=****&id=456", result);
    }

    @Test
    void testExtractRepoKey_nullOrEmpty() {
        assertNull(RequestUtils.extractRepoKey(null));
        assertNull(RequestUtils.extractRepoKey(""));
        assertNull(RequestUtils.extractRepoKey("/"));
    }

    @Test
    void testExtractRepoKey_listPrefix() {
        String path = "list/libs-release-local/some/path";
        assertEquals("libs-release-local", RequestUtils.extractRepoKey(path));
    }

    @Test
    void testExtractRepoKey_simplePrefix() {
        String path = "simple/libs-release-local/some/path";
        assertEquals("libs-release-local", RequestUtils.extractRepoKey(path));
    }

    @Test
    void testExtractRepoKey_uiPrefix() {
        RequestUtils.setUiPathPrefixes(List.of("ui"));
        assertNull(RequestUtils.extractRepoKey("ui/libs-release-local/some/path"));
        RequestUtils.setUiPathPrefixes(List.of()); // reset
    }

    @Test
    void testExtractRepoKey_nonUiPrefix() {
        RequestUtils.setNonUiPathPrefixes(List.of("api"));
        assertNull(RequestUtils.extractRepoKey("api/libs-release-local/some/path"));
        RequestUtils.setNonUiPathPrefixes(List.of()); // reset
    }

    @Test
    void testExtractRepoKey_normalRepoKey() {
        assertEquals("libs-release-local", RequestUtils.extractRepoKey("libs-release-local/some/path"));
        assertEquals("libs-release-local", RequestUtils.extractRepoKey("libs-release-local"));
    }

    @Test
    void testIsRepoRequest_validRepoKey() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local/some/path");

        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));
        when(context.getRepositoryService()).thenReturn(repoService);
        assertTrue(RequestUtils.isRepoRequest(request, false));

    }

    @Test
    void testIsRepoRequest_repoKeyNotFound_noWarn() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/unknown-repo/some/path");

        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));
        when(context.getRepositoryService()).thenReturn(repoService);

        assertFalse(RequestUtils.isRepoRequest(request, false));

    }

    @Test
    void testIsRepoRequest_repoKeyNotFound_withWarn() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/unknown-repo/some/path");

        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));
        when(context.getRepositoryService()).thenReturn(repoService);

        assertFalse(RequestUtils.isRepoRequest(request, true));

    }

    @Test
    void testIsRepoRequest_noRepoKey() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/");

        assertFalse(RequestUtils.isRepoRequest(request, false));
    }

    @Test
    void testIsRepoRequest_withMetadataAndMatrixParams() {
        when(request.getContextPath()).thenReturn("/artifactory");
        // repoKey = "libs-release-local;metadata;param=value"
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local;metadata;param=value/some/path");

        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));
        when(context.getRepositoryService()).thenReturn(repoService);

        assertTrue(RequestUtils.isRepoRequest(request, false));
    }

    @Test
    void testIsRepoRequest_urlEncodedRepoKey() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs%20release%20local/some/path");

        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs release local"));
        when(context.getRepositoryService()).thenReturn(repoService);

        assertTrue(RequestUtils.isRepoRequest(request, false));
    }

    @Test
    void testIsRepoRequest_invalidUrlEncoding() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs%ZZ/some/path");

        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));
        when(context.getRepositoryService()).thenReturn(repoService);

        assertFalse(RequestUtils.isRepoRequest(request, false));
    }

    @Test
    void testSetNonUiPathPrefixesAndSetUiPathPrefixes() {
        assertDoesNotThrow(() -> RequestUtils.setNonUiPathPrefixes(List.of("api", "foo")));
        assertDoesNotThrow(() -> RequestUtils.setUiPathPrefixes(List.of("ui", "bar")));
    }

    @Test
    void testSetPackagesEndpointUseBasicAuth() {
        assertDoesNotThrow(RequestUtils::setPackagesEndpointUseBasicAuth);
    }

    @Test
    void testGetContextPrefix() {
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local/some/path");
        assertEquals("artifactory", RequestUtils.getContextPrefix(request));

        when(request.getRequestURI()).thenReturn("/foo");
        assertEquals("", RequestUtils.getContextPrefix(request));
    }

    @Test
    void testMatchPkgEndpoint() {
        when(request.getRequestURI()).thenReturn("/artifactory/api/docker/v2/");
        when(request.getContextPath()).thenReturn("/artifactory");
        assertTrue(RequestUtils.matchPkgEndpoint(request, "/api/docker/v2/"));
        assertFalse(RequestUtils.matchPkgEndpoint(request, "/api/nuget/"));
    }

    @Test
    void testPkgEndpointMatchBasicAuth() {
        RequestUtils.setPackagesEndpointUseBasicAuth();
        when(request.getRequestURI()).thenReturn("/artifactory/api/docker/");
        when(request.getContextPath()).thenReturn("/artifactory");
        assertTrue(RequestUtils.pkgEndpointMatchBasicAuth(request));
        when(request.getRequestURI()).thenReturn("/artifactory/api/other/");
        assertFalse(RequestUtils.pkgEndpointMatchBasicAuth(request));
    }

    @Test
    void testIsWebdavRequest_method() {
        // Not a repo request
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/");
        when(request.getMethod()).thenReturn("PROPFIND");
        assertFalse(RequestUtils.isWebdavRequest(request));

        // Is repo request, method is webdav
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local/some/path");
        when(request.getMethod()).thenReturn("PROPFIND");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));
        when(context.getRepositoryService()).thenReturn(repoService);

        assertTrue(RequestUtils.isWebdavRequest(request));
    }

    @Test
    void testIsWebdavRequest_header() {
        // Is repo request, method is not webdav, but header is present
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local/some/path");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("X-wagon-provider")).thenReturn("webdav");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("libs-release-local"));

        when(context.getRepositoryService()).thenReturn(repoService);

        assertTrue(RequestUtils.isWebdavRequest(request));
    }

    @Test
    void testIsUiRequest_trueAndFalse() {
        // Not WebDav, UI path prefix, should return true
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/ui/some/path");
        RequestUtils.setUiPathPrefixes(List.of("ui"));
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("X-wagon-provider")).thenReturn(null);
        assertTrue(RequestUtils.isUiRequest(request));

        // Is WebDav, non UI, should return false
        when(request.getRequestURI()).thenReturn("/artifactory/repoKey/some/path");
        when(request.getMethod()).thenReturn("propfind");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.getAllRepoKeys()).thenReturn(Set.of("repoKey"));
        when(context.getRepositoryService()).thenReturn(repoService);
        RequestUtils.setUiPathPrefixes(List.of());
        assertFalse(RequestUtils.isUiRequest(request));
    }

    @Test
    void testIsUiPathPrefix() {
        RequestUtils.setUiPathPrefixes(List.of("ui"));
        RequestUtils.setNonUiPathPrefixes(List.of("api"));
        assertTrue(RequestUtils.isUiPathPrefix("ui"));
        assertFalse(RequestUtils.isUiPathPrefix("api"));
        assertFalse(RequestUtils.isUiPathPrefix("other"));
        RequestUtils.setUiPathPrefixes(List.of());
        RequestUtils.setNonUiPathPrefixes(List.of());
    }

    @Test
    void testIsBasicAuthHeaderPresentAndGetBasicAuthorization() {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");
        assertTrue(RequestUtils.isBasicAuthHeaderPresent(request));
        assertTrue(RequestUtils.getBasicAuthorization(request).isPresent());

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        assertFalse(RequestUtils.isBasicAuthHeaderPresent(request));
        assertFalse(RequestUtils.getBasicAuthorization(request).isPresent());

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic ");
        assertFalse(RequestUtils.getBasicAuthorization(request).isPresent());

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic Og==");
        assertFalse(RequestUtils.getBasicAuthorization(request).isPresent());
    }

    @Test
    void testIsBearerAuthenticationAndGetBearerAuthenticationValue() {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer token");
        assertTrue(RequestUtils.isBearerAuthentication(request));
        assertTrue(RequestUtils.getBearerAuthenticationValue(request).isPresent());

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("bearer token2");
        assertTrue(RequestUtils.isBearerAuthentication(request));
        assertTrue(RequestUtils.getBearerAuthenticationValue(request).isPresent());

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");
        assertFalse(RequestUtils.isBearerAuthentication(request));
        assertFalse(RequestUtils.getBearerAuthenticationValue(request).isPresent());

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer ");
        assertFalse(RequestUtils.getBearerAuthenticationValue(request).isPresent());
    }

    @Test
    void testAddAdditionalHeadersToWebAppRequest() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/webapp/app.html");
        when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("MSIE 10.0");
        HttpServletResponse response = mock(HttpServletResponse.class);

        // Set system property for ConstantValues.enableUiPagesInIframe to false
        System.setProperty("artifactory.ui.pages.in.iframe", "false");
        RequestUtils.addAdditionalHeadersToWebAppRequest(request, response);
        // Should set X-UA-Compatible, Cache-Control, X-FRAME-OPTIONS
        Mockito.verify(response).setHeader("X-UA-Compatible", "IE=Edge");
        Mockito.verify(response).setHeader("Cache-Control", "no-store,max-age=0");
        Mockito.verify(response).setHeader("X-FRAME-OPTIONS", "DENY");
        System.clearProperty("artifactory.ui.pages.in.iframe");
    }

    @Test
    void testAddAdditionalHeadersToWebAppRequest_noHeaders() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/other/path");
        HttpServletResponse response = mock(HttpServletResponse.class);
        RequestUtils.addAdditionalHeadersToWebAppRequest(request, response);
        // Should not set any headers
        Mockito.verifyNoInteractions(response);
    }

    @Test
    void testGetArtifactoryContext() {
        ServletContext servletContext = mock(ServletContext.class);
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(servletContext.getAttribute(ArtifactoryContext.APPLICATION_CONTEXT_KEY)).thenReturn(context);
        assertEquals(context, RequestUtils.getArtifactoryContext(servletContext));
    }

    @Test
    void testAnalyzeRepoRequest_notARepoRequest() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/");
        assertEquals(RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST, RequestUtils.analyzeRepoRequest(request));
    }

    @Test
    void testAnalyzeRepoRequest_invalidUrlEncoding() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs%ZZ/some/path");
        RepositoryService repoService = mock(RepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repoService);
        assertEquals(RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST, RequestUtils.analyzeRepoRequest(request));
    }

    /**
     * In the real flow, reserved names are set on:
     * {@link org.artifactory.webapp.servlet.RepoFilter#initLater}, taken from the web.xml
     */
    @Test
    void analyzeRepoRequest_reservedName() {
        String reservedName = "reservedName";
        RequestUtils.setNonUiPathPrefixes(Set.of(reservedName));
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/" + reservedName + "/some/path");
        assertEquals(RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST, RequestUtils.analyzeRepoRequest(request));
    }

    @Test
    void analyzeRequestReport_McRequest_repoDoesNotExist() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/mc/some/path");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.repoExists("mc")).thenReturn(false);
        when(context.getRepositoryService()).thenReturn(repoService);
        assertEquals(RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST, RequestUtils.analyzeRepoRequest(request));
    }

    @Test
    void analyzeRequestReport_McRequest_repoExists() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/mc/some/path");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.repoExists("mc")).thenReturn(true);
        when(context.getRepositoryService()).thenReturn(repoService);
        assertEquals(RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS,
                RequestUtils.analyzeRepoRequest(request));
    }

    @Test
    void testAnalyzeRepoRequest_repoDoesNotExist() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local/some/path");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.repoExists("libs-release-local")).thenReturn(false);
        when(context.getRepositoryService()).thenReturn(repoService);
        assertEquals(RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_NOT_EXISTS,
                RequestUtils.analyzeRepoRequest(request));
    }

    @Test
    void testAnalyzeRepoRequest_repoExists() {
        when(request.getContextPath()).thenReturn("/artifactory");
        when(request.getRequestURI()).thenReturn("/artifactory/libs-release-local/some/path");
        RepositoryService repoService = mock(RepositoryService.class);
        when(repoService.repoExists("libs-release-local")).thenReturn(true);
        when(context.getRepositoryService()).thenReturn(repoService);
        assertEquals(RequestUtils.RepoRequestAnalysis.REPO_REQUEST_REPO_EXISTS,
                RequestUtils.analyzeRepoRequest(request));
    }

    @Test
    void testAnalyzeRepoRequest_URLDecodeFails_notRepoRequest() {
        try (MockedStatic<URLDecoder> urlDecoderMockedStatic = mockStatic(URLDecoder.class)) {
            when(request.getContextPath()).thenReturn("/artifactory");
            when(request.getRequestURI()).thenReturn("/artifactory/repoKey/some/path");
            urlDecoderMockedStatic.when(() -> URLDecoder.decode("repoKey", StandardCharsets.UTF_8))
                    .thenThrow(new IllegalArgumentException("Invalid URL encoding"));

            assertEquals(RequestUtils.RepoRequestAnalysis.NOT_A_REPO_REQUEST, RequestUtils.analyzeRepoRequest(request));
        }
    }

    public static Stream<Arguments> blank() {
        return Stream.of(
                Arguments.of(null, true),
                Arguments.of(null, false),
                Arguments.of("", true),
                Arguments.of("", false));
    }
}