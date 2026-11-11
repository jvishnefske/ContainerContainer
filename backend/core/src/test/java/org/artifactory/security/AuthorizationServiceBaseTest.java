package org.artifactory.security;

import jakarta.servlet.http.HttpServletRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.model.security.BuildPermissionTargetImpl;
import org.artifactory.model.security.MutableRepoAclImpl;
import org.artifactory.model.security.ReleaseBundlePermissionTargetImpl;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.request.RequestThreadLocal;
import org.artifactory.request.RequestWrapper;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.permission.GrpcPermissionsClient;
import org.jfrog.access.client.permission.PermissionsClient;
import org.jfrog.access.client.permission.customdata.RepoCustomData;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.PermissionScopeResourceType;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.rest.permission.Permission;
import org.jfrog.access.rest.permission.PermissionActions;
import org.jfrog.access.rest.permission.PermissionActionsResponse;
import org.jfrog.access.rest.permission.PermissionResponse;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.common.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.repo.trash.TrashService.TRASH_KEY;
import static org.artifactory.security.AuthorizationServiceBase.ACCESS_PRINCIPAL_ATTRIBUTE;
import static org.artifactory.util.RepoPathUtils.removeDashCache;
import static org.jfrog.access.common.ResourceType.BUILD;
import static org.jfrog.access.common.ResourceType.RELEASE_BUNDLE;
import static org.jfrog.access.common.ResourceType.REPO;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceBaseTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AccessService accessService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private UserGroupStoreService userGroupStoreService;
    @Mock
    private AccessClient accessClient;
    @Mock
    private AuthClient authClient;
    @Mock
    private PermissionsClient permissionsClient;
    private AuthorizationServiceImpl authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationServiceImpl();
        authorizationService.setAccessService(accessService);
        authorizationService.setSecurityService(securityService);
        authorizationService.setRepositoryService(repositoryService);
        authorizationService.setProjectsService(projectsService);
        authorizationService.setBuildFacade(buildFacade);
        authorizationService.setUserGroupStoreService(userGroupStoreService);
    }

    @AfterEach
    void tearDown() {
        RequestThreadLocal.unbind();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCanAccessTrashcan(boolean admin) {
        doReturn(admin).when(securityService).isAdmin();
        assertEquals(admin, authorizationService.canAccessTrashcan());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testCanRestoreFromTrashcan(boolean admin) {
        doReturn(admin).when(securityService).isAdmin();
        RepoPath repoPath = RepoPathFactory.create(TRASH_KEY, "generic-local/path/to/file");
        assertEquals(admin, authorizationService.canRestoreFromTrashcan(repoPath));
    }

    @Test
    void testIsGrantedByAccessToken() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        doReturn("cool-token").when(jwtAccessToken).getTokenValue();
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        doReturn(jwtAccessToken).when(authentication).getAccessToken();
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
        LoginResult loginResult = mock(LoginResult.class);
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        doReturn(accessPrincipal).when(loginResult).getPrincipal();
        doReturn(loginResult).when(authClient).loginByAuthHeader(anyString());
        PermissionScope permissionScope = mock(PermissionScope.class);
        doReturn(true).when(authClient).principalCanDo(accessPrincipal, permissionScope, true);
        assertTrue(authorizationService.isGrantedByAccessToken(permissionScope));
        verify(authClient).principalCanDo(accessPrincipal, permissionScope, true);
    }

    @Test
    void testIsGrantedByAccessTokenExceptionThrown() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        doReturn("cool-token").when(jwtAccessToken).getTokenValue();
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        doReturn(jwtAccessToken).when(authentication).getAccessToken();
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
        doThrow(new RuntimeException("CacheLoader returned null for key jenkinsintegrationtoken.")).when(authClient)
                .loginByAuthHeader(anyString());
        PermissionScope permissionScope = mock(PermissionScope.class);
        assertFalse(authorizationService.isGrantedByAccessToken(permissionScope));
    }

    @Test
    void testIsGrantedByAccessTokenNotAccessToken() {
        AnonymousAuthenticationToken authentication = mock(AnonymousAuthenticationToken.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
        PermissionScope permissionScope = mock(PermissionScope.class);
        assertFalse(authorizationService.isGrantedByAccessToken(permissionScope));
        verify(authClient, never()).principalCanDo(any(), any());
    }

    @Test
    void testConvertNewAclCachedRepoKeysToRemote() {
        var repoPermissionTarget = new RepoPermissionTargetImpl("cool-pt", Set.of("generic-remote-cache"));
        MutableRepoAclImpl mutableRepoAcl = new MutableRepoAclImpl(repoPermissionTarget, null, null, 0);
        doReturn(Set.of("generic-remote")).when(repositoryService)
                .convertCachedRepoKeysToRemote(Set.of("generic-remote-cache"));
        RepoAcl repoAcl = authorizationService.convertNewAclCachedRepoKeysToRemote(mutableRepoAcl);
        assertEquals(repoAcl.getPermissionTarget().getRepoKeys(), Set.of("generic-remote"));
    }

    @ParameterizedTest
    @MethodSource("testValidateUnmodifiedPermissionTargetData")
    void testValidateUnmodifiedPermissionTarget(ResourceType resourceType, PermissionTarget permissionTarget) {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        PermissionActionsResponse permissionActionsResponse = PermissionActionsResponse.builder()
                .groupActions(Map.of("cool-group", List.of(Action.READ)))
                .userActions(Map.of("cool", List.of(Action.READ)))
                .build();
        var permissionResponse = new PermissionResponse();
        permissionResponse.actions(permissionActionsResponse);
        doReturn(Optional.of(permissionResponse)).when(permissionsClient)
                .findByResourceTypeAndDisplayName(resourceType, "cool-pt");
        assertDoesNotThrow(() -> authorizationService.validateUnmodifiedPermissionTarget(permissionTarget));
    }

    private static Stream<Arguments> testValidateUnmodifiedPermissionTargetData() {
        return Stream.of(
                Arguments.of(REPO, new RepoPermissionTargetImpl("cool-pt", Set.of(), List.of(), List.of())),
                Arguments.of(BUILD, new BuildPermissionTargetImpl("cool-pt")),
                Arguments.of(RELEASE_BUNDLE, new ReleaseBundlePermissionTargetImpl("cool-pt"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"cool-repo", "cool-repo-cache"})
    void testGetRepoPathAcls(String repoKey) {
        RepoPath repoPath = RepoPathFactory.create(repoKey, "cool-path");
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        doReturn(false).when(localRepoDescriptor).isCache();
        doReturn(localRepoDescriptor).when(repositoryService).localFederatedOrCachedRepoDescriptorByKey(repoKey);
        doReturn(false).when(buildFacade).isBuildInfoRepo(any());

        doReturn(accessClient).when(accessService).getAccessClient();
        GrpcPermissionsClient grpcPermissionsClient = mock(GrpcPermissionsClient.class);
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        RepoCustomData repoCustomData = new RepoCustomData(null, null, null, List.of("cool-repo"));
        doReturn(JsonUtils.getInstance().valueToString(repoCustomData)).when(permission1).getCustomData();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient)
                .findPermissionsByResourceType(ResourceType.REPO);

        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        List<RepoAcl> allRepoAcls = authorizationService.getRepoPathAcls(repoPath);
        assertEquals(1, allRepoAcls.size());
        assertEquals("cool-repo",
                allRepoAcls.getFirst().getPermissionTarget().getRepoKeys().stream().toList().getFirst());
    }

    @Test
    void userHasPermissionsOnRepositoryRootCache() {
        assertFalse(authorizationService.userHasPermissionsOnRepositoryRoot("cool-repo",
                Set.of(ArtifactoryPermission.values()), new HashMap<>()));
        assertFalse(authorizationService.userHasPermissionsOnRepositoryRoot("cool-repo",
                Set.of(ArtifactoryPermission.values()), null));
        assertFalse(authorizationService.userHasPermissionsOnRepositoryRoot(
                "cool-repo", Set.of(ArtifactoryPermission.READ), null));
        assertFalse(authorizationService.userHasPermissionsOnRepositoryRoot("cool-repo",
                Set.of(ArtifactoryPermission.values()), new HashMap<>(Map.of("cool-repo", false))));
        assertTrue(authorizationService.userHasPermissionsOnRepositoryRoot("cool-repo",
                Set.of(ArtifactoryPermission.values()), new HashMap<>(Map.of("cool-repo", true))));
    }

    @Test
    void testIsGrantedByAccessTokenWithPrincipalInRequest() {
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        PermissionScope permissionScope = mock(PermissionScope.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        RequestThreadLocal.bind(new RequestWrapper(request));
        when(request.getAttribute(ACCESS_PRINCIPAL_ATTRIBUTE)).thenReturn(accessPrincipal);
        when(accessService.getAccessClient()).thenReturn(accessClient);
        when(accessClient.cachedAuth()).thenReturn(authClient);
        authorizationService.isGrantedByAccessToken(permissionScope);
        verify(authClient, never()).loginByAuthHeader(any());
    }

    @Test
    void testIsGrantedByAccessTokenWithoutPrincipalInRequest() {
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        PermissionScope permissionScope = mock(PermissionScope.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        LoginResult loginResult = mock(LoginResult.class);
        when(loginResult.getPrincipal()).thenReturn(accessPrincipal);
        when(authClient.loginByAuthHeader(any())).thenReturn(loginResult);
        AccessTokenAuthentication tokenAuthentication =
                new AccessTokenAuthentication("token", mock(JwtAccessToken.class),
                        null, null);
        try (MockedStatic<AuthenticationHelper> authenticationHelper = mockStatic(AuthenticationHelper.class)) {
            authenticationHelper.when(AuthenticationHelper::getAuthentication).thenReturn(tokenAuthentication);
            RequestThreadLocal.bind(new RequestWrapper(request));
            when(request.getAttribute(ACCESS_PRINCIPAL_ATTRIBUTE)).thenReturn(null);
            when(accessService.getAccessClient()).thenReturn(accessClient);
            when(accessClient.cachedAuth()).thenReturn(authClient);
            authorizationService.isGrantedByAccessToken(permissionScope);
            verify(authClient).loginByAuthHeader(any());
            verify(request).setAttribute(ACCESS_PRINCIPAL_ATTRIBUTE, accessPrincipal);
        }
    }

    @Test
    public void whenDashCachePresentThenItRemoved() {
        assertNoDashCacheInPermissionScopeTarget("repo");
        assertNoDashCacheInPermissionScopeTarget("repo-cache");
    }

    private static void assertNoDashCacheInPermissionScopeTarget(String repoKey) {
        RepoPath repoPath = RepoPathFactory.create(repoKey, "path");
        LegacyAuthorizationServiceImpl legacyAuthorizationService = new LegacyAuthorizationServiceImpl();
        PermissionScope scope = legacyAuthorizationService.createPermissionScope(
                PermissionScopeResourceType.ARTIFACT, repoPath, org.jfrog.access.common.v2.Action.READ);
        assertEquals(scope.getTarget(), removeDashCache(repoKey));
    }

    @Test
    void testIsInternalAcl() {
        Acl<? extends PermissionTarget> acl = mock(Acl.class);
        when(acl.getAccessIdentifier()).thenReturn("INTERNAL_permission");
        boolean isInternal = authorizationService.isInternalAcl(acl);
        assertTrue(isInternal);

        when(acl.getAccessIdentifier()).thenReturn("permission");
        isInternal = authorizationService.isInternalAcl(acl);
        assertFalse(isInternal);

        when(acl.getAccessIdentifier()).thenReturn(null);
        isInternal = authorizationService.isInternalAcl(acl);
        assertFalse(isInternal);

        when(acl.getAccessIdentifier()).thenReturn("");
        isInternal = authorizationService.isInternalAcl(acl);
        assertFalse(isInternal);
    }

    @ParameterizedTest
    @MethodSource("isProjectScopeTokenData")
    void isProjectScopeToken_shouldReturnExpected_whenGivenScopes(List<String> scopes, boolean expected) {
        // Arrange
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        doReturn(scopes).when(jwtAccessToken).getScope();

        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        doReturn(jwtAccessToken).when(authentication).getAccessToken();

        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();

        // Act & Assert
        assertEquals(expected, authorizationService.isProjectScopeToken());
    }

    private static Stream<Arguments> isProjectScopeTokenData() {
        return Stream.of(
                // Single project scope → project-scoped
                Arguments.of(List.of("applied-permissions/roles:proj1:\"Developer\""), true),
                // Multiple project scopes → still project-scoped
                Arguments.of(List.of("applied-permissions/roles:proj1:\"Developer\"",
                        "applied-permissions/roles:proj2:\"Admin\""), true),
                // Mixed: user + project → not project-scoped
                Arguments.of(List.of("applied-permissions/user", "applied-permissions/roles:proj1:\"Developer\""),
                        false),
                // Mixed: groups + project → not project-scoped
                Arguments.of(List.of("applied-permissions/groups:readers",
                        "applied-permissions/roles:proj1:\"Developer\""), false),
                // User scope only → not project-scoped
                Arguments.of(List.of("applied-permissions/user"), false),
                // Groups scope only → not project-scoped
                Arguments.of(List.of("applied-permissions/groups:readers"), false),
                // Empty scope list → not project-scoped (vacuous truth guard)
                Arguments.of(Collections.emptyList(), false)
        );
    }

    @Test
    void isProjectScopeToken_shouldReturnFalse_whenNotAccessTokenAuthentication() {
        // Arrange
        AnonymousAuthenticationToken authentication = mock(AnonymousAuthenticationToken.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();

        // Act & Assert
        assertFalse(authorizationService.isProjectScopeToken());
    }

    @ParameterizedTest
    @MethodSource("isProjectScopeTokenFlagOffData")
    void isProjectScopeToken_shouldUseAnyMatch_whenFlagOff(List<String> scopes, boolean expected) {
        // Arrange
        getBound().setProperty(ConstantValues.authProjectScopeTokenAllMatch, "false");

        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        doReturn(scopes).when(jwtAccessToken).getScope();

        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        doReturn(jwtAccessToken).when(authentication).getAccessToken();

        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();

        // Act & Assert
        assertEquals(expected, authorizationService.isProjectScopeToken());
    }

    private static Stream<Arguments> isProjectScopeTokenFlagOffData() {
        return Stream.of(
                // Single project scope → true (same as allMatch)
                Arguments.of(List.of("applied-permissions/roles:proj1:\"Developer\""), true),
                // Mixed: user + project → true (anyMatch finds the roles scope)
                Arguments.of(List.of("applied-permissions/user",
                        "applied-permissions/roles:proj1:\"Developer\""), true),
                // Mixed: groups + project → true (anyMatch finds the roles scope)
                Arguments.of(List.of("applied-permissions/groups:readers",
                        "applied-permissions/roles:proj1:\"Developer\""), true),
                // User scope only → false
                Arguments.of(List.of("applied-permissions/user"), false),
                // Empty scope list → false
                Arguments.of(Collections.emptyList(), false)
        );
    }
}
