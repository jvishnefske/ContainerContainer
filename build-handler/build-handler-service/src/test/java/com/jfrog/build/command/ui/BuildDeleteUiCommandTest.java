package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.status.StatusEntryLevel;
import org.artifactory.exception.http.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildDeleteUiCommandTest {

    private BuildDeleteUiCommand command;

    private BuildReadService readService;
    private BuildDeletionService deletionService;
    private BuildPermissionService permissionService;
    private MutableStatusHolder status;

    @BeforeEach
    void setUp() {
        readService = mock(BuildReadService.class);
        deletionService = mock(BuildDeletionService.class);
        permissionService = mock(BuildPermissionService.class);
        BuildRepoService repoService = mock(BuildRepoService.class);
        command = new BuildDeleteUiCommand(repoService, readService, deletionService, permissionService);
        status = mock(MutableStatusHolder.class);
        when(deletionService.newStatusHolder()).thenReturn(status);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void deleteBuilds_shouldDeleteSpecificBuildAndReturnMessage_whenOneCoordinate() {
        BuildRun buildRun = mock(BuildRun.class);
        when(readService.getBuildRun(eq("build-a"), eq("1"), anyString(), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(buildRun);
        BuildCoordinate coordinate = new BuildCoordinate("build-a", "1", 0L, BUILDINFO_REPO_KEY);

        String message = command.deleteBuilds(List.of(coordinate));

        assertEquals("Successfully removed build-a #1 build", message);
        verify(permissionService).assertDeletePermissions(eq("build-a"), eq("1"), anyString(), eq(BUILDINFO_REPO_KEY));
        verify(deletionService).deleteBuild(same(buildRun), same(status), eq(false));
    }

    @Test
    void deleteBuilds_shouldReturnCountMessage_whenMultipleCoordinates() {
        when(readService.getBuildRun(anyString(), anyString(), anyString(), anyString())).thenReturn(mock(BuildRun.class));
        List<BuildCoordinate> coordinates = List.of(
                new BuildCoordinate("build-a", "1", 0L, BUILDINFO_REPO_KEY),
                new BuildCoordinate("build-b", "2", 0L, BUILDINFO_REPO_KEY));

        String message = command.deleteBuilds(coordinates);

        assertEquals("Successfully removed 2 builds", message);
    }

    @Test
    void deleteBuilds_shouldReturnErrorMessage_whenDeletionReportsError() {
        when(readService.getBuildRun(anyString(), anyString(), anyString(), anyString())).thenReturn(mock(BuildRun.class));
        when(status.isError()).thenReturn(true);
        when(status.getErrors()).thenReturn(List.of(new StatusEntry(500, StatusEntryLevel.ERROR, "could not delete")));
        BuildCoordinate coordinate = new BuildCoordinate("build-a", "1", 0L, BUILDINFO_REPO_KEY);

        String message = command.deleteBuilds(List.of(coordinate));
        assertEquals("could not delete", message);
    }

    @Test
    void deleteBuilds_shouldPropagateForbidden_whenPermissionDenied() {
        doThrowForbiddenOnAssert();
        BuildCoordinate coordinate = new BuildCoordinate("build-a", "1", 0L, BUILDINFO_REPO_KEY);

        assertThrows(ForbiddenException.class, () -> command.deleteBuilds(List.of(coordinate)));

        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean());
    }

    @Test
    void deleteBuilds_shouldReturnEmpty_whenCoordinatesNullOrEmpty() {
        assertEquals("", command.deleteBuilds(null));
        assertEquals("", command.deleteBuilds(List.of()));
        verify(deletionService, never()).deleteBuild(any(BuildRun.class), any(), anyBoolean());
        verify(deletionService, never()).deleteAllBuildsByName(anyList(), any(), anyBoolean());
    }

    private void doThrowForbiddenOnAssert() {
        doThrow(new ForbiddenException("denied"))
                .when(permissionService)
                .assertDeletePermissions(anyString(), anyString(), anyString(), anyString());
    }
}
