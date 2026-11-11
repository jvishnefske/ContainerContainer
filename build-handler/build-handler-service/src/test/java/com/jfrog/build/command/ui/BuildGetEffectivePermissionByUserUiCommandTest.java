package com.jfrog.build.command.ui;

import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BuildGetEffectivePermissionByUserUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_DATE_EPOCH = "1700000000000";
    private static final String BUILD_STARTED = formatBuildTime(Long.parseLong(BUILD_DATE_EPOCH));
    private static final String USERNAME = "alice";

    private BuildGetEffectivePermissionByUserUiCommand command;

    private BuildRepoService repoService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetEffectivePermissionByUserUiCommand(repoService, permissionService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermissionByUser_shouldAssertManageThenReturnPermissionTargets() {
        List<String> targets = List.of("target-a", "target-b");
        when(permissionService.getBuildPermissionsByUser(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY,
                USERNAME)).thenReturn(targets);

        List<String> result = command.getEffectivePermissionByUser(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH,
                BUILDINFO_REPO_KEY, USERNAME);

        assertEquals(targets, result);
        // Manage permission must be asserted (with the formatted started date) before fetching permissions.
        InOrder inOrder = inOrder(permissionService);
        inOrder.verify(permissionService).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY);
        inOrder.verify(permissionService).getBuildPermissionsByUser(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY, USERNAME);
    }

    @Test
    void getEffectivePermissionByUser_shouldThrowBadRequest_whenBuildCoordinateMissing() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getEffectivePermissionByUser(BUILD_NAME, null, BUILD_DATE_EPOCH, BUILDINFO_REPO_KEY,
                        USERNAME));

        assertTrue(thrown.getMessage().contains("buildName, buildNumber, buildDate"));
        verifyNoInteractions(permissionService);
    }

    @Test
    void getEffectivePermissionByUser_shouldThrowBadRequest_whenDateNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getEffectivePermissionByUser(BUILD_NAME, BUILD_NUMBER, "not-a-number",
                        BUILDINFO_REPO_KEY, USERNAME));

        assertTrue(thrown.getMessage().contains("Invalid buildDate format"));
        verifyNoInteractions(permissionService);
    }

    @Test
    void getEffectivePermissionByUser_shouldThrowForbidden_whenManageDenied() {
        doThrow(new RuntimeException("forbidden")).when(permissionService)
                .assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        assertThrows(RuntimeException.class, () -> command.getEffectivePermissionByUser(BUILD_NAME, BUILD_NUMBER,
                BUILD_DATE_EPOCH, BUILDINFO_REPO_KEY, USERNAME));

        verify(permissionService, never()).getBuildPermissionsByUser(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY, USERNAME);
    }

    @Test
    void getEffectivePermissionByUser_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(permissionService.getBuildPermissionsByUser(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY,
                USERNAME)).thenReturn(List.of());

        command.getEffectivePermissionByUser(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH, null, USERNAME);

        verify(repoService).normaliseBuildRepoName(null);
        verify(permissionService).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);
    }
}
