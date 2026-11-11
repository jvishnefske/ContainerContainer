package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildCoordinate;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BuildDeleteAllUiCommandTest {

    private BuildDeleteAllUiCommand command;

    private BuildRepoService repoService;
    private BuildDeletionService deletionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        deletionService = mock(BuildDeletionService.class);
        command = new BuildDeleteAllUiCommand(repoService, deletionService);
    }

    @Test
    void deleteAllBuilds_shouldReturnSingleProjectMessage_whenOneCoordinate() {
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
        List<BuildCoordinate> coordinates = List.of(new BuildCoordinate("build-a", BUILDINFO_REPO_KEY));

        String message = command.deleteAllBuilds(coordinates);

        assertEquals("Successfully removed build-a build project", message);
        verify(deletionService).deleteAllBuildsByName(same(coordinates), any(), eq(false));
    }

    @Test
    void deleteAllBuilds_shouldReturnCountMessage_whenMultipleCoordinates() {
        List<BuildCoordinate> coordinates = List.of(
                new BuildCoordinate("build-a", BUILDINFO_REPO_KEY),
                new BuildCoordinate("build-b", BUILDINFO_REPO_KEY));

        String message = command.deleteAllBuilds(coordinates);

        assertEquals("Successfully removed 2 build projects", message);
    }

    @Test
    void deleteAllBuilds_shouldNormaliseBuildRepoBeforeDeleting() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        BuildCoordinate coordinate = new BuildCoordinate("build-a", null);

        command.deleteAllBuilds(List.of(coordinate));

        assertEquals(BUILDINFO_REPO_KEY, coordinate.getBuildRepo());
        verify(repoService).normaliseBuildRepoName(null);
    }

    @Test
    void deleteAllBuilds_shouldThrowBadRequest_whenCoordinatesNull() {
        assertThrows(BadRequestException.class, () -> command.deleteAllBuilds(null));
        verifyNoInteractions(deletionService, repoService);
    }

    @Test
    void deleteAllBuilds_shouldThrowBadRequest_whenCoordinatesEmpty() {
        assertThrows(BadRequestException.class, () -> command.deleteAllBuilds(List.of()));
        verifyNoInteractions(deletionService, repoService);
    }
}
