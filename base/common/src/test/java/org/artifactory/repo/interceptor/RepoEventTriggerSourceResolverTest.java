package org.artifactory.repo.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.security.AuthenticationHelper;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepoEventTriggerSourceResolverTest {

    @Mock
    private SecurityService securityService;

    @BeforeEach
    void setupDefaultUsername() {
        // Default to a non-system user. Lenient: most tests return before isSystemPrincipal() is reached
        // (AI / tool / UI checks short-circuit first per DC11 ordering), so the stub is unused there.
        lenient().when(securityService.currentUsername()).thenReturn("admin");
    }

    private RepoEventTriggerSourceResolver resolver() {
        return new RepoEventTriggerSourceResolver(securityService);
    }

    // No HTTP request context = background/scheduled operation → SYSTEM_INTERNAL regardless of principal.
    @Test
    void resolve_shouldReturnSystemInternal_whenNoHttpRequestContext() {
        try (MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            rtl.when(RequestThreadLocal::getRequest).thenReturn(null);

            assertEquals(RepoEventTriggerSource.SYSTEM_INTERNAL.name(), resolver().resolve());
        }
    }

    // System principal with an HTTP request that has no named-surface signals → falls through to
    // isSystemPrincipal(), which is now checked after AI/Terraform/CLI/UI (DC11 updated ordering).
    @Test
    void resolve_shouldReturnSystemInternal_whenPrincipalIsSystemAndRequestHasNoSpecificSignals() {
        when(securityService.currentUsername()).thenReturn(SecurityService.USER_SYSTEM);
        HttpServletRequest req = request();
        try (MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            rtl.when(RequestThreadLocal::getRequest).thenReturn(req);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("curl/7.68.0");

            assertEquals(RepoEventTriggerSource.SYSTEM_INTERNAL.name(), resolver().resolve());
        }
    }

    @Test
    void resolve_shouldReturnSystemInternal_whenAccessTokenSubjectIsKnownServiceToken() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("jfxr@01abc");
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);

            assertEquals(RepoEventTriggerSource.SYSTEM_INTERNAL.name(), resolver().resolve());
        }
    }

    @Test
    void resolve_shouldReturnJfrogAiAgent_whenJfrogAiHeaderIsPresent() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_AI_AGENT, requestWithHeader("X-JFrog-AI-Agent", "true"),
                "agent/cursor");
    }

    @Test
    void resolve_shouldReturnUserAiAgent_whenGenericAiAgentSignalIsPresent() {
        assertRequestResolvedAs(RepoEventTriggerSource.USER_AI_AGENT, request(), "agent/claude");
    }

    @Test
    void resolve_shouldPreferJfrogAiOverCli_whenSignalsAreMixed() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_AI_AGENT, requestWithHeader("X-JFrog-AI-Agent", "true"),
                "jfrog-cli-go/2.74.0");
    }

    @Test
    void resolve_shouldPreferTerraformOverCliAndUi_whenSignalsAreMixed() {
        assertRequestResolvedAs(RepoEventTriggerSource.TERRAFORM, request(),
                "jfrog-frontend jfrog/terraform-provider-artifactory/12.0.0 jfrog-cli-go");
    }

    @Test
    void resolve_shouldPreferCliOverUi_whenSignalsAreMixed() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_CLI, request(), "jfrog-frontend jfrog-cli-go/2.74.0");
    }

    @Test
    void resolve_shouldReturnJfrogUi_whenRequestAgentHeaderIsArtifactoryUi() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_UI, requestWithHeader("Request-Agent", "artifactoryUI"),
                "Mozilla/5.0");
    }

    @Test
    void resolve_shouldReturnRestApi_whenHttpRequestHasNoSpecificSignal() {
        assertRequestResolvedAs(RepoEventTriggerSource.REST_API, request(), "curl/7.68.0");
    }

    @Test
    void resolve_shouldReturnUnknown_whenRequestHasNoClassifiableSignals() {
        assertRequestResolvedAs(RepoEventTriggerSource.UNKNOWN, mock(HttpServletRequest.class), null);
    }

    @Test
    void resolve_shouldReturnJfrogUi_whenAccessTokenWasIssuedByJfrogFrontend() {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            auth.when(() -> AuthenticationHelper.isJFrogFrontendAuthentication(authentication)).thenReturn(true);

            assertEquals(RepoEventTriggerSource.JFROG_UI.name(), resolver().resolve());
        }
    }

    // Regression: the frontend service uses subject jffe@<instanceId>; the jffe prefix appears in
    // ServiceTokenPrefixes so isInternalServiceToken() would return SYSTEM_INTERNAL if checked first.
    // The UI check must win over the internal-service check.
    @Test
    void resolve_shouldReturnJfrogUi_whenFrontendServiceSubjectWouldOtherwiseMatchInternalServicePrefix() {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            auth.when(() -> AuthenticationHelper.isJFrogFrontendAuthentication(authentication)).thenReturn(true);

            assertEquals(RepoEventTriggerSource.JFROG_UI.name(), resolver().resolve());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Token layer (resolveByAuthenticationToken): an AccessTokenAuthentication subject is the authoritative signal and
    // is consulted before any HTTP header/URI heuristic. These cover the per-source subject markers (DC11 ordering).
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void resolve_shouldReturnJfrogAiAgent_whenTokenSubjectMarksJfrogAi() {
        assertTokenSubjectResolvedAs(RepoEventTriggerSource.JFROG_AI_AGENT, "jfrog-ai@01abc");
    }

    @Test
    void resolve_shouldReturnJfrogAiAgent_whenTokenSubjectMarksJfrogMcp() {
        assertTokenSubjectResolvedAs(RepoEventTriggerSource.JFROG_AI_AGENT, "jfrog-mcp@01abc");
    }

    @Test
    void resolve_shouldReturnUserAiAgent_whenTokenSubjectMarksCursor() {
        assertTokenSubjectResolvedAs(RepoEventTriggerSource.USER_AI_AGENT, "cursor/agent@01abc");
    }

    @Test
    void resolve_shouldReturnUserAiAgent_whenTokenSubjectMarksClaude() {
        assertTokenSubjectResolvedAs(RepoEventTriggerSource.USER_AI_AGENT, "claude/agent@01abc");
    }

    @Test
    void resolve_shouldReturnTerraform_whenTokenSubjectMarksTerraform() {
        assertTokenSubjectResolvedAs(RepoEventTriggerSource.TERRAFORM, "terraform@01abc");
    }

    @Test
    void resolve_shouldReturnJfrogCli_whenTokenSubjectMarksJfrogCli() {
        assertTokenSubjectResolvedAs(RepoEventTriggerSource.JFROG_CLI, "jfrog-cli@01abc");
    }

    // The token layer wins over conflicting HTTP signals: an AI-agent token classifies as USER_AI_AGENT even when the
    // request also carries the canonical UI Request-Agent header.
    @Test
    void resolve_shouldPreferTokenSubjectOverHttpUiSignal_whenBothPresent() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("cursor/agent@01abc");
        // The request carries the canonical UI header, but the token layer short-circuits before the HTTP layer is
        // reached, so these stubs go unused on purpose (lenient) — that they are never read IS the precedence proof.
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getHeader("Request-Agent")).thenReturn("artifactoryUI");
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class);
                MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("Mozilla/5.0");

            assertEquals(RepoEventTriggerSource.USER_AI_AGENT.name(), resolver().resolve());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Precedence ordering conflicts: AI-agent HTTP headers override non-AI token classifications (an AI agent may
    // authenticate with any token type). Token-AI still wins over HTTP-AI when both are present.
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    void resolve_shouldPreferAiHeader_overTerraformToken() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("terraform@01abc");
        HttpServletRequest request = requestWithHeader("X-JFrog-AI-Agent", "true");
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class);
                MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("terraform-provider/4.0.0");

            assertEquals(RepoEventTriggerSource.JFROG_AI_AGENT.name(), resolver().resolve());
        }
    }

    @Test
    void resolve_shouldPreferUserAiHeader_overCliToken() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("jfrog-cli@01abc");
        HttpServletRequest request = requestWithHeader("X-AI-Agent", "true");
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class);
                MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("jfrog-cli-go/2.74.0");

            assertEquals(RepoEventTriggerSource.USER_AI_AGENT.name(), resolver().resolve());
        }
    }

    @Test
    void resolve_shouldPreferAiTokenSubject_overAiHttpHeader() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("jfrog-ai@01abc");
        HttpServletRequest request = requestWithHeader("X-AI-Agent", "true");
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class);
                MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("agent/cursor");

            assertEquals(RepoEventTriggerSource.JFROG_AI_AGENT.name(), resolver().resolve());
        }
    }

    @Test
    void resolve_shouldPreferAiUserAgent_overInternalServiceToken() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("jfxr@01abc");
        HttpServletRequest request = request();
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class);
                MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("agent/claude mcp-client/1.0");

            assertEquals(RepoEventTriggerSource.USER_AI_AGENT.name(), resolver().resolve());
        }
    }

    @Test
    void resolve_shouldReturnNonAiTokenSource_whenNoAiHttpSignals() {
        AccessTokenAuthentication authentication = accessTokenAuthentication("terraform@01abc");
        HttpServletRequest request = request();
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class);
                MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn("Go-http-client/2.0");

            assertEquals(RepoEventTriggerSource.TERRAFORM.name(), resolver().resolve());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // HTTP layer: branches reached when there is no AccessTokenAuthentication (e.g. basic auth, anonymous). Each named
    // surface/tool has both a header/path signal and a User-Agent signal; the prior tests covered only a subset.
    // ---------------------------------------------------------------------------------------------------------------

    // Basic-auth requests are NOT AccessTokenAuthentication, so the token layer is skipped and the HTTP layer decides.
    // This is the exact shape of the reported curl: no UI signal on the wire => REST_API (not JFROG_UI).
    @Test
    void resolve_shouldReturnRestApi_whenBasicAuthCurlHasNoUiSignal() {
        assertRequestResolvedAs(RepoEventTriggerSource.REST_API, request(), "curl/8.4.0");
    }

    @Test
    void resolve_shouldReturnUserAiAgent_whenGenericAiAgentHeaderIsPresent() {
        assertRequestResolvedAs(RepoEventTriggerSource.USER_AI_AGENT, requestWithHeader("X-AI-Agent", "true"),
                "Mozilla/5.0");
    }

    @Test
    void resolve_shouldReturnJfrogAiAgent_whenUserAgentMarksJfrogMcp() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_AI_AGENT, request(), "jfrog-mcp/1.2.0");
    }

    // The canonical UI path signal (mirrors UiRequestUtils.isUiRestRequest): a /ui/api/ request URI => JFROG_UI even
    // without the Request-Agent header.
    @Test
    void resolve_shouldReturnJfrogUi_whenRequestUriIsUiApiPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/artifactory/ui/api/v1/ui/repositories");

        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_UI, request, "Mozilla/5.0");
    }

    // Characterizes current behavior: a "jfrog-frontend" User-Agent => JFROG_UI. NOTE: unlike the Request-Agent and
    // /ui/ path signals (grounded in UiRequestUtils.isUiRestRequest), this UA token appears nowhere else in the
    // codebase — confirm it matches the real frontend service UA before relying on it.
    @Test
    void resolve_shouldReturnJfrogUi_whenUserAgentMarksJfrogFrontend() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_UI, request(), "jfrog-frontend/7.0.0");
    }

    @Test
    void resolve_shouldReturnTerraform_whenRequestUriIsTerraformApiPath() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/artifactory/api/terraform/v1/modules");

        assertRequestResolvedAs(RepoEventTriggerSource.TERRAFORM, request, "go-resty/2.7.0");
    }

    @Test
    void resolve_shouldReturnTerraform_whenUserAgentMarksTerraformProvider() {
        assertRequestResolvedAs(RepoEventTriggerSource.TERRAFORM, request(), "Go-http-client/2.0 terraform-provider");
    }

    @Test
    void resolve_shouldReturnJfrogCli_whenUserAgentStartsWithJfrogSlash() {
        assertRequestResolvedAs(RepoEventTriggerSource.JFROG_CLI, request(), "jfrog/2.74.0");
    }

    private void assertTokenSubjectResolvedAs(RepoEventTriggerSource expected, String subject) {
        AccessTokenAuthentication authentication = accessTokenAuthentication(subject);
        try (MockedStatic<AuthenticationHelper> auth = mockStatic(AuthenticationHelper.class)) {
            auth.when(AuthenticationHelper::getAuthentication).thenReturn(authentication);

            assertEquals(expected.name(), resolver().resolve());
        }
    }

    private void assertRequestResolvedAs(RepoEventTriggerSource expected, HttpServletRequest request,
            String userAgent) {
        try (MockedStatic<RequestThreadLocal> rtl = mockStatic(RequestThreadLocal.class)) {
            rtl.when(RequestThreadLocal::getRequest).thenReturn(request);
            rtl.when(RequestThreadLocal::getUserAgent).thenReturn(userAgent);

            assertEquals(expected.name(), resolver().resolve());
        }
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRequestURI()).thenReturn("/artifactory/api/repositories/libs-release-local");
        return request;
    }

    private static HttpServletRequest requestWithHeader(String headerName, String headerValue) {
        HttpServletRequest request = request();
        lenient().when(request.getHeader(anyString())).thenAnswer(invocation ->
                headerName.equals(invocation.getArgument(0)) ? headerValue : null);
        return request;
    }

    private static AccessTokenAuthentication accessTokenAuthentication(String subject) {
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        JwtAccessToken accessToken = mock(JwtAccessToken.class);
        when(authentication.getAccessToken()).thenReturn(accessToken);
        when(accessToken.getSubject()).thenReturn(subject);
        return authentication;
    }
}
