package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetArtifactBuildJsonUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "15";
    private static final String START_TIME = "1700000000000";
    private static final String NORMALISED_REPO = "artifactory-build-info";

    private BuildReadService readService;
    private BuildRepoService repoService;
    private BuildGetArtifactBuildJsonUiCommand command;

    @BeforeEach
    void setUp() {
        readService = mock(BuildReadService.class);
        repoService = mock(BuildRepoService.class);
        command = new BuildGetArtifactBuildJsonUiCommand(readService, repoService);
        when(repoService.normaliseBuildRepoName(null)).thenReturn(NORMALISED_REPO);
    }

    @Test
    void getArtifactBuildJson_shouldReturnJson_whenBuildExists() {
        BuildRun buildRun = mock(BuildRun.class);
        when(readService.getBuildRun(eq(BUILD_NAME), eq(BUILD_NUMBER), anyString(), eq(NORMALISED_REPO)))
                .thenReturn(buildRun);
        when(readService.getBuildAsJson(buildRun)).thenReturn("{\"version\":\"1.0.1\"}");

        String json = command.getArtifactBuildJson(BUILD_NAME, BUILD_NUMBER, START_TIME, null);

        assertEquals("{\"version\":\"1.0.1\"}", json);
    }

    @Test
    void getArtifactBuildJson_shouldReturnNull_whenBuildNotFound() {
        when(readService.getBuildRun(eq(BUILD_NAME), eq(BUILD_NUMBER), anyString(), eq(NORMALISED_REPO)))
                .thenReturn(null);

        String json = command.getArtifactBuildJson(BUILD_NAME, BUILD_NUMBER, START_TIME, null);

        assertNull(json);
        verify(readService, never()).getBuildAsJson(org.mockito.ArgumentMatchers.any());
    }
}
