package com.jfrog.build.gateway;

import com.google.common.collect.ImmutableSetMultimap;
import com.jfrog.build.acl.model.api.permission.BuildPermissionTargetInfo;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.acl.model.api.permission.PrincipalEffectivePermissions;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.exception.http.InternalServerException;
import org.artifactory.repo.RepoPath;
import org.artifactory.security.BuildAcl;
import org.artifactory.security.BuildPermissionTarget;
import org.artifactory.security.MutableAceInfo;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.mime.NamingUtils;
import org.artifactory.util.AolUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.artifactory.aol.AolService.SUPER_USER_NAME;
import static org.jfrog.access.common.PermissionScopeResourceType.BUILD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildPermissionGatewayTest {

    private static final String NAME = "my-build";
    private static final String NUMBER = "1";
    private static final String STARTED = "2024-01-01T00:00:00.000+0000";
    private static final String REPO = "artifactory-build-info";

    private AuthorizationService authorizationService;
    private UserGroupService userGroupService;
    private BuildPermissionGateway gateway;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        userGroupService = mock(UserGroupService.class);
        gateway = new BuildPermissionGateway(authorizationService, userGroupService);
        // Defaults so the aggregation helpers don't NPE when a test doesn't care about admins/groups.
        when(userGroupService.getAllUsersAndAdminStatus(true)).thenReturn(Map.of());
        when(userGroupService.getAllAdminGroupsNames()).thenReturn(List.of());
    }

    @Test
    void hasPermission_shouldDelegateToAuthorizationService() {
        when(authorizationService.hasPermission(eq(BUILD), any(RepoPath.class), any())).thenReturn(true);
        assertTrue(gateway.hasPermission(BUILD, "read", "repo-local/path"));
    }

    @Test
    void hasPermission_shouldWrapFailuresInInternalServerException() {
        when(authorizationService.hasPermission(eq(BUILD), any(RepoPath.class), any()))
                .thenThrow(new RuntimeException("boom"));
        assertThrows(InternalServerException.class, () -> gateway.hasPermission(BUILD, "read", "repo-local/path"));
    }

    @Test
    void getArtifactActions_shouldReturnEmpty_whenNoReadPermission() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(false);
        assertTrue(gateway.getArtifactActions("repo-local", "lib/lib.jar").isEmpty());
    }

    @Test
    void getArtifactActions_shouldReturnDownloadAndShowInTree_forNonViewable() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        try (MockedStatic<NamingUtils> naming = mockStatic(NamingUtils.class)) {
            naming.when(() -> NamingUtils.isViewable("lib/lib.jar")).thenReturn(false);
            assertEquals(List.of("Download", "ShowInTree"), gateway.getArtifactActions("repo-local", "lib/lib.jar"));
        }
    }

    @Test
    void getArtifactActions_shouldAddView_forClassFile() {
        when(authorizationService.canRead(any(RepoPath.class))).thenReturn(true);
        try (MockedStatic<NamingUtils> naming = mockStatic(NamingUtils.class)) {
            naming.when(() -> NamingUtils.isViewable("com/example/Foo.class")).thenReturn(false);
            assertEquals(List.of("Download", "ShowInTree", "View"),
                    gateway.getArtifactActions("repo-local", "com/example/Foo.class"));
        }
    }

    @Test
    void getBuildEffectivePermissions_shouldAggregateAdminsAclsAndGroupExpansion() {
        try (MockedStatic<AolUtils> aol = mockStatic(AolUtils.class)) {
            aol.when(AolUtils::isAol).thenReturn(false);

            when(userGroupService.getAllUsersAndAdminStatus(true)).thenReturn(Map.of("admin", true));
            when(userGroupService.getAllAdminGroupsNames()).thenReturn(List.of("admin-group"));
            // user "bob" belongs to group "devs" -> devs permissions are copied to bob
            when(userGroupService.getAllUsersInGroups()).thenReturn(ImmutableSetMultimap.of("bob", "devs"));

            MutableAceInfo aliceAce = ace("alice", false, true);
            MutableAceInfo devsAce = ace("devs", true, false);
            BuildAcl acl = acl(List.of("**"), List.of(), aliceAce, devsAce);
            when(authorizationService.getBuildPathAcls(NAME, NUMBER, STARTED, REPO)).thenReturn(List.of(acl));

            BuildPermissionsModel model = gateway.getBuildEffectivePermissions(NAME, NUMBER, STARTED, REPO);

            // users: admin (admin), alice (ACL), bob (inherited from devs)
            assertEquals(3, model.getUserEffectivePermissions().size());
            PrincipalEffectivePermissions admin = principal(model.getUserEffectivePermissions(), "admin");
            assertTrue(admin.isAdmin());
            assertTrue(admin.getPermission().isRead());
            PrincipalEffectivePermissions alice = principal(model.getUserEffectivePermissions(), "alice");
            assertTrue(alice.getPermission().isRead());
            assertTrue(alice.getPermission().isDeploy());
            assertTrue(alice.getPermissionTargets().contains("pt1"));
            PrincipalEffectivePermissions bob = principal(model.getUserEffectivePermissions(), "bob");
            assertTrue(bob.getPermission().isRead());
            assertTrue(bob.getPermissionTargets().contains("pt1"));

            // groups: admin-group (admin), devs (ACL)
            assertEquals(2, model.getGroupEffectivePermissions().size());
            assertTrue(principal(model.getGroupEffectivePermissions(), "admin-group").isAdmin());
            assertTrue(principal(model.getGroupEffectivePermissions(), "devs").getPermission().isRead());

            // permission targets
            assertEquals(1, model.getPermissionTargets().size());
            BuildPermissionTargetInfo target = model.getPermissionTargets().getFirst();
            assertEquals("pt1", target.getPermissionName());
            assertTrue(target.getUsers().contains("alice"));
            assertTrue(target.getGroups().contains("devs"));
            assertEquals(List.of("**"), target.getIncludePatterns());
        }
    }

    @Test
    void getBuildEffectivePermissions_shouldSkipSuperUser_whenAol() {
        try (MockedStatic<AolUtils> aol = mockStatic(AolUtils.class)) {
            aol.when(AolUtils::isAol).thenReturn(true);
            when(userGroupService.getAllUsersAndAdminStatus(true))
                    .thenReturn(new java.util.HashMap<>(Map.of("admin", true, SUPER_USER_NAME, true)));
            when(authorizationService.getBuildPathAcls(NAME, NUMBER, STARTED, REPO)).thenReturn(List.of());

            BuildPermissionsModel model = gateway.getBuildEffectivePermissions(NAME, NUMBER, STARTED, REPO);

            // the AOL super user is removed; only "admin" remains
            assertFalse(model.getUserEffectivePermissions().stream()
                    .anyMatch(p -> SUPER_USER_NAME.equals(p.getPrincipal())));
            assertTrue(model.getUserEffectivePermissions().stream()
                    .anyMatch(p -> "admin".equals(p.getPrincipal())));
        }
    }

    @Test
    void getBuildEffectivePermissionsByRepoPath_shouldUseAllBuildAcls_forRoot() {
        try (MockedStatic<AolUtils> aol = mockStatic(AolUtils.class)) {
            aol.when(AolUtils::isAol).thenReturn(false);
            when(authorizationService.getAllBuildAcls(true)).thenReturn(List.of());

            BuildPermissionsModel model = gateway.getBuildEffectivePermissionsByRepoPath(REPO, "");

            assertTrue(model.getPermissionTargets().isEmpty());
            verify(authorizationService).getAllBuildAcls(true);
        }
    }

    @Test
    void getBuildEffectivePermissionsByRepoPath_shouldUsePathAcls_forNonRoot() {
        try (MockedStatic<AolUtils> aol = mockStatic(AolUtils.class)) {
            aol.when(AolUtils::isAol).thenReturn(false);
            when(authorizationService.getBuildPathAcls(any(RepoPath.class), eq(true))).thenReturn(List.of());

            BuildPermissionsModel model = gateway.getBuildEffectivePermissionsByRepoPath(REPO, "my-build/1");

            assertTrue(model.getPermissionTargets().isEmpty());
            verify(authorizationService).getBuildPathAcls(any(RepoPath.class), eq(true));
        }
    }

    @Test
    void getBuildPermissionsByUser_shouldReturnEmpty_whenUserNotFound() {
        when(userGroupService.findUser("ghost")).thenThrow(new UsernameNotFoundException("no user"));
        assertTrue(gateway.getBuildPermissionsByUser(NAME, NUMBER, STARTED, REPO, "ghost").isEmpty());
    }

    @Test
    void getBuildPermissionsByUser_shouldMatchByPrincipal() {
        stubUserWithGroups("alice", Set.of());
        BuildAcl acl = acl(List.of(), List.of(), ace("alice", false, false));
        when(authorizationService.getBuildPathAcls(NAME, NUMBER, STARTED, REPO)).thenReturn(List.of(acl));

        assertEquals(List.of("pt1"), gateway.getBuildPermissionsByUser(NAME, NUMBER, STARTED, REPO, "alice"));
    }

    @Test
    void getBuildPermissionsByUser_shouldMatchViaGroupMembership() {
        stubUserWithGroups("carol", Set.of("devs"));
        BuildAcl acl = acl(List.of(), List.of(), ace("devs", true, false));
        when(authorizationService.getBuildPathAcls(NAME, NUMBER, STARTED, REPO)).thenReturn(List.of(acl));

        assertEquals(List.of("pt1"), gateway.getBuildPermissionsByUser(NAME, NUMBER, STARTED, REPO, "carol"));
    }

    @Test
    void getBuildPermissionsByGroup_shouldMatchGroupAce() {
        BuildAcl acl = acl(List.of(), List.of(), ace("devs", true, false));
        when(authorizationService.getBuildPathAcls(NAME, NUMBER, STARTED, REPO)).thenReturn(List.of(acl));

        assertEquals(List.of("pt1"), gateway.getBuildPermissionsByGroup(NAME, NUMBER, STARTED, REPO, "devs"));
    }

    @Test
    void getBuildPermissionsByGroup_shouldReturnEmpty_whenOnlyUserAce() {
        BuildAcl acl = acl(List.of(), List.of(), ace("alice", false, false));
        when(authorizationService.getBuildPathAcls(NAME, NUMBER, STARTED, REPO)).thenReturn(List.of(acl));

        assertTrue(gateway.getBuildPermissionsByGroup(NAME, NUMBER, STARTED, REPO, "devs").isEmpty());
    }

    private void stubUserWithGroups(String username, Set<String> groupNames) {
        UserInfo userInfo = mock(UserInfo.class);
        Set<UserGroupInfo> groups = new LinkedHashSet<>();
        for (String groupName : groupNames) {
            UserGroupInfo groupInfo = mock(UserGroupInfo.class);
            when(groupInfo.getGroupName()).thenReturn(groupName);
            groups.add(groupInfo);
        }
        when(userInfo.getGroups()).thenReturn(groups);
        when(userGroupService.findUser(username)).thenReturn(userInfo);
    }

    private BuildAcl acl(List<String> includes, List<String> excludes, MutableAceInfo... aces) {
        BuildPermissionTarget target = mock(BuildPermissionTarget.class);
        when(target.getName()).thenReturn("pt1");
        when(target.getIncludes()).thenReturn(includes);
        when(target.getExcludes()).thenReturn(excludes);
        BuildAcl acl = mock(BuildAcl.class);
        when(acl.getPermissionTarget()).thenReturn(target);
        when(acl.getMutableAces()).thenReturn(new LinkedHashSet<>(asList(aces)));
        return acl;
    }

    private MutableAceInfo ace(String principal, boolean group, boolean deploy) {
        MutableAceInfo ace = mock(MutableAceInfo.class);
        when(ace.getPrincipal()).thenReturn(principal);
        when(ace.isGroup()).thenReturn(group);
        when(ace.canRead()).thenReturn(true);
        when(ace.canDeploy()).thenReturn(deploy);
        return ace;
    }

    private PrincipalEffectivePermissions principal(List<PrincipalEffectivePermissions> permissions, String name) {
        Optional<PrincipalEffectivePermissions> match = permissions.stream()
                .filter(p -> name.equals(p.getPrincipal()))
                .findFirst();
        assertTrue(match.isPresent(), "Expected principal: " + name);
        return match.get();
    }
}
