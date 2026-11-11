package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.api.permission.BuildPermissionsModel;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetEffectivePermissionUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_DATE_EPOCH = "1700000000000";
    private static final String BUILD_STARTED = formatBuildTime(Long.parseLong(BUILD_DATE_EPOCH));

    private BuildGetEffectivePermissionUiCommand command;

    private BuildRepoService repoService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetEffectivePermissionUiCommand(repoService, permissionService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermission_shouldAssertManageThenReturnModel() {
        BuildPermissionsModel model = new BuildPermissionsModel(List.of(), List.of(), List.of());
        when(permissionService.getBuildEffectivePermissions(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY)).thenReturn(model);

        BuildPermissionsModel result =
                command.getEffectivePermission(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH, BUILDINFO_REPO_KEY);

        assertSame(model, result);
        // Manage permission must be asserted (with the formatted started date) before fetching permissions.
        InOrder inOrder = inOrder(permissionService);
        inOrder.verify(permissionService).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY);
        inOrder.verify(permissionService).getBuildEffectivePermissions(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermission_shouldThrowForbidden_whenManageDenied() {
        doThrow(new RuntimeException("forbidden")).when(permissionService)
                .assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        assertThrows(RuntimeException.class,
                () -> command.getEffectivePermission(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH, BUILDINFO_REPO_KEY));

        verify(permissionService, never()).getBuildEffectivePermissions(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermission_shouldThrowBadRequest_whenDateNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getEffectivePermission(BUILD_NAME, BUILD_NUMBER, "not-a-number", BUILDINFO_REPO_KEY));

        assertTrue(thrown.getMessage().contains("Invalid buildDate format"));
        verify(permissionService, never()).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, "not-a-number",
                BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermission_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(permissionService.getBuildEffectivePermissions(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY)).thenReturn(new BuildPermissionsModel(List.of(), List.of(), List.of()));

        command.getEffectivePermission(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(permissionService).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);
    }
}
