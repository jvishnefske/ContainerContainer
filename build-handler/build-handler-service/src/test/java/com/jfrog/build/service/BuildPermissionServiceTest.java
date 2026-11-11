package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildSecurityDescriptor;
import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import org.artifactory.exception.http.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.service.BuildPermissionService.PERMISSION_ANNOTATE;
import static com.jfrog.build.service.BuildPermissionService.PERMISSION_DELETE;
import static com.jfrog.build.service.BuildPermissionService.PERMISSION_MANAGE;
import static com.jfrog.build.service.BuildPermissionService.PERMISSION_READ;
import static com.jfrog.build.service.BuildPermissionService.PERMISSION_WRITE;
import static org.jfrog.access.common.PermissionScopeResourceType.ARTIFACT;
import static org.jfrog.access.common.PermissionScopeResourceType.BUILD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildPermissionServiceTest {

    private static final String NAME = "my-build";
    private static final String NUMBER = "1";
    private static final String STARTED = "2024-01-01T00:00:00.000+0000";
    private static final String REPO = "artifactory-build-info";

    private BuildBridgeToArtifactory bridge;
    private BuildPermissionService service;

    @BeforeEach
    void setUp() {
        bridge = mock(BuildBridgeToArtifactory.class);
        service = new BuildPermissionService(bridge);
        when(bridge.getCurrentUsername()).thenReturn("tester");
        when(bridge.getSecurityDescriptor()).thenReturn(BuildSecurityDescriptor.builder().build());
    }

    private void allowGlobalBasicRead(boolean allowed, boolean forAnonymous) {
        when(bridge.getSecurityDescriptor()).thenReturn(BuildSecurityDescriptor.builder()
                .buildGlobalBasicReadAllowed(allowed)
                .buildGlobalBasicReadForAnonymous(forAnonymous)
                .build());
    }

    @Test
    void getArtifactActions_shouldDelegateToBridge() {
        when(bridge.getArtifactActions("repo", "path")).thenReturn(List.of("Download", "View"));
        assertEquals(List.of("Download", "View"), service.getArtifactActions("repo", "path"));
    }

    @Test
    void getBuildEffectivePermissions_shouldDelegateToBridge() {
        BuildPermissionsModel model = new BuildPermissionsModel(List.of(), List.of(), List.of());
        when(bridge.getBuildEffectivePermissions(NAME, NUMBER, STARTED, REPO)).thenReturn(model);
        assertEquals(model, service.getBuildEffectivePermissions(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void getBuildEffectivePermissionsByRepoPath_shouldDelegateToBridge() {
        BuildPermissionsModel model = new BuildPermissionsModel(List.of(), List.of(), List.of());
        when(bridge.getBuildEffectivePermissionsByRepoPath(REPO, "path")).thenReturn(model);
        assertEquals(model, service.getBuildEffectivePermissionsByRepoPath(REPO, "path"));
    }

    @Test
    void getBuildPermissionsByUser_shouldDelegateToBridge() {
        when(bridge.getBuildPermissionsByUser(NAME, NUMBER, STARTED, REPO, "alice")).thenReturn(List.of("pt"));
        assertEquals(List.of("pt"), service.getBuildPermissionsByUser(NAME, NUMBER, STARTED, REPO, "alice"));
    }

    @Test
    void getBuildPermissionsByGroup_shouldDelegateToBridge() {
        when(bridge.getBuildPermissionsByGroup(NAME, NUMBER, STARTED, REPO, "devs")).thenReturn(List.of("pt"));
        assertEquals(List.of("pt"), service.getBuildPermissionsByGroup(NAME, NUMBER, STARTED, REPO, "devs"));
    }

    @Test
    void hasBasicReadPermissionForAllBuilds_shouldBeTrue_whenAdmin() {
        when(bridge.isAdmin()).thenReturn(true);
        assertTrue(service.hasBasicReadPermissionForAllBuilds());
    }

    @Test
    void hasBasicReadPermissionForAllBuilds_shouldUseGlobalFlag_whenNotAdmin() {
        when(bridge.isAdmin()).thenReturn(false);
        allowGlobalBasicRead(true, false);
        assertTrue(service.hasBasicReadPermissionForAllBuilds());
    }

    @Test
    void hasBasicReadPermissionForAllBuilds_shouldBeFalse_whenNotAdminAndGlobalDisallowed() {
        when(bridge.isAdmin()).thenReturn(false);
        allowGlobalBasicRead(false, false);
        assertFalse(service.hasBasicReadPermissionForAllBuilds());
    }

    @Test
    void globalBasicRead_forAnonymous_requiresBothFlags() {
        when(bridge.isAnonymous()).thenReturn(true);
        allowGlobalBasicRead(true, false);
        // allowed but not-for-anonymous -> false for an anonymous user
        assertFalse(service.isBuildGlobalBasicReadAllowed());

        allowGlobalBasicRead(true, true);
        assertTrue(service.isBuildGlobalBasicReadAllowed());
    }

    @Test
    void canReadArtifact_shouldDelegateToHasPermission() {
        when(bridge.hasPermission(ARTIFACT, PERMISSION_READ, "repo/path")).thenReturn(true);
        assertTrue(service.canReadArtifact("repo/path"));
    }

    @Test
    void canAnnotate_shouldDelegateToHasPermission() {
        when(bridge.hasPermission(ARTIFACT, PERMISSION_ANNOTATE, "repo/path")).thenReturn(true);
        assertTrue(service.canAnnotate("repo/path"));
    }

    @Test
    void canWriteArtifact_shouldDelegateToHasPermission() {
        when(bridge.hasPermission(ARTIFACT, PERMISSION_WRITE, "repo/path")).thenReturn(true);
        assertTrue(service.canWriteArtifact("repo/path"));
    }

    @Test
    void canDeleteArtifact_shouldDelegateToHasPermission() {
        when(bridge.hasPermission(ARTIFACT, PERMISSION_DELETE, "repo/path")).thenReturn(true);
        assertTrue(service.canDeleteArtifact("repo/path"));
    }

    @Test
    void canManageArtifact_shouldDelegateToHasPermission() {
        when(bridge.hasPermission(ARTIFACT, PERMISSION_MANAGE, "repo/path")).thenReturn(true);
        assertTrue(service.canManageArtifact("repo/path"));
    }

    @Test
    void canReadBuildPath_shouldDelegateToHasPermissionWithBuildScope() {
        when(bridge.hasPermission(BUILD, PERMISSION_READ, "repo/path")).thenReturn(true);
        assertTrue(service.canReadBuild("repo/path"));
    }

    @Test
    void canReadBuild_shouldBeTrue_viaArtifactRead() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_READ), anyString())).thenReturn(true);
        assertTrue(service.canReadBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void canReadBuild_shouldBeTrue_viaBuildReadWhenArtifactReadDenied() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_READ), anyString())).thenReturn(false);
        when(bridge.hasPermission(eq(BUILD), eq(PERMISSION_READ), anyString())).thenReturn(true);
        assertTrue(service.canReadBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void canReadBuild_shouldBeFalse_whenNeitherGranted() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_READ), anyString())).thenReturn(false);
        when(bridge.hasPermission(eq(BUILD), eq(PERMISSION_READ), anyString())).thenReturn(false);
        assertFalse(service.canReadBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void canUploadBuild_shouldUseArtifactWrite() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_WRITE), anyString())).thenReturn(true);
        assertTrue(service.canUploadBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void canDeleteBuild_shouldUseArtifactDelete() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_DELETE), anyString())).thenReturn(true);
        assertTrue(service.canDeleteBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void canManageBuild_shouldUseArtifactManage() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_MANAGE), anyString())).thenReturn(true);
        assertTrue(service.canManageBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void canDeleteBuildName_shouldUseArtifactDeleteOnRootPath() {
        when(bridge.hasPermission(ARTIFACT, PERMISSION_DELETE, REPO + "/" + NAME + "/")).thenReturn(true);
        assertTrue(service.canDeleteBuildName(NAME, REPO));
    }

    @Test
    void canDeleteBuildName_shouldThrow_whenNameBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.canDeleteBuildName("", REPO));
    }

    @Test
    void assertCanManageBuild_shouldThrow_whenNotAllowed() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_MANAGE), anyString())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertCanManageBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void assertCanManageBuild_shouldPass_whenAllowed() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_MANAGE), anyString())).thenReturn(true);
        assertDoesNotThrow(() -> service.assertCanManageBuild(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void assertReadPermissions_shouldThrow_whenNotAllowed() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_READ), anyString())).thenReturn(false);
        when(bridge.hasPermission(eq(BUILD), eq(PERMISSION_READ), anyString())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertReadPermissions(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void assertDeletePermissions_byCoordinates_shouldThrow_whenNotAllowed() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_DELETE), anyString())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertDeletePermissions(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void assertDeletePermissions_byBuildName_shouldThrow_whenNotAllowed() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_DELETE), anyString())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertDeletePermissions(NAME, REPO));
    }

    @Test
    void assertUploadPermissions_shouldThrow_whenNotAllowed() {
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_WRITE), anyString())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertUploadPermissions(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void assertBasicReadPermissions_byCoordinates_shouldThrow_whenNotAllowed() {
        allowGlobalBasicRead(false, false);
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_READ), anyString())).thenReturn(false);
        when(bridge.hasPermission(eq(BUILD), eq(PERMISSION_READ), anyString())).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertBasicReadPermissions(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void assertBasicReadPermissions_byBuildName_shouldPass_whenGlobalAllowed() {
        allowGlobalBasicRead(true, false);
        assertDoesNotThrow(() -> service.assertBasicReadPermissions(NAME, REPO));
    }

    @Test
    void assertCanViewBuildPermissions_shouldThrow_whenNoPrivilege() {
        when(bridge.isAdmin()).thenReturn(false);
        when(bridge.isProjectAdmin("proj")).thenReturn(false);
        when(bridge.isResourceManager()).thenReturn(false);
        assertThrows(ForbiddenException.class, () -> service.assertCanViewBuildPermissions("proj"));
    }

    @Test
    void assertCanViewBuildPermissions_shouldPass_whenResourceManager() {
        when(bridge.isAdmin()).thenReturn(false);
        when(bridge.isProjectAdmin("proj")).thenReturn(false);
        when(bridge.isResourceManager()).thenReturn(true);
        assertDoesNotThrow(() -> service.assertCanViewBuildPermissions("proj"));
    }

    @Test
    void isBuildBasicRead_byBuildRun_shouldBeTrue_whenGlobalAllowed() {
        allowGlobalBasicRead(true, false);
        BuildRun run = mock(BuildRun.class);
        when(run.getName()).thenReturn(NAME);
        when(run.getNumber()).thenReturn(NUMBER);
        when(run.getStarted()).thenReturn(STARTED);
        when(run.getBuildRepo()).thenReturn(REPO);

        assertTrue(service.isBuildBasicRead(run));
    }

    @Test
    void isBuildBasicRead_byCoordinates_shouldFallBackToReadPermission() {
        allowGlobalBasicRead(false, false);
        when(bridge.hasPermission(eq(ARTIFACT), eq(PERMISSION_READ), anyString())).thenReturn(true);
        assertTrue(service.isBuildBasicRead(NAME, NUMBER, STARTED, REPO));
    }

    @Test
    void isBuildBasicRead_byBuildName_shouldFallBackToReadOnBuildRootPath() {
        allowGlobalBasicRead(false, false);
        when(bridge.hasPermission(ARTIFACT, PERMISSION_READ, REPO + "/" + NAME + "/")).thenReturn(true);
        assertTrue(service.isBuildBasicRead(NAME, REPO));
    }
}
