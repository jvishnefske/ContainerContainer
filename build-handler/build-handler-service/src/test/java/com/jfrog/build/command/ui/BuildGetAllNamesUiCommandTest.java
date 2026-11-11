package com.jfrog.build.command.ui;

import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.InternalServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetAllNamesUiCommandTest {

    private BuildGetAllNamesUiCommand command;

    private BuildReadService readService;
    private BuildRepoService repoService;

    @BeforeEach
    void setUp() {
        readService = mock(BuildReadService.class);
        repoService = mock(BuildRepoService.class);
        command = new BuildGetAllNamesUiCommand(readService, repoService);
    }

    @Test
    void getAllBuildNames_shouldReturnBuildNames_whenReadServiceSucceeds() {
        List<String> buildNames = List.of("build-a", "build-b");
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
        when(readService.getBuildNames(BUILDINFO_REPO_KEY)).thenReturn(buildNames);

        List<String> result = command.getAllBuildNames(BUILDINFO_REPO_KEY);

        assertEquals(buildNames, result);
    }

    @Test
    void getAllBuildNames_shouldNormaliseBuildRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(readService.getBuildNames(BUILDINFO_REPO_KEY)).thenReturn(List.of());

        command.getAllBuildNames(null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(readService).getBuildNames(BUILDINFO_REPO_KEY);
    }

    @Test
    void getAllBuildNames_shouldThrowInternalServerException_whenReadServiceFails() {
        when(repoService.normaliseBuildRepoName("phoenix-build-info")).thenReturn("phoenix-build-info");
        when(readService.getBuildNames("phoenix-build-info"))
                .thenThrow(new RuntimeException("database unavailable"));

        InternalServerException thrown = assertThrows(
                InternalServerException.class,
                () -> command.getAllBuildNames("phoenix-build-info"));

        assertTrue(thrown.getMessage().contains("phoenix-build-info"));
        assertTrue(thrown.getMessage().contains("database unavailable"));
    }
}
