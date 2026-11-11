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

class BuildGetEffectivePermissionByGroupUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "5";
    private static final String BUILD_DATE_EPOCH = "1700000000000";
    private static final String BUILD_STARTED = formatBuildTime(Long.parseLong(BUILD_DATE_EPOCH));
    private static final String GROUP_NAME = "developers";

    private BuildGetEffectivePermissionByGroupUiCommand command;

    private BuildRepoService repoService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetEffectivePermissionByGroupUiCommand(repoService, permissionService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getEffectivePermissionByGroup_shouldAssertManageThenReturnPermissionTargets() {
        List<String> targets = List.of("target-a", "target-b");
        when(permissionService.getBuildPermissionsByGroup(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY,
                GROUP_NAME)).thenReturn(targets);

        List<String> result = command.getEffectivePermissionByGroup(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH,
                BUILDINFO_REPO_KEY, GROUP_NAME);

        assertEquals(targets, result);
        InOrder inOrder = inOrder(permissionService);
        inOrder.verify(permissionService).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY);
        inOrder.verify(permissionService).getBuildPermissionsByGroup(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY, GROUP_NAME);
    }

    @Test
    void getEffectivePermissionByGroup_shouldThrowBadRequest_whenBuildCoordinateMissing() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getEffectivePermissionByGroup(BUILD_NAME, BUILD_NUMBER, null, BUILDINFO_REPO_KEY,
                        GROUP_NAME));

        assertTrue(thrown.getMessage().contains("buildName, buildNumber, buildDate"));
        verifyNoInteractions(permissionService);
    }

    @Test
    void getEffectivePermissionByGroup_shouldThrowBadRequest_whenDateNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.getEffectivePermissionByGroup(BUILD_NAME, BUILD_NUMBER, "not-a-number",
                        BUILDINFO_REPO_KEY, GROUP_NAME));

        assertTrue(thrown.getMessage().contains("Invalid buildDate format"));
        verifyNoInteractions(permissionService);
    }

    @Test
    void getEffectivePermissionByGroup_shouldThrowForbidden_whenManageDenied() {
        doThrow(new RuntimeException("forbidden")).when(permissionService)
                .assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);

        assertThrows(RuntimeException.class, () -> command.getEffectivePermissionByGroup(BUILD_NAME, BUILD_NUMBER,
                BUILD_DATE_EPOCH, BUILDINFO_REPO_KEY, GROUP_NAME));

        verify(permissionService, never()).getBuildPermissionsByGroup(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED,
                BUILDINFO_REPO_KEY, GROUP_NAME);
    }

    @Test
    void getEffectivePermissionByGroup_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(permissionService.getBuildPermissionsByGroup(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY,
                GROUP_NAME)).thenReturn(List.of());

        command.getEffectivePermissionByGroup(BUILD_NAME, BUILD_NUMBER, BUILD_DATE_EPOCH, null, GROUP_NAME);

        verify(repoService).normaliseBuildRepoName(null);
        verify(permissionService).assertCanManageBuild(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED, BUILDINFO_REPO_KEY);
    }
}
