package org.artifactory.security;

import org.artifactory.addon.AddonsManager;
import org.artifactory.api.security.SecurityService;
import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.model.security.BuildPermissionTargetImpl;
import org.artifactory.model.security.GroupImpl;
import org.artifactory.model.security.MutableBuildAclImpl;
import org.artifactory.model.security.MutableRepoAclImpl;
import org.artifactory.model.security.RepoPermissionTargetImpl;
import org.artifactory.model.security.UserImpl;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.security.access.AccessService;
import org.artifactory.security.access.AccessTokenAuthentication;
import org.artifactory.security.interceptor.SecurityConfigurationChangesInterceptors;
import org.artifactory.storage.StorageException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.util.AlreadyExistsException;
import org.jfrog.access.client.AccessClient;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;
import org.jfrog.access.client.auth.AccessPrincipal;
import org.jfrog.access.client.auth.AccessPrincipalImpl;
import org.jfrog.access.client.auth.AuthClient;
import org.jfrog.access.client.auth.LoginResult;
import org.jfrog.access.client.permission.GrpcPermissionsClient;
import org.jfrog.access.client.permission.PermissionsClient;
import org.jfrog.access.client.user.UsersClient;
import org.jfrog.access.common.Action;
import org.jfrog.access.common.PermissionScope;
import org.jfrog.access.common.ResourceType;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.rest.permission.Permission;
import org.jfrog.access.rest.permission.PermissionActions;
import org.jfrog.access.rest.permission.Permissions;
import org.jfrog.access.rest.user.User;
import org.jfrog.access.token.JwtAccessToken;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_CONFLICT;
import static java.util.Collections.emptyList;
import static org.artifactory.api.security.PermissionHeuristicScore.admin;
import static org.artifactory.api.security.PermissionHeuristicScore.readAll;
import static org.artifactory.api.security.PermissionHeuristicScore.readNotAllowed;
import static org.artifactory.api.security.PermissionHeuristicScore.readWithExclusion;
import static org.artifactory.entitlement.EntitlementType.RELEASE_BUNDLES_READ;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;
import static org.artifactory.security.ArtifactoryPermission.ANNOTATE;
import static org.artifactory.security.ArtifactoryPermission.DELETE;
import static org.artifactory.security.ArtifactoryPermission.DEPLOY;
import static org.artifactory.security.ArtifactoryPermission.DISTRIBUTE;
import static org.artifactory.security.ArtifactoryPermission.MANAGE;
import static org.artifactory.security.ArtifactoryPermission.MANAGED_XRAY_META;
import static org.artifactory.security.ArtifactoryPermission.READ;
import static org.artifactory.util.bundles.BundleConfigurationUtil.DEFAULT_RBV2_REPO;
import static org.jfrog.access.common.ResourceType.BUILD;
import static org.jfrog.access.common.ResourceType.RELEASE_BUNDLE;
import static org.jfrog.access.common.ResourceType.REPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Andrey Tashlitsky
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private AddonsManager addons;
    @Mock
    private AccessService accessService;
    @Mock
    private SecurityService securityService;
    @Mock
    private InternalRepositoryService repositoryService;
    @Mock
    private BuildFacade buildFacade;
    @Mock
    private SecurityConfigurationChangesInterceptors interceptors;
    @Mock
    private AccessClient accessClient;
    @Mock
    private AuthClient authClient;
    @Mock
    private PermissionsClient permissionsClient;
    @Mock
    private GrpcPermissionsClient grpcPermissionsClient;
    @Mock
    private UsersClient usersClient;
    // The service converts Access-model Permission/PermissionActions into Artifactory *Acl objects through the
    // real (non-mocked) AclMapper. These deeply-stubbed mocks are therefore coupled to AclMapper's internals:
    // if the mapper starts reading additional Permission fields, the affected tests must stub them or they will
    // NPE/return unexpected values. This brittleness is intentional — we exercise the real mapping rather than
    // mocking it, so mapper regressions surface here.
    @Mock
    private Permission permission;
    @Mock
    private PermissionActions permissionActions;
    private AuthorizationServiceImpl authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationServiceImpl();
        authorizationService.setAddons(addons);
        authorizationService.setAccessService(accessService);
        authorizationService.setSecurityService(securityService);
        authorizationService.setRepositoryService(repositoryService);
        authorizationService.setBuildFacade(buildFacade);
        authorizationService.setInterceptors(interceptors);
        authorizationService.postConstruct();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canDo_user_returnsPrincipalDecision(boolean principalCanDo) {
        // Read/annotate/delete/deploy all delegate to principalCanDo with a single typed (repoType=local)
        // scope; the client matches ANY / ANY LOCAL via the repoType attribute.
        UserInfo userInfo = new UserImpl("cool-user");
        setSimpleUserAuthentication(userInfo);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path/to/file");
        doReturn(false).when(repositoryService).existsByType(any(), eq("cool-repo"));
        doReturn(true).when(repositoryService).existsByType(LOCAL, "cool-repo");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.of(mock(User.class))).when(usersClient).findUserByUsername(anyString(), eq(true));
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(principalCanDo).when(authClient).principalCanDo(any(), any(), eq(true));
        assertEquals(principalCanDo, authorizationService.canRead(repoPath));
        ArgumentCaptor<PermissionScope> argCaptor = ArgumentCaptor.forClass(PermissionScope.class);
        verify(authClient).principalCanDo(any(), argCaptor.capture(), eq(true));
        var scope = argCaptor.getValue();
        assertEquals("cool-repo", scope.getTarget());
        assertEquals(List.of("local"), scope.getAttributes().map(a -> a.get("repoType")).orElse(null));
        assertEquals(principalCanDo, authorizationService.canRead(userInfo, repoPath));
        assertEquals(principalCanDo, authorizationService.canAnnotate(repoPath));
        assertEquals(principalCanDo, authorizationService.canAnnotate(userInfo, repoPath));
        assertEquals(principalCanDo, authorizationService.canDelete(repoPath));
        assertEquals(principalCanDo, authorizationService.canDelete(userInfo, repoPath));
        assertEquals(principalCanDo, authorizationService.canDeploy(repoPath));
        assertEquals(principalCanDo, authorizationService.canDeploy(userInfo, repoPath));
    }

    @ParameterizedTest
    @ValueSource(strings = {"artifactory-build-info", RELEASE_BUNDLE_REPO_KEY, DEFAULT_RBV2_REPO})
    void canDo_user_buildInfoOrReleaseBundleRepo_scopesToRepoKeyOnly(String repoKey) {
        UserInfo userInfo = new UserImpl("cool-user");
        setSimpleUserAuthentication(userInfo);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.of(mock(User.class))).when(usersClient).findUserByUsername(anyString(), anyBoolean());
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));
        if ("artifactory-build-info".equals(repoKey)) {
            doReturn(true).when(buildFacade).isBuildInfoRepo(repoKey);
        }
        if (RELEASE_BUNDLE_REPO_KEY.equals(repoKey)) {
            doReturn(true).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        }
        RepoPath repoPath = RepoPathFactory.create(repoKey, "cool-path/to/file");
        assertTrue(authorizationService.canRead(repoPath));
        ArgumentCaptor<PermissionScope> argCaptor = ArgumentCaptor.forClass(PermissionScope.class);
        verify(authClient).principalCanDo(any(), argCaptor.capture(), eq(true));
        var scope = argCaptor.getValue();
        assertEquals(repoKey, scope.getTarget());
        assertTrue(scope.getAttributes().isEmpty(), "build/release-bundle scopes carry no repoType attribute");
    }

    @Test
    void canDo_userNotResolved_returnsFalse() {
        UserInfo userInfo = new UserImpl("cool-user");
        setSimpleUserAuthentication(userInfo);
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path/to/file");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.empty()).when(usersClient).findUserByUsername(eq("cool-user"), anyBoolean());
        assertFalse(authorizationService.canRead(repoPath));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canDo_group_returnsPrincipalDecision(boolean principalCanDo) {
        // Remote repo: a single typed (repoType=remote) scope; the client matches ANY / ANY REMOTE and
        // aliases the bare<->"-cache" key itself, so no explicit "-cache" scope is needed.
        GroupInfo groupInfo = new GroupImpl("cool-group");
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path/to/file");
        doReturn(false).when(repositoryService).existsByType(any(), eq("cool-repo"));
        doReturn(true).when(repositoryService).existsByType(REMOTE, "cool-repo");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(principalCanDo).when(authClient).principalCanDo(any(), any(), eq(true));
        assertEquals(principalCanDo, authorizationService.canRead(groupInfo, repoPath));
        ArgumentCaptor<PermissionScope> argCaptor = ArgumentCaptor.forClass(PermissionScope.class);
        verify(authClient).principalCanDo(any(), argCaptor.capture(), eq(true));
        var scope = argCaptor.getValue();
        assertEquals("cool-repo", scope.getTarget());
        assertEquals(List.of("remote"), scope.getAttributes().map(a -> a.get("repoType")).orElse(null));
        assertEquals(principalCanDo, authorizationService.canAnnotate(groupInfo, repoPath));
        assertEquals(principalCanDo, authorizationService.canDelete(groupInfo, repoPath));
        assertEquals(principalCanDo, authorizationService.canDeploy(groupInfo, repoPath));
    }

    @Test
    void canDo_group_repoNotFound_usesRawRepoKeyScope() {
        GroupInfo groupInfo = new GroupImpl("cool-group");
        RepoPath repoPath = RepoPathFactory.create("cool-repo", "cool-path/to/file");
        doReturn(false).when(repositoryService).existsByType(any(), eq("cool-repo"));
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));
        assertTrue(authorizationService.canDeploy(groupInfo, repoPath));
        ArgumentCaptor<PermissionScope> argCaptor = ArgumentCaptor.forClass(PermissionScope.class);
        verify(authClient).principalCanDo(any(), argCaptor.capture(), eq(true));
        var scope = argCaptor.getValue();
        // Unknown type: bare-key scope, no repoType attribute. The client still matches a plain "ANY" grant
        // but not the typed ANY LOCAL / ANY REMOTE.
        assertEquals("cool-repo", scope.getTarget());
        assertTrue(scope.getAttributes().isEmpty(), "unknown-type scope must carry no repoType attribute");
    }

    @Test
    void canDo_user_localRepoWithCacheSuffix_stripsCacheSuffix() {
        UserInfo userInfo = new UserImpl("cool-user");
        setSimpleUserAuthentication(userInfo);
        RepoPath repoPath = RepoPathFactory.create("local-repo-cache", "path/to/file");
        doReturn(false).when(repositoryService).existsByType(any(), eq("local-repo-cache"));
        doReturn(true).when(repositoryService).existsByType(LOCAL, "local-repo-cache");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.of(mock(User.class))).when(usersClient)
                .findUserByUsername(anyString(), eq(true));
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));
        assertTrue(authorizationService.canRead(repoPath));
        ArgumentCaptor<PermissionScope> argCaptor = ArgumentCaptor.forClass(PermissionScope.class);
        verify(authClient).principalCanDo(any(), argCaptor.capture(), eq(true));
        var scope = argCaptor.getValue();
        assertEquals("local-repo", scope.getTarget());
        assertEquals(List.of("local"), scope.getAttributes().map(a -> a.get("repoType")).orElse(null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void canDo_user_remoteRepoWithCacheSuffix_stripsCacheSuffix(boolean strippedKeyFound) {
        UserInfo userInfo = new UserImpl("cool-user");
        setSimpleUserAuthentication(userInfo);
        RepoPath repoPath = RepoPathFactory.create("remote-repo-cache", "path/to/file");
        doReturn(false).when(repositoryService).existsByType(any(), eq("remote-repo-cache"));
        if (strippedKeyFound) {
            doReturn(true).when(repositoryService).existsByType(REMOTE, "remote-repo");
        } else {
            doReturn(false).when(repositoryService).existsByType(REMOTE, "remote-repo");
            doReturn(true).when(repositoryService).existsByType(REMOTE, "remote-repo-cache");
        }
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.of(mock(User.class))).when(usersClient)
                .findUserByUsername(anyString(), eq(true));
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));
        assertTrue(authorizationService.canRead(repoPath));
        ArgumentCaptor<PermissionScope> argCaptor = ArgumentCaptor.forClass(PermissionScope.class);
        verify(authClient).principalCanDo(any(), argCaptor.capture(), eq(true));
        var scope = argCaptor.getValue();
        // "-cache" suffix stripped to the bare key; a single typed (repoType=remote) scope is emitted and
        // Access aliases bare<->"-cache" itself.
        assertEquals("remote-repo", scope.getTarget());
        assertEquals(List.of("remote"), scope.getAttributes().map(a -> a.get("repoType")).orElse(null));
    }

    private static Stream<Arguments> canManageTargetTypes() {
        return Stream.of(
                Arguments.of(REPO, RepoPermissionTarget.class),
                Arguments.of(BUILD, BuildPermissionTarget.class),
                Arguments.of(RELEASE_BUNDLE, ReleaseBundlePermissionTarget.class));
    }

    @ParameterizedTest
    @MethodSource("canManageTargetTypes")
    void canManage_matchesPermissionTargetByDisplayName(ResourceType resourceType,
            Class<? extends PermissionTarget> targetType) {
        // canManage matches the requested target's name against the MANAGE permissions Access returns for the
        // resource type; this holds uniformly across repo, build and release-bundle targets.
        stubResolvedUser("coolest-user");
        Permission permission1 = mock(Permission.class);
        doReturn("cool-pt1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission1, permission2)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(resourceType), eq(Action.MANAGE),
                        eq(false));
        PermissionTarget matchingTarget = mock(targetType);
        doReturn("cool-pt1").when(matchingTarget).getName();
        assertTrue(authorizationService.canManage(matchingTarget));
        PermissionTarget nonMatchingTarget = mock(targetType);
        doReturn("cool-pt2").when(nonMatchingTarget).getName();
        assertFalse(authorizationService.canManage(nonMatchingTarget));
    }

    @Test
    void canManage_unauthenticated_returnsFalse() {
        PermissionTarget permissionTarget = mock(PermissionTarget.class);
        assertFalse(authorizationService.canManage(permissionTarget));
    }

    @Test
    void canManage_admin_returnsTrue() {
        PermissionTarget permissionTarget = mock(PermissionTarget.class);
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isEffectiveAdmin();
        setSimpleUserAuthentication(userInfo);
        assertTrue(authorizationService.canManage(permissionTarget));
    }

    @Test
    void canManage_userNotResolved_returnsFalse() {
        PermissionTarget permissionTarget = mock(PermissionTarget.class);
        stubUnresolvedUser("coolest-user");
        assertFalse(authorizationService.canManage(permissionTarget));
    }

    @Test
    void hasRepoPermission_reflectsPermissionsPresence() {
        stubResolvedUser("coolest-user");
        doReturn(permissionActions).when(permission).getActions();
        String customData = """
                {
                    "repoKeys": ["cool-repo"]
                }""";
        doReturn(customData).when(permission).getCustomData();
        doReturn("repo-acl").when(permission).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(REPO), eq(Action.READ), eq(false));
        assertTrue(authorizationService.hasRepoPermission(READ, true));
        doReturn(emptyList()).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(REPO), eq(Action.READ), eq(true));
        assertFalse(authorizationService.hasRepoPermission(READ));
    }

    @Test
    void hasRepoPermission_passesUserOnlyPrincipalToAccess() {
        // RTDEV-88055 / JA-21746: the permissions() family must receive a user-only principal. Access
        // resolves the user's group memberships server-side, so the principal must carry the resolved user
        // and must NOT blend the authentication-cache groups (here 'dev') into it.
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(false).when(userInfo).isEffectiveAdmin();
        doReturn("coolest-user").when(userInfo).getUsername();
        UserGroupInfo group = mock(UserGroupInfo.class);
        doReturn("dev").when(group).getGroupName();
        doReturn(Set.of(group)).when(userInfo).getGroups();
        setSimpleUserAuthentication(userInfo);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(usersClient).when(accessClient).cachedUsers();
        User user = mock(User.class);
        doReturn("coolest-user").when(user).getUsername();
        doReturn(Optional.of(user)).when(usersClient).findUserByUsername(anyString(), anyBoolean());
        doReturn(permissionActions).when(permission).getActions();
        String customData = """
                {
                    "repoKeys": ["cool-repo"]
                }""";
        doReturn(customData).when(permission).getCustomData();
        doReturn("repo-acl").when(permission).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(REPO), eq(Action.READ), eq(false));
        assertTrue(authorizationService.hasRepoPermission(READ, true));
        ArgumentCaptor<AccessPrincipal> principalCaptor = ArgumentCaptor.forClass(AccessPrincipal.class);
        verify(permissionsClient).findPrincipalPermissionsByResourceTypeAndAction(principalCaptor.capture(),
                eq(REPO), eq(Action.READ), eq(false));
        AccessPrincipalImpl principal = (AccessPrincipalImpl) principalCaptor.getValue();
        assertEquals("coolest-user", principal.getName());
        assertTrue(principal.getUser().isPresent());
        assertTrue(principal.getGroupNames().isEmpty(),
                "authentication group 'dev' must not be blended into the principal; Access resolves groups itself");
    }

    @Test
    void hasRepoPermission_admin_returnsTrue() {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isEffectiveAdmin();
        setSimpleUserAuthentication(userInfo);
        assertTrue(authorizationService.hasRepoPermission(READ, true));
    }

    @Test
    void hasBuildPermission_reflectsPermissionsPresence() {
        stubResolvedUser("coolest-user");
        doReturn(permissionActions).when(permission).getActions();
        String customData = """
                {
                    "repoKeys": ["cool-repo"]
                }""";
        doReturn(customData).when(permission).getCustomData();
        doReturn("build-acl").when(permission).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(BUILD), eq(Action.READ), eq(false));
        assertTrue(authorizationService.hasBuildPermission("cool-repo", READ, true));
        doReturn(emptyList()).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(BUILD), eq(Action.READ), eq(true));
        assertFalse(authorizationService.hasBuildPermission("cool-repo", READ, false));
        assertFalse(authorizationService.hasBuildPermission("cool-repo", READ));
    }

    @Test
    void hasBuildPermission_admin_returnsTrue() {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isEffectiveAdmin();
        setSimpleUserAuthentication(userInfo);
        assertTrue(authorizationService.hasBuildPermission("cool-repo", READ, true));
    }

    @Test
    void hasBuildPermission_buildRepoNotInPermissionTarget_returnsFalse() {
        // Build permission targets are filtered by buildRepo: a permission scoped to 'cool-repo' must not
        // grant access when the request is for a different buildRepo, even though a permission is present.
        stubResolvedUser("coolest-user");
        doReturn(permissionActions).when(permission).getActions();
        String customData = """
                {
                    "repoKeys": ["cool-repo"]
                }""";
        doReturn(customData).when(permission).getCustomData();
        doReturn("build-acl").when(permission).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(BUILD), eq(Action.READ), eq(false));
        assertFalse(authorizationService.hasBuildPermission("other-repo", READ, true));
    }

    @Test
    void hasReleaseBundlePermission_reflectsPermissionsPresence() {
        doReturn(true).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        stubResolvedUser("coolest-user");
        doReturn(permissionActions).when(permission).getActions();
        String customData = """
                {
                    "repoKeys": ["cool-repo"]
                }""";
        doReturn(customData).when(permission).getCustomData();
        doReturn("rb-acl").when(permission).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(RELEASE_BUNDLE), eq(Action.READ), eq(false));
        assertTrue(authorizationService.hasReleaseBundlePermission(READ, true, true));
        assertTrue(authorizationService.hasReleaseBundlePermission(READ, true, false));
        doReturn(emptyList()).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(RELEASE_BUNDLE), eq(Action.READ), eq(true));
        assertFalse(authorizationService.hasReleaseBundlePermission(READ));
        assertFalse(authorizationService.hasReleaseBundlePermission(READ, false, false));
    }

    @Test
    void hasReleaseBundlePermission_admin_returnsTrue() {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isEffectiveAdmin();
        setSimpleUserAuthentication(userInfo);
        assertTrue(authorizationService.hasReleaseBundlePermission(READ, true, true));
    }

    @Test
    void hasReleaseBundlePermission_notAdminAndNotEntitled_returnsFalse() {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(false).when(userInfo).isEffectiveAdmin();
        setSimpleUserAuthentication(userInfo);
        doReturn(false).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        assertFalse(authorizationService.hasReleaseBundlePermission(READ, true, true));
    }

    @Test
    void hasRepoPermission_unauthenticated_returnsFalse() {
        assertFalse(authorizationService.hasRepoPermission(READ, true));
        assertFalse(authorizationService.hasRepoPermission(READ));
        verify(accessService, never()).getAccessClient();
    }

    @Test
    void hasRepoPermission_nullPrincipal_returnsFalse() {
        setupNullPrincipalUser();
        assertFalse(authorizationService.hasRepoPermission(READ, true));
        verify(permissionsClient, never()).findPrincipalPermissionsByResourceTypeAndAction(any(), any(), any(),
                anyBoolean());
    }

    @Test
    void hasBuildPermission_unauthenticated_returnsFalse() {
        assertFalse(authorizationService.hasBuildPermission("cool-repo", READ, true));
        assertFalse(authorizationService.hasBuildPermission("cool-repo", READ));
        verify(accessService, never()).getAccessClient();
    }

    @Test
    void hasBuildPermission_nullPrincipal_returnsFalse() {
        setupNullPrincipalUser();
        assertFalse(authorizationService.hasBuildPermission("cool-repo", READ, true));
        verify(permissionsClient, never()).findPrincipalPermissionsByResourceTypeAndAction(any(), any(), any(),
                anyBoolean());
    }

    @Test
    void hasReleaseBundlePermission_unauthenticated_returnsFalse() {
        assertFalse(authorizationService.hasReleaseBundlePermission(READ, true, true));
        assertFalse(authorizationService.hasReleaseBundlePermission(READ, true, false));
        assertFalse(authorizationService.hasReleaseBundlePermission(READ));
        verify(accessService, never()).getAccessClient();
    }

    @Test
    void hasReleaseBundlePermission_nullPrincipal_returnsFalse() {
        setupNullPrincipalUser();
        assertFalse(authorizationService.hasReleaseBundlePermission(READ, true, false));
        verify(permissionsClient, never()).findPrincipalPermissionsByResourceTypeAndAction(any(), any(), any(),
                anyBoolean());
    }

    /**
     * Authenticated non-admin user that's unknown to Access and has no groups,
     * so {@code createAccessPrincipal()} returns {@code null}.
     */
    private void setupNullPrincipalUser() {
        UserInfo userInfo = new UserImpl("unknown-user");
        setSimpleUserAuthentication(userInfo);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.empty()).when(usersClient).findUserByUsername(eq("unknown-user"), anyBoolean());
    }

    /**
     * Wires an authenticated, non-admin user that Access resolves successfully, including the
     * {@code permissions()} client used by the permission-target lookups. Returns the resolved
     * {@link User} mock so callers can stub extra behaviour on it when needed.
     * <p>
     * Note: this always stubs {@code accessClient.permissions()}. Under MockitoExtension's strict stubs,
     * every caller must therefore reach a permission lookup, otherwise the test fails with
     * {@code UnnecessaryStubbingException}. Use {@link #stubUnresolvedUser(String)} for paths that stop earlier.
     */
    private User stubResolvedUser(String username) {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(username).when(userInfo).getUsername();
        setSimpleUserAuthentication(userInfo);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(permissionsClient).when(accessClient).permissions();
        User user = mock(User.class);
        doReturn(Optional.of(user)).when(usersClient).findUserByUsername(eq(username), anyBoolean());
        return user;
    }

    /**
     * Wires an authenticated, non-admin user that Access fails to resolve (no token-scope groups), so
     * {@code createAccessPrincipal()} returns {@code null} and the permission lookups are never reached.
     */
    private void stubUnresolvedUser(String username) {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(username).when(userInfo).getUsername();
        setSimpleUserAuthentication(userInfo);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.empty()).when(usersClient).findUserByUsername(eq(username), anyBoolean());
    }

    /** Installs a simple (username/password) authentication for the given user in the security context. */
    private void setSimpleUserAuthentication(UserInfo userInfo) {
        SimpleUser simpleUser = new SimpleUser(userInfo);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(simpleUser, "password", SimpleUser.USER_GAS);
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
    }

    @Test
    void hasBuildBasicReadPermission_reflectsPermissionsPresence() {
        doReturn(false).when(buildFacade).hasBasicReadPermissionForAllBuilds();
        stubResolvedUser("coolest-user");
        doReturn(permissionActions).when(permission).getActions();
        String customData = """
                {
                    "repoKeys": ["cool-repo"]
                }""";
        doReturn(customData).when(permission).getCustomData();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(BUILD), eq(Action.READ), eq(true));
        assertTrue(authorizationService.hasBuildBasicReadPermission());
        doReturn(emptyList()).when(permissions).getPermissions();
        assertFalse(authorizationService.hasBuildBasicReadPermission());
    }

    @Test
    void hasBuildBasicReadPermission_unauthenticated_followsAllBuildsFlag() {
        doReturn(true).when(buildFacade).hasBasicReadPermissionForAllBuilds();
        assertTrue(authorizationService.hasBuildBasicReadPermission());
        doReturn(false).when(buildFacade).hasBasicReadPermissionForAllBuilds();
        assertFalse(authorizationService.hasBuildBasicReadPermission());
    }

    @Test
    void hasBuildBasicReadPermission_userNotResolved_returnsFalse() {
        doReturn(false).when(buildFacade).hasBasicReadPermissionForAllBuilds();
        stubUnresolvedUser("coolest-user");
        assertFalse(authorizationService.hasBuildBasicReadPermission());
    }

    @Test
    void getRepoPermissionTargets_returnsSameTargetsForAdminAndNonAdmin() {
        // The target list is driven purely by the permissions Access returns; the caller's admin flag does
        // not widen or narrow it. Verified for both an empty (unauthenticated) and a resolved principal.
        assertTrue(authorizationService.getRepoPermissionTargets(MANAGE).isEmpty());
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isEffectiveAdmin();
        doReturn("coolest-user").when(userInfo).getUsername();
        setSimpleUserAuthentication(userInfo);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.of(mock(User.class))).when(usersClient).findUserByUsername(anyString(), anyBoolean());
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission1, permission2)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(REPO), any(), eq(true));
        List<String> permissionTargets = authorizationService.getRepoPermissionTargets(MANAGE)
                .stream()
                .map(PermissionTarget::getName)
                .toList();
        assertEquals(List.of("repo-acl1", "repo-acl2"), permissionTargets);
        // Flipping only the admin flag must not change the resolved targets.
        doReturn(false).when(userInfo).isEffectiveAdmin();
        permissionTargets = authorizationService.getRepoPermissionTargets(MANAGE)
                .stream()
                .map(PermissionTarget::getName)
                .toList();
        assertEquals(List.of("repo-acl1", "repo-acl2"), permissionTargets);
    }

    @Test
    void getRepoPermissionTargets_userNotResolved_returnsEmpty() {
        stubUnresolvedUser("coolest-user");
        List<RepoPermissionTarget> permissionTargets = authorizationService.getRepoPermissionTargets(MANAGE);
        assertEquals(emptyList(), permissionTargets);
    }

    @Test
    void getStrongestReadPermissionTarget_returnsStrongestTarget() {
        // Walks the read-strength heuristic as the matching permissions widen: none -> readWithExclusion
        // (an exclude pattern still applies) -> readAll (unrestricted include on the requested repo).
        stubResolvedUser("cool-user");
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        Permission permission3 = mock(Permission.class);
        PermissionActions permissionActions3 = mock(PermissionActions.class);
        doReturn(permissionActions3).when(permission3).getActions();
        doReturn("repo-acl3").when(permission3).getDisplayName();
        Permission permission4 = mock(Permission.class);
        PermissionActions permissionActions4 = mock(PermissionActions.class);
        doReturn(permissionActions4).when(permission4).getActions();
        doReturn("repo-acl4").when(permission4).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission1, permission2, permission3, permission4)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(REPO), any(), eq(true));
        assertEquals(readNotAllowed, authorizationService.getStrongestReadPermissionTarget("cool-repo"));
        String customData1 = """
                {
                    "repoKeys": ["cool-repo"],
                    "excludePattern": "**/cool"
                }""";
        doReturn(customData1).when(permission1).getCustomData();
        String customData2 = """
                {
                    "repoKeys": ["ANY"],
                    "includePattern": "**/cool"
                }""";
        doReturn(customData2).when(permission2).getCustomData();
        String customData3 = """
                {
                    "repoKeys": ["ANY LOCAL"],
                    "excludePattern": ""
                }""";
        doReturn(customData3).when(permission3).getCustomData();
        String customData4 = """
                {
                    "repoKeys": ["generic-local"],
                    "excludePattern": ""
                }""";
        doReturn(customData4).when(permission4).getCustomData();
        assertEquals(readWithExclusion, authorizationService.getStrongestReadPermissionTarget("cool-repo"));
        customData1 = """
                {
                    "repoKeys": ["cool-repo"],
                    "includePattern": "**",
                    "excludePattern": ""
                }""";
        doReturn(customData1).when(permission1).getCustomData();
        customData2 = """
                {
                    "repoKeys": ["ANY"],
                    "includePattern": "**",
                    "excludePattern": ""
                }""";
        doReturn(customData2).when(permission2).getCustomData();
        customData3 = """
                {
                    "repoKeys": ["ANY LOCAL"],
                    "includePattern": "**",
                    "excludePattern": ""
                }""";
        doReturn(customData3).when(permission3).getCustomData();
        assertEquals(readAll, authorizationService.getStrongestReadPermissionTarget("cool-repo"));
    }

    @Test
    void getStrongestReadPermissionTarget_admin_returnsAdminTarget() {
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isEffectiveAdmin();
        setSimpleUserAuthentication(userInfo);
        assertEquals(admin, authorizationService.getStrongestReadPermissionTarget("cool-repo"));
    }

    @Test
    void getAllRepoAcls_returnsAcls() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient).findPermissionsByResourceType(REPO);
        List<RepoAcl> allRepoAcls = authorizationService.getAllRepoAcls();
        assertEquals(2, allRepoAcls.size());
    }

    @Test
    void getAllRepoAcls_admin_returnsAcls() {
        doReturn(true).when(securityService).isAdmin();
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient).findPermissionsByResourceType(REPO);
        List<RepoAcl> allRepoAcls = authorizationService.getAllRepoAcls(READ);
        assertEquals(2, allRepoAcls.size());
    }

    @Test
    void getAllRepoAcls_filtered_returnsMatchingAcls() {
        stubResolvedUser("coolest-user");
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission1, permission2)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(REPO), any(), eq(true));
        List<RepoAcl> allRepoAcls = authorizationService.getAllRepoAcls(READ);
        assertEquals(2, allRepoAcls.size());
        allRepoAcls = authorizationService.getAllRepoAcls(DEPLOY);
        assertEquals(2, allRepoAcls.size());
        allRepoAcls = authorizationService.getAllRepoAcls(ANNOTATE);
        assertEquals(2, allRepoAcls.size());
        allRepoAcls = authorizationService.getAllRepoAcls(DELETE);
        assertEquals(2, allRepoAcls.size());
        allRepoAcls = authorizationService.getAllRepoAcls(MANAGE);
        assertEquals(2, allRepoAcls.size());
    }

    @Test
    void getAllRepoAcls_filtered_userNotResolved_returnsEmpty() {
        stubUnresolvedUser("coolest-user");
        assertEquals(emptyList(), authorizationService.getAllRepoAcls(MANAGE));
    }

    @Test
    void getAllBuildAcls_returnsAcls() {
        // getAllBuildAcls(true) excludes internal (INTERNAL_-prefixed) permissions; getAllBuildAcls(false) keeps them.
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getId();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getId();
        Permission permission3 = mock(Permission.class);
        PermissionActions permissionActions3 = mock(PermissionActions.class);
        doReturn(permissionActions3).when(permission3).getActions();
        doReturn("INTERNAL_repo-acl3").when(permission3).getId();
        doReturn(List.of(permission1, permission2, permission3)).when(grpcPermissionsClient)
                .findPermissionsByResourceType(BUILD);
        List<BuildAcl> allRepoAcls = authorizationService.getAllBuildAcls(false);
        assertEquals(3, allRepoAcls.size());
        allRepoAcls = authorizationService.getAllBuildAcls(true);
        assertEquals(2, allRepoAcls.size());
    }

    @Test
    void getAllBuildAcls_admin_returnsAcls() {
        doReturn(true).when(securityService).isAdmin();
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("build-acl1").when(permission1).getId();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("INTERNAL_build-acl2").when(permission2).getId();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient)
                .findPermissionsByResourceType(BUILD);
        List<BuildAcl> allBuildAcls = authorizationService.getAllBuildAcls(MANAGE);
        assertEquals(2, allBuildAcls.size());
        verify(accessClient, never()).permissions();
    }

    @Test
    void getAllBuildAcls_filtered_returnsMatchingAcls() {
        stubResolvedUser("coolest-user");
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission1, permission2)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(BUILD), eq(Action.READ), eq(true));
        List<BuildAcl> allRepoAcls = authorizationService.getAllBuildAcls(READ);
        assertEquals(2, allRepoAcls.size());
    }

    @Test
    void getAllBuildAcls_filtered_userNotResolved_returnsEmpty() {
        stubUnresolvedUser("coolest-user");
        assertEquals(emptyList(), authorizationService.getAllBuildAcls(MANAGE));
    }

    @Test
    void getAllReleaseBundleAcls_returnsAcls() {
        doReturn(true).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient)
                .findPermissionsByResourceType(RELEASE_BUNDLE);
        assertEquals(2, authorizationService.getAllReleaseBundleAcls(true).size());
        assertEquals(2, authorizationService.getAllReleaseBundleAcls(false).size());
    }

    @Test
    void getAllReleaseBundleAcls_notEntitled_returnsEmpty() {
        doReturn(false).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        assertEquals(0, authorizationService.getAllReleaseBundleAcls(true).size());
    }

    @Test
    void getAllReleaseBundleAcls_getByLicenseFalse_notEntitled_stillReturnsAcls() {
        // When getByLicense is false the entitlement gate is skipped entirely (addons.isEntitled is never
        // consulted) and the ACLs are returned regardless of the release-bundles entitlement.
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("rb-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("rb-acl2").when(permission2).getDisplayName();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient)
                .findPermissionsByResourceType(RELEASE_BUNDLE);
        assertEquals(2, authorizationService.getAllReleaseBundleAcls(false).size());
        verify(addons, never()).isEntitled(RELEASE_BUNDLES_READ);
    }

    @Test
    void getAllReleaseBundleAcls_admin_returnsAcls() {
        doReturn(true).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        doReturn(true).when(securityService).isAdmin();
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(grpcPermissionsClient).when(accessClient).grpcPermissions();
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("rb-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("rb-acl2").when(permission2).getDisplayName();
        doReturn(List.of(permission1, permission2)).when(grpcPermissionsClient)
                .findPermissionsByResourceType(RELEASE_BUNDLE);
        List<ReleaseBundleAcl> allRbAcls = authorizationService.getAllReleaseBundleAcls(MANAGE);
        assertEquals(2, allRbAcls.size());
        verify(accessClient, never()).permissions();
    }

    @Test
    void getAllReleaseBundleAcls_adminNotEntitled_returnsEmpty() {
        doReturn(false).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        List<ReleaseBundleAcl> allRbAcls = authorizationService.getAllReleaseBundleAcls(MANAGE);
        assertEquals(emptyList(), allRbAcls);
        verify(securityService, never()).isAdmin();
    }

    @Test
    void getAllReleaseBundleAcls_filtered_returnsMatchingAcls() {
        doReturn(true).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        stubResolvedUser("coolest-user");
        Permission permission1 = mock(Permission.class);
        PermissionActions permissionActions1 = mock(PermissionActions.class);
        doReturn(permissionActions1).when(permission1).getActions();
        doReturn("repo-acl1").when(permission1).getDisplayName();
        Permission permission2 = mock(Permission.class);
        PermissionActions permissionActions2 = mock(PermissionActions.class);
        doReturn(permissionActions2).when(permission2).getActions();
        doReturn("repo-acl2").when(permission2).getDisplayName();
        Permissions permissions = mock(Permissions.class);
        doReturn(List.of(permission1, permission2)).when(permissions).getPermissions();
        doReturn(permissions).when(permissionsClient)
                .findPrincipalPermissionsByResourceTypeAndAction(any(), eq(RELEASE_BUNDLE), eq(Action.READ), eq(true));
        List<ReleaseBundleAcl> allRepoAcls = authorizationService.getAllReleaseBundleAcls(READ);
        assertEquals(2, allRepoAcls.size());
    }

    @Test
    void getAllReleaseBundleAcls_filtered_userNotResolved_returnsEmpty() {
        assertEquals(emptyList(), authorizationService.getAllReleaseBundleAcls(MANAGE));
        doReturn(true).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        stubUnresolvedUser("coolest-user");
        assertEquals(emptyList(), authorizationService.getAllReleaseBundleAcls(MANAGE));
    }

    @Test
    void createAcl_createsPermission() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(true).when(securityService).isAdmin();
        ServiceId serviceId = mock(ServiceId.class);
        doReturn("cool-service-id").when(serviceId).getFormattedName();
        doReturn(serviceId).when(accessService).getArtifactoryServiceId();
        var permissionTarget = new RepoPermissionTargetImpl("cool-permission-target");
        var acl = new MutableRepoAclImpl(permissionTarget, null, null, 0, null);
        authorizationService.createAcl(acl);
        verify(securityService).isAdmin();
        verify(repositoryService).existsByType(any(), any());
        verify(permissionsClient).createPermission(any());
        verify(interceptors).onPermissionsAdd(any());
    }

    @Test
    void createAcl_nonAdmin_throwsSecurityException() {
        // Only admins may create permissions; a non-admin caller is rejected before any Access interaction.
        doReturn(false).when(securityService).isAdmin();
        var acl = mock(MutableRepoAclImpl.class);
        assertThrows(SecurityException.class, () -> authorizationService.createAcl(acl));
        verify(repositoryService, never()).existsByType(any(), any());
        verify(permissionsClient, never()).createPermission(any());
        verify(interceptors, never()).onPermissionsAdd(any());
    }

    @Test
    void createAcl_invalidPermissionTarget_throwsIllegalArgument() {
        // An unrecognized permission-target type cannot be mapped to an Access permission and is rejected.
        doReturn(true).when(securityService).isAdmin();
        var acl = mock(MutableRepoAclImpl.class);
        doReturn(mock(RepoPermissionTargetImpl.class)).when(acl).getPermissionTarget();
        assertThrows(IllegalArgumentException.class, () -> authorizationService.createAcl(acl));
        verify(repositoryService, never()).existsByType(any(), any());
        verify(permissionsClient, never()).createPermission(any());
        verify(interceptors, never()).onPermissionsAdd(any());
    }

    @Test
    void createAcl_httpConflict_throwsAclAlreadyExists() {
        doReturn(accessClient).when(accessService).getAccessClient();
        var accessClientHttpException = new AccessClientHttpException(SC_CONFLICT, "Conflict");
        doThrow(accessClientHttpException).when(accessClient).permissions();
        doReturn(true).when(securityService).isAdmin();
        var permissionTarget = new RepoPermissionTargetImpl("cool-permission-target");
        var acl = new MutableRepoAclImpl(permissionTarget, null, null, 0, null);
        var e = assertThrows(AlreadyExistsException.class, () -> authorizationService.createAcl(acl));
        assertEquals("Could not create permission cool-permission-target with identifier null", e.getMessage());
        verify(securityService).isAdmin();
        verify(repositoryService).existsByType(any(), any());
        verify(permissionsClient, never()).createPermission(any());
        verify(interceptors, never()).onPermissionsAdd(any());
    }

    @Test
    void createAcl_httpOtherStatus_propagates() {
        doReturn(accessClient).when(accessService).getAccessClient();
        var accessClientHttpException = new AccessClientHttpException(SC_BAD_REQUEST, "Bad Request");
        doThrow(accessClientHttpException).when(accessClient).permissions();
        doReturn(true).when(securityService).isAdmin();
        var permissionTarget = new RepoPermissionTargetImpl("cool-permission-target");
        var acl = new MutableRepoAclImpl(permissionTarget, null, null, 0, null);
        var e = assertThrows(StorageException.class, () -> authorizationService.createAcl(acl));
        assertEquals("Could not create permission cool-permission-target with identifier null", e.getMessage());
        verify(securityService).isAdmin();
        verify(repositoryService).existsByType(any(), any());
        verify(permissionsClient, never()).createPermission(any());
        verify(interceptors, never()).onPermissionsAdd(any());
    }

    @Test
    void createAcl_accessClientException_propagates() {
        doReturn(accessClient).when(accessService).getAccessClient();
        var accessClientException = new AccessClientException("Access Client Exception");
        doThrow(accessClientException).when(accessClient).permissions();
        doReturn(true).when(securityService).isAdmin();
        var permissionTarget = new RepoPermissionTargetImpl("cool-permission-target");
        var acl = new MutableRepoAclImpl(permissionTarget, null, null, 0, null);
        var e = assertThrows(StorageException.class, () -> authorizationService.createAcl(acl));
        assertEquals("Could not create permission cool-permission-target with identifier null", e.getMessage());
        verify(securityService).isAdmin();
        verify(repositoryService).existsByType(any(), any());
        verify(permissionsClient, never()).createPermission(any());
        verify(interceptors, never()).onPermissionsAdd(any());
    }

    @Test
    void deleteAcl_deletesPermission() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        var permissionTarget = new RepoPermissionTargetImpl("cool-permission-target");
        var acl = new MutableRepoAclImpl(permissionTarget, null, null, 0, "cool-access-identifier");
        authorizationService.deleteAcl(acl);
        verify(permissionsClient).deletePermissionById(any());
        verify(interceptors).onPermissionsDelete(any());
    }

    @Test
    void deleteAcl_exception_propagates() {
        var permissionTarget = new RepoPermissionTargetImpl("cool-permission-target");
        var acl = new MutableRepoAclImpl(permissionTarget, null, null, 0, null);
        assertThrows(IllegalStateException.class, () -> authorizationService.deleteAcl(acl));
        verify(permissionsClient, never()).deletePermissionById(any());
        verify(interceptors, never()).onPermissionsDelete(any());
    }

    @Test
    void updateAcl_updatesPermission() {
        // Both overloads (admin-inferred and explicit non-admin) replace the permission and fire the interceptor.
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(true).when(securityService).isAdmin();
        ServiceId serviceId = mock(ServiceId.class);
        doReturn("cool-service-id").when(serviceId).getFormattedName();
        doReturn(serviceId).when(accessService).getArtifactoryServiceId();
        var permissionTarget = new BuildPermissionTargetImpl("cool-permission-target");
        var acl = new MutableBuildAclImpl(permissionTarget, null, null, 0, "cool-access-identifier");
        authorizationService.updateAcl(acl);
        authorizationService.updateAcl(acl, false);
        verify(permissionsClient, times(2)).replacePermission(any());
        verify(interceptors, times(2)).onPermissionsUpdate(any());
    }

    @Test
    void updateAcl_internalAcl_isNoOp() {
        // Internal permissions (identifier prefixed with INTERNAL_) are managed by the system and must be
        // silently skipped: no replace call, no interceptor, and Access is never contacted.
        doReturn(true).when(securityService).isAdmin();
        var permissionTarget = new BuildPermissionTargetImpl("cool-permission-target");
        var acl = new MutableBuildAclImpl(permissionTarget, null, null, 0, "INTERNAL_cool-access-identifier");
        authorizationService.updateAcl(acl);
        verify(accessService, never()).getAccessClient();
        verify(interceptors, never()).onPermissionsUpdate(any());
    }

    @Test
    void getRepoAcl_returnsAcl() {
        // Access finds the permission by display name; it is mapped to a RepoAcl carrying that target name.
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(permissionActions).when(permission).getActions();
        doReturn("repo-acl").when(permission).getDisplayName();
        doReturn(Optional.of(permission)).when(permissionsClient)
                .findByResourceTypeAndDisplayName(REPO, "repo-acl");
        RepoAcl repoAcl = authorizationService.getRepoAcl("repo-acl");
        assertEquals("repo-acl", repoAcl.getPermissionTarget().getName());
    }

    @Test
    void getRepoAcl_notFound_returnsNull() {
        // Missing permission target maps to a null ACL (the .orElse(null) contract callers rely on).
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(Optional.empty()).when(permissionsClient).findByResourceTypeAndDisplayName(REPO, "missing");
        assertNull(authorizationService.getRepoAcl("missing"));
    }

    @Test
    void getBuildAcl_returnsAcl() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(permissionActions).when(permission).getActions();
        doReturn("build-acl").when(permission).getDisplayName();
        doReturn(Optional.of(permission)).when(permissionsClient)
                .findByResourceTypeAndDisplayName(BUILD, "build-acl");
        BuildAcl buildAcl = authorizationService.getBuildAcl("build-acl");
        assertEquals("build-acl", buildAcl.getPermissionTarget().getName());
    }

    @Test
    void getBuildAcl_notFound_returnsNull() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(Optional.empty()).when(permissionsClient).findByResourceTypeAndDisplayName(BUILD, "missing");
        assertNull(authorizationService.getBuildAcl("missing"));
    }

    @Test
    void getReleaseBundleAcl_returnsAcl() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(permissionActions).when(permission).getActions();
        doReturn("rb-acl").when(permission).getDisplayName();
        doReturn(Optional.of(permission)).when(permissionsClient)
                .findByResourceTypeAndDisplayName(RELEASE_BUNDLE, "rb-acl");
        ReleaseBundleAcl releaseBundleAcl = authorizationService.getReleaseBundleAcl("rb-acl");
        assertEquals("rb-acl", releaseBundleAcl.getPermissionTarget().getName());
    }

    @Test
    void getReleaseBundleAcl_notFound_returnsNull() {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(permissionsClient).when(accessClient).permissions();
        doReturn(Optional.empty()).when(permissionsClient).findByResourceTypeAndDisplayName(RELEASE_BUNDLE, "missing");
        assertNull(authorizationService.getReleaseBundleAcl("missing"));
    }

    @Test
    void toAction_mapsArtifactoryPermissionToAction() {
        assertEquals(Action.READ, authorizationService.toAction(READ));
        assertEquals(Action.DEPLOY, authorizationService.toAction(DEPLOY));
        assertEquals(Action.ANNOTATE, authorizationService.toAction(ANNOTATE));
        assertEquals(Action.DELETE, authorizationService.toAction(DELETE));
        assertEquals(Action.MANAGE, authorizationService.toAction(MANAGE));
        assertEquals(Action.MANAGE_XRAY_MD, authorizationService.toAction(MANAGED_XRAY_META));
        assertEquals(Action.DISTRIBUTE, authorizationService.toAction(DISTRIBUTE));
    }

    @Test
    void toPermissionScopeAction_mapsArtifactoryPermissionToScopeAction() {
        assertEquals(org.jfrog.access.common.v2.Action.READ, authorizationService.toPermissionScopeAction(READ));
        assertEquals(org.jfrog.access.common.v2.Action.WRITE, authorizationService.toPermissionScopeAction(DEPLOY));
        assertEquals(org.jfrog.access.common.v2.Action.ANNOTATE,
                authorizationService.toPermissionScopeAction(ANNOTATE));
        assertEquals(org.jfrog.access.common.v2.Action.DELETE, authorizationService.toPermissionScopeAction(DELETE));
        assertEquals(org.jfrog.access.common.v2.Action.MANAGE, authorizationService.toPermissionScopeAction(MANAGE));
        assertEquals(org.jfrog.access.common.v2.Action.SCAN,
                authorizationService.toPermissionScopeAction(MANAGED_XRAY_META));
        assertEquals(org.jfrog.access.common.v2.Action.EXECUTE,
                authorizationService.toPermissionScopeAction(DISTRIBUTE));
    }

    @Test
    void isGranted_releaseBundleV1NotEntitled_returnsFalse() {
        RepoPath repoPath = mock(RepoPath.class);
        doReturn("release-bundles").when(repoPath).getRepoKey();
        doReturn(false).when(addons).isEntitled(RELEASE_BUNDLES_READ);
        UserInfo userInfo = mock(UserInfo.class);
        assertFalse(authorizationService.isGranted(repoPath, READ, userInfo));
        GroupInfo groupInfo = mock(GroupInfo.class);
        assertFalse(authorizationService.isGranted(repoPath, READ, groupInfo));
    }

    @Test
    void isGranted_internalMetadataPath_user_returnsTrueWithoutQueryingAccess() {
        // .jfrog internal-metadata paths are accessible to all, mirroring the Legacy bypass,
        // so the new implementation must short-circuit to true without consulting Access.
        RepoPath repoPath = RepoPathFactory.create("cool-repo", ".jfrog/attestations/test-package/1.0.0.json");
        UserInfo userInfo = mock(UserInfo.class);
        assertTrue(authorizationService.isGranted(repoPath, READ, userInfo));
        verify(accessService, never()).getAccessClient();
    }

    @Test
    void isGranted_internalMetadataPath_group_returnsTrueWithoutQueryingAccess() {
        // .jfrog internal-metadata paths are accessible to all, mirroring the Legacy bypass,
        // so the new implementation must short-circuit to true without consulting Access.
        RepoPath repoPath = RepoPathFactory.create("cool-repo", ".jfrog/security/keys.json");
        GroupInfo groupInfo = mock(GroupInfo.class);
        assertTrue(authorizationService.isGranted(repoPath, READ, groupInfo));
        verify(accessService, never()).getAccessClient();
    }

    @Test
    void isGranted_projectScopeToken_returnsFalse() {
        // A project-scoped token (applied-permissions/roles) is not honored by repo-path checks here,
        // so access is denied without consulting Access.
        RepoPath repoPath = RepoPathFactory.create("generic-local", "path/to/artifact");
        UserInfo userInfo = new UserImpl("cool-user");
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        doReturn(List.of("applied-permissions/roles:project-reader")).when(jwtAccessToken).getScope();
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        doReturn(jwtAccessToken).when(authentication).getAccessToken();
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
        assertFalse(authorizationService.isGranted(repoPath, READ, userInfo));
    }

    @Test
    void isGranted_appliedPermissionsGroupsToken_doesNotResolveTokenCreatorAsUser() {
        // Regression: when the access token has scope `applied-permissions/groups`, UserInfo.getUsername()
        // carries the token creator (e.g. "admin"), not an effective principal. The principal must be
        // built from the token-scope groups only — never by resolving "admin" through Access.
        RepoPath repoPath = RepoPathFactory.create("generic-local", "path/to/artifact");
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isAppliedPermissionsGroups();
        UserGroupInfo group = mock(UserGroupInfo.class);
        doReturn("dev").when(group).getGroupName();
        doReturn(Set.of(group)).when(userInfo).getGroups();

        doReturn(false).when(repositoryService).existsByType(any(), eq("generic-local"));
        doReturn(true).when(repositoryService).existsByType(LOCAL, "generic-local");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));

        boolean result = authorizationService.isGranted(repoPath, READ, userInfo);

        assertTrue(result);
        verify(accessClient, never()).cachedUsers();
        verify(userInfo, never()).getUsername();
        verify(authClient).principalCanDo(any(), any(), eq(true));
    }

    @Test
    void isGranted_identityTokenEvenWithGroupsScopeFlag_stillResolvesUser() {
        // Identity tokens (`applied-permissions/user` or `member-of-groups:*`) keep user-scope behavior;
        // mirrors Legacy's `(user.isIdentity() || !user.isAppliedPermissionsGroups())` guard.
        RepoPath repoPath = RepoPathFactory.create("generic-local", "path/to/artifact");
        UserInfo userInfo = mock(UserInfo.class);
        doReturn(true).when(userInfo).isAppliedPermissionsGroups();
        doReturn(true).when(userInfo).isIdentity();
        doReturn("real-user").when(userInfo).getUsername();
        UserGroupInfo group = mock(UserGroupInfo.class);
        doReturn("dev").when(group).getGroupName();
        doReturn(Set.of(group)).when(userInfo).getGroups();

        doReturn(false).when(repositoryService).existsByType(any(), eq("generic-local"));
        doReturn(true).when(repositoryService).existsByType(LOCAL, "generic-local");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.of(mock(User.class))).when(usersClient).findUserByUsername(eq("real-user"), eq(true));
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));

        boolean result = authorizationService.isGranted(repoPath, READ, userInfo);

        assertTrue(result);
        verify(usersClient).findUserByUsername(eq("real-user"), eq(true));
    }

    @Test
    void createAccessPrincipal_userResolved_passesUserOnlyAndDoesNotBlendAuthenticationGroups() {
        // RTDEV-88055: once Access resolves the user, it resolves the user's group memberships and the
        // associated permissions internally. We therefore pass only the user and never blend the
        // authentication-cache group snapshot into the principal.
        RepoPath repoPath = RepoPathFactory.create("generic-local", "path/to/artifact");
        UserInfo userInfo = mock(UserInfo.class);
        doReturn("real-user").when(userInfo).getUsername();
        UserGroupInfo group = mock(UserGroupInfo.class);
        doReturn("dev").when(group).getGroupName();
        doReturn(Set.of(group)).when(userInfo).getGroups();

        doReturn(false).when(repositoryService).existsByType(any(), eq("generic-local"));
        doReturn(true).when(repositoryService).existsByType(LOCAL, "generic-local");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        User user = mock(User.class);
        doReturn("real-user").when(user).getUsername();
        doReturn(Optional.of(user)).when(usersClient).findUserByUsername(eq("real-user"), eq(true));
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));

        assertTrue(authorizationService.isGranted(repoPath, READ, userInfo));

        ArgumentCaptor<AccessPrincipal> principalCaptor = ArgumentCaptor.forClass(AccessPrincipal.class);
        verify(authClient).principalCanDo(principalCaptor.capture(), any(), eq(true));
        AccessPrincipalImpl principal = (AccessPrincipalImpl) principalCaptor.getValue();
        assertEquals("real-user", principal.getName());
        assertTrue(principal.getUser().isPresent());
        assertTrue(principal.getGroupNames().isEmpty(),
                "authentication group 'dev' must not be blended into the principal; Access resolves groups itself");
    }

    @Test
    void createAccessPrincipal_userMissingButAuthGroupsPresent_buildsGroupsOnlyPrincipal() {
        // Degraded fallback: when Access cannot resolve the user but the authentication carries groups, we must
        // still build a groups-only principal (Legacy parity) rather than failing with an empty principal.
        RepoPath repoPath = RepoPathFactory.create("generic-local", "path/to/artifact");
        UserInfo userInfo = mock(UserInfo.class);
        doReturn("ghost-user").when(userInfo).getUsername();
        UserGroupInfo group = mock(UserGroupInfo.class);
        doReturn("dev").when(group).getGroupName();
        doReturn(Set.of(group)).when(userInfo).getGroups();

        doReturn(false).when(repositoryService).existsByType(any(), eq("generic-local"));
        doReturn(true).when(repositoryService).existsByType(LOCAL, "generic-local");
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(usersClient).when(accessClient).cachedUsers();
        doReturn(Optional.empty()).when(usersClient).findUserByUsername(eq("ghost-user"), eq(true));
        doReturn(authClient).when(accessClient).cachedAuth();
        doReturn(true).when(authClient).principalCanDo(any(), any(), eq(true));

        assertTrue(authorizationService.isGranted(repoPath, READ, userInfo));

        ArgumentCaptor<AccessPrincipal> principalCaptor = ArgumentCaptor.forClass(AccessPrincipal.class);
        verify(authClient).principalCanDo(principalCaptor.capture(), any(), eq(true));
        AccessPrincipalImpl principal = (AccessPrincipalImpl) principalCaptor.getValue();
        assertTrue(principal.getUser().isEmpty());
        assertTrue(principal.getGroupNames().contains("dev"));
    }

    @Test
    void isGrantedByAccessToken_returnsAccessTokenDecision() {
        // Logs in by the request's auth header and delegates the decision for the given scope to Access.
        PermissionScope permissionScope = mock(PermissionScope.class);
        AccessPrincipal accessPrincipal = stubAccessTokenLogin(permissionScope);
        assertTrue(authorizationService.isGrantedByAccessToken(permissionScope));
        verify(authClient).principalCanDo(accessPrincipal, permissionScope, true);
    }

    @Test
    void principalCanDo_returnsAccessTokenDecision() {
        // Same token-login path as isGrantedByAccessToken, exercised through the public principalCanDo entry point.
        PermissionScope permissionScope = mock(PermissionScope.class);
        AccessPrincipal accessPrincipal = stubAccessTokenLogin(permissionScope);
        assertTrue(authorizationService.principalCanDo(permissionScope));
        verify(authClient).principalCanDo(accessPrincipal, permissionScope, true);
    }

    /**
     * Wires an access-token authentication whose header login resolves to a principal that Access grants the
     * given {@code scope}. Returns the resolved {@link AccessPrincipal} so callers can verify the delegation.
     */
    private AccessPrincipal stubAccessTokenLogin(PermissionScope scope) {
        doReturn(accessClient).when(accessService).getAccessClient();
        doReturn(authClient).when(accessClient).cachedAuth();
        JwtAccessToken jwtAccessToken = mock(JwtAccessToken.class);
        doReturn("test-token").when(jwtAccessToken).getTokenValue();
        AccessTokenAuthentication authentication = mock(AccessTokenAuthentication.class);
        doReturn(jwtAccessToken).when(authentication).getAccessToken();
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);
        doReturn(authentication).when(securityContext).getAuthentication();
        LoginResult loginResult = mock(LoginResult.class);
        AccessPrincipal accessPrincipal = mock(AccessPrincipal.class);
        doReturn(accessPrincipal).when(loginResult).getPrincipal();
        doReturn(loginResult).when(authClient).loginByAuthHeader(anyString());
        doReturn(true).when(authClient).principalCanDo(accessPrincipal, scope, true);
        return accessPrincipal;
    }
}
