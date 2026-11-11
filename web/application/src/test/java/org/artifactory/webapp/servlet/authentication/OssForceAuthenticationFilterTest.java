package org.artifactory.webapp.servlet.authentication;

import org.apache.http.HttpStatus;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.authentication.ArtifactoryAuthenticationType;
import org.artifactory.descriptor.repo.HttpRepoDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.props.auth.ApiKeyManager;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.common.token.TokenVerifyResult;
import org.jfrog.access.model.Realm;
import org.jfrog.access.token.JwtAccessToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;

import static org.artifactory.util.SessionUtils.LAST_USER_KEY;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


public class OssForceAuthenticationFilterTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryApplicationContext artifactoryContext;
    @Mock
    private RepositoryService repoService;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private SecurityService securityService;
    @Mock
    private AccessService accessService;
    private static final String VIRTUAL_REPO = "virtual-repo";
    private VirtualRepoDescriptor virtualRepoDescriptor;
    private final OssForceAuthenticationFilter filter = new OssForceAuthenticationFilter();
    private AutoCloseable mocks;

    @BeforeEach
    public void beforeMethod() {
        mocks = MockitoAnnotations.openMocks(this);
        filter.setRepoService(repoService);
        filter.setSecurityService(securityService);
        filter.setAccessClient(accessService);
        virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Maven);
        virtualRepoDescriptor.setForceMavenAuthentication(true);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(virtualRepoDescriptor);
        when(artifactoryContext.beanForType(InternalRepositoryService.class)).thenReturn(internalRepositoryService);
        when(artifactoryContext.beanForType(AccessService.class)).thenReturn(accessService);
        when(accessService.isRealmAuthenticationEnabled(Realm.HTTPSSO)).thenReturn(false);

        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(virtualRepoDescriptor);
    }

    @AfterEach
    public void afterMethod() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    public void denyWhenAuthorizationHeaderPresent() {
        // Enable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46MTIzNDU=");
        assertFalse(filter.acceptFilter(request));
        // Disable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(false);
    }

    @Test
    public void denyWhenAuthorizationHeaderButLinkIsBadPresent() {
        // Enable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui/native");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46MTIzNDU=");
        assertFalse(filter.acceptFilter(request));
        // Disable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(false);
    }

    @Test
    public void allowWhenAuthorizationHeaderAndNativeInPathPresent() {
        // Enable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui/native/" + VIRTUAL_REPO);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic YW5vbnltb3VzOjEyMzQ1");
        assertTrue(filter.acceptFilter(request));
        // Disable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(false);
    }

    @Test
    public void denyWhenAuthorizationHeaderAndTokenIsWrong() {
        // Enable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui/native/" + VIRTUAL_REPO);
        when(accessService.verifyAndGetResult(anyString())).thenReturn(null);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some_fake_token");
        assertTrue(filter.acceptFilter(request));
        // Disable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(false);
    }

    /**
     * The case (RTDEV-84183): a bearer token that cannot be verified (e.g. signed with an
     * unsupported algorithm such as HS256 against the RSA verifier, or rejected by
     * a transient Access-service error) must not surface as a server error.
     * This test drives the path that previously propagated
     * {@link AccessClientException} -> HTTP 500 and asserts it is now swallowed:
     * the request is treated as unauthenticated so the downstream auth filters
     * can answer with a proper 401.
     * <p>
     * The stub uses the exact exception type thrown by
     * {@code AccessServiceImpl.verifyAndGetResult(String)} in production so this test
     * breaks if someone narrows the broad {@code catch (Exception e)} in
     * {@code verifyBearerAuthPresent} back to a more specific type and accidentally
     * re-opens the 500 path.
     */
    @Test
    public void acceptWhenBearerTokenVerificationThrows() {
        when(accessService.isAnonymousAccessEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some.malformed.hs256.token");
        when(accessService.verifyAndGetResult(anyString()))
                .thenThrow(new AccessClientException("Failed to verify access token"));
        // The filter must (a) not propagate the verification failure and (b) still
        // accept the request on a force-auth Maven virtual repo so a 401 challenge
        // is sent downstream.
        assertTrue(assertDoesNotThrow(() -> filter.acceptFilter(request)));
        when(accessService.isAnonymousAccessEnabled()).thenReturn(false);
    }

    @Test
    public void allowWithoutGlobalAnonymous() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui/native/" + VIRTUAL_REPO);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic YW5vbnltb3VzOjEyMzQ1");
        // Doesn't need proceed force authentication if authorization header is present
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenAuthorizationHeaderPresentButAnonymous() {
        // Enable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui/native/" + VIRTUAL_REPO);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic YW5vbnltb3VzOjEyMzQ1");
        assertTrue(filter.acceptFilter(request));
        request.removeHeader(HttpHeaders.AUTHORIZATION);

        JwtAccessToken jwtAccessToken = Mockito.mock(JwtAccessToken.class);
        TokenVerifyResult tokenVerifyResult = Mockito.mock(TokenVerifyResult.class);
        when(tokenVerifyResult.getAccessToken()).thenReturn(jwtAccessToken);
        when(tokenVerifyResult.getAccessToken().getSubject()).thenReturn("anonymous");
        when(accessService.verifyAndGetResult(anyString())).thenReturn(tokenVerifyResult);

        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer some_fake_token");
        assertTrue(filter.acceptFilter(request));
        // Disable full anonymous access
        when(accessService.isAnonymousAccessEnabled()).thenReturn(false);
    }

    @Test
    public void denyWhenApiKeyHeaderPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        request.addHeader(ApiKeyManager.API_KEY_HEADER, "AP1234..");
        assertFalse(filter.acceptFilter(request));
        request.addHeader(ApiKeyManager.OLD_API_KEY_HEADER, "AP1234...");
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenSsoUserPresent() {
        when(securityService.isHttpSsoProxied()).thenReturn(true);
        when(securityService.getHttpSsoRemoteUserRequestVariable()).thenReturn("user");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        // attribute
        request.setAttribute("user", "text");
        assertFalse(filter.acceptFilter(request));
        // header
        request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        request.addHeader("user", "text");
        assertFalse(filter.acceptFilter(request));
        // remote user
        request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        request.setRemoteUser("text");
        assertFalse(filter.acceptFilter(request));
        // sso enabled but no user --> should accept
        request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertTrue(filter.acceptFilter(request));
        // reset
        when(securityService.isHttpSsoProxied()).thenReturn(false);
    }

    @Test
    public void denyWhenHttpSessionPresentAndAuthenticated() {
        MockHttpServletRequest request = createRequestWithSessionMock(true);
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void acceptWhenHttpSessionPresentButNotAuthenticated() {
        MockHttpServletRequest request = createRequestWithSessionMock(false);
        assertTrue(filter.acceptFilter(request));
    }

    private MockHttpServletRequest createRequestWithSessionMock(boolean authenticated) {
        Authentication authentication = Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(authenticated);
        HttpSession httpSession = Mockito.mock(HttpSession.class);
        when(httpSession.getAttribute(LAST_USER_KEY)).thenReturn(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        request.setSession(httpSession);
        return request;
    }

    @Test
    public void denyWhenReachingNonRepoEndpoints() {
        // Creating dummy repo what will answer to every request as a valid maven virtual
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Maven);
        when(repoService.repoDescriptorByKey("ui")).thenReturn(virtualRepoDescriptor);
        when(repoService.repoDescriptorByKey("api")).thenReturn(virtualRepoDescriptor);
        when(repoService.repoDescriptorByKey("webapp")).thenReturn(virtualRepoDescriptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui");
        assertFalse(filter.acceptFilter(request));
        request = new MockHttpServletRequest("GET", "webapp");
        assertFalse(filter.acceptFilter(request));
        request = new MockHttpServletRequest("GET", "api");
        assertFalse(filter.acceptFilter(request));
        request = new MockHttpServletRequest("GET", "");
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void acceptWhenAnonymousMaven() {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Maven);
        virtualRepoDescriptor.setForceMavenAuthentication(true);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(virtualRepoDescriptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertTrue(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenAnonymousGeneric() {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Generic);
        virtualRepoDescriptor.setForceMavenAuthentication(true);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(virtualRepoDescriptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenAnonymousMavenLocal() {
        LocalRepoDescriptor localRepoDescriptor = new LocalRepoDescriptor();
        localRepoDescriptor.setType(RepoType.Maven);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(localRepoDescriptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenAnonymousMavenRemote() {
        HttpRepoDescriptor httpRepoDescriptor = new HttpRepoDescriptor();
        httpRepoDescriptor.setType(RepoType.Maven);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(httpRepoDescriptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenAnonymousNoRepo() {
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void denyWhenAnonymousNoForceConfiguration() {
        VirtualRepoDescriptor virtualRepoDescriptor = new VirtualRepoDescriptor();
        virtualRepoDescriptor.setType(RepoType.Maven);
        virtualRepoDescriptor.setForceMavenAuthentication(false);
        when(repoService.repoDescriptorByKey(VIRTUAL_REPO)).thenReturn(virtualRepoDescriptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertFalse(filter.acceptFilter(request));
    }

    @Test
    public void responseContainsChallenge() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        // Contains a challenge
        assertTrue(response.containsHeader(HttpHeaders.WWW_AUTHENTICATE));
        assertEquals(response.getStatus(), HttpStatus.SC_UNAUTHORIZED);
    }

    @Test
    public void testRequiresReAuthenticationTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", VIRTUAL_REPO);
        assertTrue(filter.requiresReAuthentication(request, null));
    }

    @Test
    public void testRequiresReAuthenticationFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "ui");
        assertFalse(filter.requiresReAuthentication(request, null));
    }

    @Test
    void getType() {
        OssForceAuthenticationFilter ossForceAuthenticationFilter = new OssForceAuthenticationFilter();
        assertEquals(ArtifactoryAuthenticationType.OSS_FORCE_AUTH, ossForceAuthenticationFilter.getType());
    }

    @Test
    void getCacheKey() {
        OssForceAuthenticationFilter ossForceAuthenticationFilter = new OssForceAuthenticationFilter();
        assertNull(ossForceAuthenticationFilter.getCacheKey(null));
    }

    @Test
    void getLoginIdentifier() {
        OssForceAuthenticationFilter ossForceAuthenticationFilter = new OssForceAuthenticationFilter();
        assertNull(ossForceAuthenticationFilter.getLoginIdentifier(null));
    }

    @Test
    void testInit() {
        OssForceAuthenticationFilter ossForceAuthenticationFilter = new OssForceAuthenticationFilter();
        assertDoesNotThrow(() -> ossForceAuthenticationFilter.init(null));
    }

    @Test
    void testDestroy() {
        OssForceAuthenticationFilter ossForceAuthenticationFilter = new OssForceAuthenticationFilter();
        assertDoesNotThrow(ossForceAuthenticationFilter::destroy);
    }
}