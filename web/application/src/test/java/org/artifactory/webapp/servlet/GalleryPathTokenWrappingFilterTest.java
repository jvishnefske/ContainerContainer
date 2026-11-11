/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2024 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.webapp.servlet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.http.HttpHeaders;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.OauthManager;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.token.AccessTokenType;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GalleryPathTokenWrappingFilterTest {

    private static final String AI_EDITOR_EXTENSIONS_PATH_MARKER = "/aieditorextensions/";
    private static final String REMOTE_REPO_KEY = "my-repo";
    private static final String OTHER_REPO_KEY = "other-repo";
    private static final String AI_EDITOR_EXTENSIONS_PREFIX = AI_EDITOR_EXTENSIONS_PATH_MARKER + REMOTE_REPO_KEY;
    private static final String ARTIFACT_SCOPE_PREFIX = "artifact:";
    private static final String ARTIFACTORY_WEBAPP_CONTEXT = "/artifactory";
    private static final String ARTIFACTORY_API_SERVLET_PATH = "/api";
    /** /artifactory/api/aieditorextensions/my-repo/ */
    private static final String WEBAPP_API_AI_EDITOR_REPO_SLASH =
            ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + AI_EDITOR_EXTENSIONS_PATH_MARKER
                    + REMOTE_REPO_KEY + "/";
    private static final String ALTERNATE_SERVLET_PATH = "/legacy-api";
    private static final String SAMPLE_EMBEDDED_ACCESS_TOKEN =
            "tcmVmdGtuOjAxOjE4MDcyNzI0MDE6QkR11110em1yUFl4bUVTRGJvbUtKTGZ2VG45";
    private static final String EXPECTED_BEARER_FOR_EMBEDDED_TOKEN =
            OauthManager.OAUTH_TOKEN_PREFIX + SAMPLE_EMBEDDED_ACCESS_TOKEN;
    private static final String SAMPLE_EMBEDDED_TOKEN_FOR_OVERRIDE_SCENARIO = "from-path-token";

    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private AccessService accessService;

    private MockedStatic<RequestUtils> requestUtilsStaticMock;

    @BeforeEach
    void openRequestUtilsStaticMock() {
        requestUtilsStaticMock = mockStatic(RequestUtils.class);
    }

    @AfterEach
    void closeRequestUtilsStaticMock() {
        requestUtilsStaticMock.close();
    }

    private void givenAccessServiceResolvedFromServletContext() {
        requestUtilsStaticMock.when(() -> RequestUtils.getArtifactoryContext(any(ServletContext.class)))
                .thenReturn(artifactoryContext);
        when(artifactoryContext.beanForType(AccessService.class)).thenReturn(accessService);
    }

    private void stubAccessServiceTreatsThesePathSegmentsAsReferenceTokens(String... pathSegmentValues) {
        when(accessService.getAccessTokenType(anyString())).thenAnswer(invocation -> {
            String pathSegment = invocation.getArgument(0);
            for (String acceptedValue : pathSegmentValues) {
                if (acceptedValue.equals(pathSegment)) {
                    return AccessTokenType.REFERENCE_TOKEN;
                }
            }
            return AccessTokenType.NOT_VALID_TOKEN;
        });
    }

    private void stubVerifyReferenceTokenReturnsArtifactScopesForRepo(String repoKey, String scopeActions) {
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getScope()).thenReturn(List.of(ARTIFACT_SCOPE_PREFIX + repoKey + ":" + scopeActions));
        TokenVerifyResult verifyResult = mock(TokenVerifyResult.class);
        when(verifyResult.isSuccessful()).thenReturn(true);
        when(verifyResult.getAccessToken()).thenReturn(jwtAccessToken);
        when(accessService.verifyAndGetResult(anyString())).thenReturn(verifyResult);
    }

    /**
     * ServletContext resolves AccessService; path segments are reference tokens; verify succeeds with artifact scope.
     */
    private void givenAccessAndVerifiedReferenceToken(String repoKey, String scopeActions, String... pathTokens) {
        givenAccessServiceResolvedFromServletContext();
        stubAccessServiceTreatsThesePathSegmentsAsReferenceTokens(pathTokens);
        stubVerifyReferenceTokenReturnsArtifactScopesForRepo(repoKey, scopeActions);
    }

    private static void assertBearerHeaderUsesSampleEmbeddedToken(HttpServletRequest request) {
        assertEquals(EXPECTED_BEARER_FOR_EMBEDDED_TOKEN, request.getHeader(HttpHeaders.AUTHORIZATION));
    }

    private static MockHttpServletRequest mockHttpServletRequest(String requestUri, String contextPath,
            String servletPath) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        request.setContextPath(contextPath);
        request.setServletPath(servletPath);
        return request;
    }

    @Test
    void whenUriDoesNotIncludeAiEditorExtensions_requestIsUnchanged() {
        MockHttpServletRequest request = mockHttpServletRequest("/artifactory/api/system/ping", "", "");
        assertSame(request, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(request));
    }

    @Test
    void whenGalleryBranchIsVscode_requestIsUnchanged() {
        givenAccessServiceResolvedFromServletContext();
        stubAccessServiceTreatsThesePathSegmentsAsReferenceTokens();
        String webappContextPath = "/app";
        String pathAfterServletMapping = AI_EDITOR_EXTENSIONS_PREFIX
                + "/generic-local/_apis/public/gallery/vscode/extension/foo";
        String fullRequestUri = webappContextPath + ARTIFACTORY_API_SERVLET_PATH + pathAfterServletMapping;
        MockHttpServletRequest request =
                mockHttpServletRequest(fullRequestUri, webappContextPath, ARTIFACTORY_API_SERVLET_PATH);

        assertSame(request, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(request));
    }

    @Test
    void whenGalleryBranchIsExtensionQuery_requestIsUnchanged() {
        givenAccessServiceResolvedFromServletContext();
        stubAccessServiceTreatsThesePathSegmentsAsReferenceTokens();
        String pathAfterServlet = AI_EDITOR_EXTENSIONS_PREFIX + "/generic-local/_apis/public/gallery/extensionquery";
        String fullRequestUri = ALTERNATE_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest request = mockHttpServletRequest(fullRequestUri, "", ALTERNATE_SERVLET_PATH);

        assertSame(request, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(request));
    }

    @Test
    void whenRequestUriIsBlank_requestIsUnchanged() {
        MockHttpServletRequest request = mockHttpServletRequest(" ", "", "");
        assertSame(request, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(request));
    }

    @Test
    void whenEmbeddedSegmentIsValidAccessToken_uriIsSanitisedAndBearerHeaderSet() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String pathAfterServlet = AI_EDITOR_EXTENSIONS_PREFIX + "/handler/generic-local/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_ACCESS_TOKEN + "/vscode/item";
        String fullRequestUri = ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertNotSame(incomingRequest, wrappedRequest);
        assertEquals(ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + AI_EDITOR_EXTENSIONS_PREFIX
                        + "/handler/generic-local/_apis/public/gallery/vscode/item",
                wrappedRequest.getRequestURI());
        assertEquals(ARTIFACTORY_API_SERVLET_PATH, wrappedRequest.getServletPath());
        assertEquals(AI_EDITOR_EXTENSIONS_PREFIX + "/handler/generic-local/_apis/public/gallery/vscode/item",
                wrappedRequest.getPathInfo());
        assertBearerHeaderUsesSampleEmbeddedToken(wrappedRequest);
        List<String> authorizationValues = Collections.list(wrappedRequest.getHeaders(HttpHeaders.AUTHORIZATION));
        assertEquals(1, authorizationValues.size());
        assertTrue(Collections.list(wrappedRequest.getHeaderNames()).stream()
                .anyMatch(HttpHeaders.AUTHORIZATION::equalsIgnoreCase));
    }

    @Test
    void whenApiAiEditorExtensionsPathEmbedsValidAccessToken_uriIsSanitisedAndBearerHeaderSet() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String fullRequestUri = WEBAPP_API_AI_EDITOR_REPO_SLASH + SAMPLE_EMBEDDED_ACCESS_TOKEN
                + "/https/open-vsx.org/api/ms-toolsai/jupyter/file/package.json";
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertNotSame(incomingRequest, wrappedRequest);
        String expectedSanitisedUri = WEBAPP_API_AI_EDITOR_REPO_SLASH
                + "https/open-vsx.org/api/ms-toolsai/jupyter/file/package.json";
        assertEquals(expectedSanitisedUri, wrappedRequest.getRequestURI());
        assertBearerHeaderUsesSampleEmbeddedToken(wrappedRequest);
    }

    @Test
    void whenTokenScopeUsesWriteActionsForRequestRepo_uriIsSanitisedAndBearerHeaderSet() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "w", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String pathAfterServlet = AI_EDITOR_EXTENSIONS_PREFIX + "/handler/generic-local/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_ACCESS_TOKEN + "/vscode/item";
        String fullRequestUri = ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertNotSame(incomingRequest, wrappedRequest);
        assertBearerHeaderUsesSampleEmbeddedToken(wrappedRequest);
    }

    @Test
    void whenTokenScopeTargetsDifferentRepo_requestIsUnchanged() {
        givenAccessAndVerifiedReferenceToken(OTHER_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String pathAfterServlet = AI_EDITOR_EXTENSIONS_PREFIX + "/handler/generic-local/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_ACCESS_TOKEN + "/vscode/item";
        String fullRequestUri = ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);

        assertSame(incomingRequest, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest));
    }

    @Test
    void whenEmbeddedPathSegmentIsNotValidAccessToken_requestIsUnchanged() {
        givenAccessServiceResolvedFromServletContext();
        when(accessService.getAccessTokenType(anyString())).thenReturn(AccessTokenType.NOT_VALID_TOKEN);
        String fullRequestUri = WEBAPP_API_AI_EDITOR_REPO_SLASH + SAMPLE_EMBEDDED_ACCESS_TOKEN
                + "/https/example/file.json";
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);

        assertSame(incomingRequest, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest));
    }

    @Test
    void whenApiAiEditorExtensionsSecondSegmentIsNotAccessToken_requestIsUnchanged() {
        givenAccessServiceResolvedFromServletContext();
        stubAccessServiceTreatsThesePathSegmentsAsReferenceTokens(SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String fullRequestUri = WEBAPP_API_AI_EDITOR_REPO_SLASH + "https/open-vsx.org/foo";
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);

        assertSame(incomingRequest, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest));
    }

    @Test
    void whenSanitisedUriDoesNotMatchOriginalServletPrefix_rebuildsServletPathAndPathInfo() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String fullRequestUri = "/repo/custom-mount" + AI_EDITOR_EXTENSIONS_PREFIX + "/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_ACCESS_TOKEN + "/vscode/item";
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, "", ARTIFACTORY_API_SERVLET_PATH);

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertNotSame(incomingRequest, wrappedRequest);
        assertEquals("/repo/custom-mount" + AI_EDITOR_EXTENSIONS_PREFIX + "/_apis/public/gallery/vscode/item",
                wrappedRequest.getRequestURI());
        assertEquals("/repo", wrappedRequest.getServletPath());
        assertEquals("/custom-mount" + AI_EDITOR_EXTENSIONS_PREFIX + "/_apis/public/gallery/vscode/item",
                wrappedRequest.getPathInfo());
        assertBearerHeaderUsesSampleEmbeddedToken(wrappedRequest);
    }

    @Test
    void whenContextPathCoversGalleryPrefix_singleSegmentFallbackLeavesOneServletPath() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String contextPathCoveringGallery =
                "/tenant" + AI_EDITOR_EXTENSIONS_PATH_MARKER + REMOTE_REPO_KEY + "/_apis/public/gallery";
        String fullRequestUri = contextPathCoveringGallery + "/" + SAMPLE_EMBEDDED_ACCESS_TOKEN + "/vscode";
        String inconsistentServletPath = "/servlet-path-not-matching-uri-prefix";
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, contextPathCoveringGallery, inconsistentServletPath);

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertNotSame(incomingRequest, wrappedRequest);
        assertEquals(contextPathCoveringGallery + "/vscode", wrappedRequest.getRequestURI());
        assertEquals("/vscode", wrappedRequest.getServletPath());
        assertNull(wrappedRequest.getPathInfo());
        assertBearerHeaderUsesSampleEmbeddedToken(wrappedRequest);
    }

    @Test
    void pathEmbeddedTokenIsAlwaysExposedAsAuthorization_evenIfRequestAlreadyHadBearer() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_TOKEN_FOR_OVERRIDE_SCENARIO);
        String handlerMountPath = "/package-scope" + AI_EDITOR_EXTENSIONS_PREFIX + "/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_TOKEN_FOR_OVERRIDE_SCENARIO + "/resource";
        String fullRequestUri = ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + handlerMountPath;
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);
        incomingRequest.addHeader(HttpHeaders.AUTHORIZATION,
                OauthManager.OAUTH_TOKEN_PREFIX + "existing-header-not-used-for-wrapped-authorization");

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertEquals(OauthManager.OAUTH_TOKEN_PREFIX + SAMPLE_EMBEDDED_TOKEN_FOR_OVERRIDE_SCENARIO,
                wrappedRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void whenTokenIsLastGallerySegment_uriEndsAtGalleryAndBearerStillSet() {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String pathAfterServlet = AI_EDITOR_EXTENSIONS_PREFIX + "/handler-scope/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_ACCESS_TOKEN;
        String fullRequestUri = ARTIFACTORY_API_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest incomingRequest = mockHttpServletRequest(
                fullRequestUri, "", ARTIFACTORY_API_SERVLET_PATH);

        HttpServletRequest wrappedRequest = GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest);

        assertNotSame(incomingRequest, wrappedRequest);
        String expectedSanitisedUri = ARTIFACTORY_API_SERVLET_PATH + AI_EDITOR_EXTENSIONS_PREFIX
                + "/handler-scope/_apis/public/gallery/";
        assertEquals(expectedSanitisedUri, wrappedRequest.getRequestURI());
        assertBearerHeaderUsesSampleEmbeddedToken(wrappedRequest);
    }

    @Test
    void whenNothingFollowsGalleryPrefix_requestIsUnchanged() {
        String mountPathAfterApi = "/package-scope" + AI_EDITOR_EXTENSIONS_PREFIX + "/_apis/public/gallery/";
        String fullRequestUri = ARTIFACTORY_WEBAPP_CONTEXT + ARTIFACTORY_API_SERVLET_PATH + mountPathAfterApi;
        MockHttpServletRequest incomingRequest =
                mockHttpServletRequest(fullRequestUri, ARTIFACTORY_WEBAPP_CONTEXT, ARTIFACTORY_API_SERVLET_PATH);
        assertSame(incomingRequest, GalleryPathTokenWrappingFilter.wrapGalleryPathBearer(incomingRequest));
    }

    @Test
    void doFilter_passesWrappedRequestWhenPathContainsRecognisedToken() throws IOException, ServletException {
        givenAccessAndVerifiedReferenceToken(REMOTE_REPO_KEY, "r", SAMPLE_EMBEDDED_ACCESS_TOKEN);
        String pathAfterServlet = "/handler-key" + AI_EDITOR_EXTENSIONS_PREFIX + "/_apis/public/gallery/"
                + SAMPLE_EMBEDDED_ACCESS_TOKEN + "/sub-path";
        String fullRequestUri = ARTIFACTORY_API_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest incomingRequest = mockHttpServletRequest(
                fullRequestUri, "", ARTIFACTORY_API_SERVLET_PATH);
        FilterChain filterChain = mock(FilterChain.class);

        new GalleryPathTokenWrappingFilter().doFilter(incomingRequest, mock(ServletResponse.class), filterChain);

        ArgumentCaptor<ServletRequest> chainRequestCaptor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(filterChain).doFilter(chainRequestCaptor.capture(), any());
        HttpServletRequest passedDownstream = (HttpServletRequest) chainRequestCaptor.getValue();
        assertNotSame(incomingRequest, passedDownstream);
        assertEquals(ARTIFACTORY_API_SERVLET_PATH + "/handler-key" + AI_EDITOR_EXTENSIONS_PREFIX
                        + "/_apis/public/gallery/sub-path",
                passedDownstream.getRequestURI());
        assertBearerHeaderUsesSampleEmbeddedToken(passedDownstream);
    }

    @Test
    void doFilter_withoutAiEditorMarker_passesSameRequestInstance() throws IOException, ServletException {
        String pathAfterServlet = "/handler-key/_apis/public/gallery/vscode/extra";
        String fullRequestUri = ARTIFACTORY_API_SERVLET_PATH + pathAfterServlet;
        MockHttpServletRequest incomingRequest = mockHttpServletRequest(
                fullRequestUri, "", ARTIFACTORY_API_SERVLET_PATH);
        FilterChain filterChain = mock(FilterChain.class);

        new GalleryPathTokenWrappingFilter().doFilter(incomingRequest, mock(ServletResponse.class), filterChain);

        ArgumentCaptor<ServletRequest> chainRequestCaptor = ArgumentCaptor.forClass(ServletRequest.class);
        verify(filterChain).doFilter(chainRequestCaptor.capture(), any());
        assertSame(incomingRequest, chainRequestCaptor.getValue());
    }
}
