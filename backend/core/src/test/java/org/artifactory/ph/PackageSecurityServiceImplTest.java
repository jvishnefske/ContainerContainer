package org.artifactory.ph;

import com.google.common.collect.Lists;
import com.jfrog.ph.acl.exception.PackageException;
import com.jfrog.ph.acl.model.ArtifactScopeAction;
import com.jfrog.ph.acl.model.ArtifactScopeTarget;
import com.jfrog.ph.acl.model.PackageAuthTokenCacheKey;
import com.jfrog.ph.acl.model.PackageTokenType;
import org.apache.http.HttpStatus;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.access.CreatedTokenInfo;
import org.artifactory.api.security.access.TokenNotFoundException;
import org.artifactory.api.security.access.UserTokenSpec;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.model.security.UserImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.security.InternalSecurityService;
import org.artifactory.security.SaltedPassword;
import org.artifactory.security.SimpleUser;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.access.CreatedTokenInfoImpl;
import org.artifactory.security.props.auth.OauthManager;
import org.artifactory.security.props.auth.model.AuthenticationModel;
import org.artifactory.security.providermgr.ArtifactoryCacheKey;
import org.artifactory.security.token.ArtifactoryTokenProvider;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.GrpcAuthClient;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.token.TokenClient;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.access.token.JwtAccessTokenImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

class PackageSecurityServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private InternalSecurityService securityService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    AccessService accessService;
    @Mock
    GrpcAuthClient grpcAuthClient;
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    UserGroupStoreService userGroupStoreService;
    @Mock
    OauthManager tokenManager;
    @Mock
    private LoginResult loginResult;
    @Mock
    private AccessPrincipal principal;
    @Mock
    private ArtifactoryTokenProvider tokenProvider;
    @Mock
    private TokenClient tokenClient;

    private PackageSecurityServiceImpl packageSecurityService;
    private AutoCloseable mocks;

    @BeforeEach
    public void setUp() {
        mocks = openMocks(this);
        when(accessService.getGrpcAuthClient()).thenReturn(grpcAuthClient);
        packageSecurityService = new PackageSecurityServiceImpl(securityService, authorizationService, accessService,
                authenticationManager, userGroupStoreService, tokenManager, tokenProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testCanRead() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        assertFalse(packageSecurityService.canRead("repo", "path"));
        verify(authorizationService, times(1)).canRead(eq(RepoPathFactory.create("repo", "path")));

        reset(securityService, authorizationService);

        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        assertTrue(packageSecurityService.canRead("repo", "path"));
        verify(authorizationService, times(1)).canRead(eq(RepoPathFactory.create("repo", "path")));
    }

    @Test
    void testCanWrite() {
        when(authorizationService.canDeploy(any(RepoPath.class))).thenReturn(false);
        assertFalse(packageSecurityService.canWrite("repo", "path"));
        verify(authorizationService, times(1)).canDeploy(eq(RepoPathFactory.create("repo", "path")));

        reset(securityService, authorizationService);

        when(authorizationService.canDeploy(any(RepoPath.class))).thenReturn(true);
        assertTrue(packageSecurityService.canWrite("repo", "path"));
        verify(authorizationService, times(1)).canDeploy(eq(RepoPathFactory.create("repo", "path")));
    }

    @Test
    void testCanDelete() {
        when(authorizationService.canDelete(any(RepoPath.class))).thenReturn(false);
        assertFalse(packageSecurityService.canDelete("repo", "path"));
        verify(authorizationService, times(1)).canDelete(eq(RepoPathFactory.create("repo", "path")));

        reset(securityService, authorizationService);

        when(authorizationService.canDelete(any(RepoPath.class))).thenReturn(true);
        assertTrue(packageSecurityService.canDelete("repo", "path"));
        verify(authorizationService, times(1)).canDelete(eq(RepoPathFactory.create("repo", "path")));
    }

    @Test
    void testCanAnnotate() {
        when(authorizationService.canAnnotate(any(RepoPath.class))).thenReturn(false);
        assertFalse(packageSecurityService.canAnnotate("repo", "path"));
        verify(authorizationService, times(1)).canAnnotate(eq(RepoPathFactory.create("repo", "path")));

        reset(securityService, authorizationService);

        when(authorizationService.canAnnotate(any(RepoPath.class))).thenReturn(true);
        assertTrue(packageSecurityService.canAnnotate("repo", "path"));
        verify(authorizationService, times(1)).canAnnotate(eq(RepoPathFactory.create("repo", "path")));
    }

    @Test
    void testCanManage() {
        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(false);
        assertFalse(packageSecurityService.canManage("repo", "path"));
        verify(authorizationService, times(1)).canManage(eq(RepoPathFactory.create("repo", "path")));

        reset(securityService, authorizationService);

        when(authorizationService.canManage(any(RepoPath.class))).thenReturn(true);
        assertTrue(packageSecurityService.canManage("repo", "path"));
        verify(authorizationService, times(1)).canManage(eq(RepoPathFactory.create("repo", "path")));
    }

    @Test
    void testCallAsSystem() throws Exception {
        Mockito.doAnswer(invocationOnMock -> ((Callable) invocationOnMock.getArgument(0)).call())
                .when(securityService).callAsSystem(any(Callable.class));
        Callable<Boolean> callable = () -> true;
        assertTrue(securityService.callAsSystem(callable));
        verify(securityService, times(1)).callAsSystem(eq(callable));
    }

    @Test
    void testGetCurrentUserName() {
        when(securityService.currentUsername()).thenReturn("user");
        assertEquals("user", packageSecurityService.getCurrentUserName());
        verify(securityService, times(1)).currentUsername();
    }

    @Test
    void testIsAuthenticated() {
        when(securityService.isAuthenticated()).thenReturn(true);
        assertTrue(packageSecurityService.isAuthenticated());
        verify(securityService, times(1)).isAuthenticated();
    }

    @Test
    void testLoginAndGenerateAccessTokenWithBasicAuth() throws PackageException {
        String user = "USER";
        String password = "PASSWORD";
        Long genericTokensExpirationTimeSecs = (long) (86400 * 30);
        Authentication authentication = mock(Authentication.class);
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(user, password);
        when(authenticationManager.authenticate(authenticationToken)).thenReturn(authentication);
        UserTokenSpec tokenSpec = UserTokenSpec.create(user)
                .expiresIn(genericTokensExpirationTimeSecs)
                .refreshable(false)
                .scope(Lists.newArrayList("member-of-groups:*"));
        CreatedTokenInfo createdTokenInfo = new CreatedTokenInfoImpl("a", "b", "c", "d", 1L, "f");
        when(accessService.createToken(any(UserTokenSpec.class), anyBoolean(), anyBoolean())).thenReturn(
                createdTokenInfo);
        packageSecurityService.loginAndGenerateAccessTokenWithBasicAuth(user, password);
        verify(accessService, times(1)).createToken(refEq(tokenSpec), anyBoolean(), anyBoolean());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystem() throws Exception {
        ServiceId serviceId = ServiceId.fromFormattedName("jfrt@0123456789");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        givenTokenClientReturning("the-token");
        givenCallAsSystemRunsTheCallable();

        String token = packageSecurityService.createRepositoryScopedTokenAsSystem(
                List.of(ArtifactScopeTarget.ofRepo("plugins")), "anonymous",
                EnumSet.of(ArtifactScopeAction.READ, ArtifactScopeAction.WRITE, ArtifactScopeAction.ANNOTATE), 300L);

        assertEquals("the-token", token);
        ArgumentCaptor<TokenRequest> requestCaptor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient).create(requestCaptor.capture());
        TokenRequest request = requestCaptor.getValue();
        assertEquals(List.of("artifact:plugins/**:r", "artifact:plugins/**:w", "artifact:plugins/**:a"),
                request.getScope());
        assertEquals(List.of(serviceId.getFormattedName()), request.getAudience());
        assertEquals(300L, request.getExpiresIn());
        assertFalse(request.isRefreshable());
        // Artifactory denies the login for a subject-less token, so the request would never reach the scope check
        assertEquals("jfrt@0123456789/users/anonymous", request.getSubject());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemScopesEachRepositorySeparately() throws Exception {
        when(accessService.getArtifactoryServiceId()).thenReturn(ServiceId.fromFormattedName("jfrt@0123456789"));
        givenTokenClientReturning("the-token");
        givenCallAsSystemRunsTheCallable();

        packageSecurityService.createRepositoryScopedTokenAsSystem(List.of(ArtifactScopeTarget.ofRepo("first")),
                "anonymous", EnumSet.of(ArtifactScopeAction.READ), 300L);
        packageSecurityService.createRepositoryScopedTokenAsSystem(List.of(ArtifactScopeTarget.ofRepo("second")),
                "anonymous", EnumSet.of(ArtifactScopeAction.READ), 300L);

        ArgumentCaptor<TokenRequest> requestCaptor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient, times(2)).create(requestCaptor.capture());
        assertEquals(List.of("artifact:first/**:r"), requestCaptor.getAllValues().get(0).getScope());
        assertEquals(List.of("artifact:second/**:r"), requestCaptor.getAllValues().get(1).getScope());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemGrantsMultipleTargetsInOneToken() throws Exception {
        when(accessService.getArtifactoryServiceId()).thenReturn(ServiceId.fromFormattedName("jfrt@0123456789"));
        givenTokenClientReturning("the-token");
        givenCallAsSystemRunsTheCallable();

        packageSecurityService.createRepositoryScopedTokenAsSystem(
                List.of(ArtifactScopeTarget.ofRepo("first"), ArtifactScopeTarget.of("second", "plugins/foo")),
                "anonymous", EnumSet.of(ArtifactScopeAction.READ, ArtifactScopeAction.WRITE), 300L);

        ArgumentCaptor<TokenRequest> requestCaptor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient).create(requestCaptor.capture());
        assertEquals(List.of("artifact:first/**:r", "artifact:first/**:w", "artifact:second/plugins/foo/**:r",
                "artifact:second/plugins/foo/**:w"), requestCaptor.getValue().getScope());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemScopesTheBareKeyOfACacheRepository() throws Exception {
        when(accessService.getArtifactoryServiceId()).thenReturn(ServiceId.fromFormattedName("jfrt@0123456789"));
        givenTokenClientReturning("the-token");
        givenCallAsSystemRunsTheCallable();

        packageSecurityService.createRepositoryScopedTokenAsSystem(
                List.of(ArtifactScopeTarget.ofRepo("plugins-cache")), "anonymous",
                EnumSet.of(ArtifactScopeAction.READ), 300L);

        ArgumentCaptor<TokenRequest> requestCaptor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient).create(requestCaptor.capture());
        assertEquals(List.of("artifact:plugins/**:r"), requestCaptor.getValue().getScope());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemMintsForTheGivenSubject() throws Exception {
        when(accessService.getArtifactoryServiceId()).thenReturn(ServiceId.fromFormattedName("jfrt@0123456789"));
        givenTokenClientReturning("the-token");
        givenCallAsSystemRunsTheCallable();

        packageSecurityService.createRepositoryScopedTokenAsSystem(
                List.of(ArtifactScopeTarget.ofRepo("plugins")), "some-service-user",
                EnumSet.of(ArtifactScopeAction.READ), 300L);

        ArgumentCaptor<TokenRequest> requestCaptor = ArgumentCaptor.forClass(TokenRequest.class);
        verify(tokenClient).create(requestCaptor.capture());
        assertEquals("jfrt@0123456789/users/some-service-user", requestCaptor.getValue().getSubject());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemRejectsEmptyActions() {
        PackageException e = assertThrows(PackageException.class,
                () -> packageSecurityService.createRepositoryScopedTokenAsSystem(
                        List.of(ArtifactScopeTarget.ofRepo("plugins")), "anonymous",
                        EnumSet.noneOf(ArtifactScopeAction.class), 300L));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemRejectsEmptyTargets() {
        PackageException e = assertThrows(PackageException.class,
                () -> packageSecurityService.createRepositoryScopedTokenAsSystem(List.of(), "anonymous",
                        EnumSet.of(ArtifactScopeAction.READ), 300L));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemRejectsBlankRepoKeyInTarget() {
        PackageException e = assertThrows(PackageException.class,
                () -> packageSecurityService.createRepositoryScopedTokenAsSystem(
                        List.of(ArtifactScopeTarget.ofRepo(" ")), "anonymous",
                        EnumSet.of(ArtifactScopeAction.READ), 300L));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemRejectsBlankSubject() {
        PackageException e = assertThrows(PackageException.class,
                () -> packageSecurityService.createRepositoryScopedTokenAsSystem(
                        List.of(ArtifactScopeTarget.ofRepo("plugins")), " ",
                        EnumSet.of(ArtifactScopeAction.READ), 300L));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @Test
    void testCreateRepositoryScopedTokenAsSystemWrapsAccessFailure() throws Exception {
        when(accessService.getArtifactoryServiceId()).thenReturn(ServiceId.fromFormattedName("jfrt@0123456789"));
        AccessClient accessClient = mock(AccessClient.class);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.token()).thenReturn(tokenClient);
        when(tokenClient.create(any(TokenRequest.class))).thenThrow(new AccessClientException("access is down"));
        givenCallAsSystemRunsTheCallable();

        PackageException e = assertThrows(PackageException.class,
                () -> packageSecurityService.createRepositoryScopedTokenAsSystem(
                        List.of(ArtifactScopeTarget.ofRepo("plugins")), "anonymous",
                        EnumSet.of(ArtifactScopeAction.READ), 300L));
        assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getStatus());
    }

    private void givenTokenClientReturning(String tokenValue) {
        AccessClient accessClient = mock(AccessClient.class);
        TokenResponse tokenResponse = mock(TokenResponse.class);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.token()).thenReturn(tokenClient);
        when(tokenClient.create(any(TokenRequest.class))).thenReturn(tokenResponse);
        when(tokenResponse.getTokenValue()).thenReturn(tokenValue);
    }

    private void givenCallAsSystemRunsTheCallable() throws Exception {
        Mockito.doAnswer(invocation -> ((Callable<?>) invocation.getArgument(0)).call())
                .when(securityService).callAsSystem(any(Callable.class));
    }

    @Test
    void testRevokeToken() throws PackageException {
        packageSecurityService.revokeToken("token");
        verify(accessService).revokeToken("token");
    }

    @Test
    void testRevokeTokenOnNonRevocableToken() throws PackageException {
        doThrow(new AccessClientException("")).when(accessService).revokeToken("NonRevocableToken");
        packageSecurityService.revokeToken("NonRevocableToken");
        verify(accessService).revokeToken("NonRevocableToken");
    }

    @Test
    void testRevokeTokenOnNonExistingToken() {
        doThrow(new TokenNotFoundException("")).when(accessService).revokeToken("NonExistingToken");
        try {
            packageSecurityService.revokeToken("NonExistingToken");
            fail("Expected PackageException");
        } catch (PackageException e) {
            verify(accessService).revokeToken("NonExistingToken");
            assertEquals("Logout locally failed", e.getMessage());
            assertEquals(HttpStatus.SC_BAD_REQUEST, e.getStatus());
        }
    }

    @Test
    void testGetCurrentUserDetailsWithAccessTokenAuthentication() throws PackageException {
        UserImpl userInfo = new UserImpl("user1");
        userInfo.setEmail("user1@jfrog.com");
        String token = "stamtoken";
        String tokenValue = "stamtokenvalue";
        SecurityContext securityContext = new SecurityContextImpl();
        SimpleUser user = new SimpleUser(userInfo);
        MockedStatic<JwtAccessTokenImpl> jwtAccessTokenMockedStatic = mockStatic(JwtAccessTokenImpl.class);
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        when(jwtAccessToken.getTokenValue()).thenReturn(tokenValue);
        when(accessService.extractSubjectUsername(jwtAccessToken)).thenReturn("user1");
        jwtAccessTokenMockedStatic.when(() -> JwtAccessTokenImpl.parseTokenValue(token))
                .thenReturn(jwtAccessToken);
        Authentication authentication =
                new AccessTokenAuthentication(token, user, null);
        authentication.setAuthenticated(true);
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        com.jfrog.ph.acl.model.SimpleUser resultUser = packageSecurityService.getCurrentUserDetails();
        assertNotNull(resultUser);
        assertEquals("user1", resultUser.getName());
        assertEquals(tokenValue, resultUser.getPassword());
        assertEquals("user1@jfrog.com", resultUser.getEmail());
        jwtAccessTokenMockedStatic.close();
    }

    @Test
    void testGetCurrentUserDetailsWhenUnAuthenticated() {
        try {
            packageSecurityService.getCurrentUserDetails();
            fail("Expected PackageException");
        } catch (PackageException e) {
            assertEquals("User is unauthenticated", e.getMessage());
        }
    }

    @Test
    void testGetCurrentUserDetails() throws PackageException {
        UserImpl userInfo = new UserImpl("user1");
        userInfo.setEmail("user1@jfrog.com");
        String password = "PASSWORD";
        userInfo.setPassword(new SaltedPassword(password, ""));
        SecurityContext securityContext = new SecurityContextImpl();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(new SimpleUser(userInfo), password, null);
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(userGroupStoreService.findUser("user1")).thenReturn(userInfo);
        when(securityService.createEncryptedPasswordIfNeeded(userInfo, password)).thenReturn(password);
        com.jfrog.ph.acl.model.SimpleUser resultUser = packageSecurityService.getCurrentUserDetails();
        assertNotNull(resultUser);
        assertEquals("user1", resultUser.getName());
        assertEquals(password, resultUser.getPassword());
        assertEquals("user1@jfrog.com", resultUser.getEmail());
    }

    @Test
    void principalNameReceived() {
        final String accessToken = "accessToken";
        final String expectedPrincipalName = "principalName";
        when(loginResult.getPrincipal()).thenReturn(principal);
        when(loginResult.isSuccessful()).thenReturn(true);
        when(grpcAuthClient.authenticate("", accessToken)).thenReturn(loginResult);
        when(principal.getName()).thenReturn(expectedPrincipalName);
        final String principalName = packageSecurityService.getUserNameByAccessTokenValue(accessToken);
        assertNotNull(principalName);
        assertEquals(expectedPrincipalName, principalName);
    }

    @Test
    void principalNameIsNullLoginNotSuccessful() {
        final String accessToken = "accessToken";
        when(loginResult.getPrincipal()).thenReturn(principal);
        when(loginResult.isSuccessful()).thenReturn(false);
        when(grpcAuthClient.authenticate("", accessToken)).thenReturn(loginResult);
        when(principal.getName()).thenReturn("notNeeded");
        final String principalName = packageSecurityService.getUserNameByAccessTokenValue(accessToken);
        assertNull(principalName);
    }

    @Test
    void principalNameIsNullLoginSuccessful() {
        final String accessToken = "accessToken";
        when(loginResult.getPrincipal()).thenReturn(null);
        when(loginResult.isSuccessful()).thenReturn(true);
        when(grpcAuthClient.authenticate("", accessToken)).thenReturn(loginResult);
        final String principalName = packageSecurityService.getUserNameByAccessTokenValue(accessToken);
        assertNull(principalName);
    }

    @Test
    void principalNameWithAccessException() {
        final String accessToken = "accessToken";
        when(loginResult.getPrincipal()).thenReturn(principal);
        when(loginResult.isSuccessful()).thenReturn(false);
        doThrow(new AccessClientException("Failed to execute authenticate user with error Bad credentials")).when(
                grpcAuthClient).authenticate("", accessToken);
        final String principalName = packageSecurityService.getUserNameByAccessTokenValue(accessToken);
        assertNull(principalName);
    }

    @Test
    void getToken() {
        final PackageAuthTokenCacheKey cacheKey = mock(PackageAuthTokenCacheKey.class);
        when(cacheKey.getTokenType()).thenReturn(PackageTokenType.NPM);
        when(tokenProvider.getToken(any())).thenReturn(mock(AuthenticationModel.class));
        final String principalName = packageSecurityService.getToken(cacheKey);
        verify(tokenProvider).getToken(any(ArtifactoryCacheKey.class));
        assertNull(principalName);
    }
}