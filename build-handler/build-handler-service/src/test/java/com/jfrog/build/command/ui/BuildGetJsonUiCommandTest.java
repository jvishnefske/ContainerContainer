package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.model.api.ViewArtifactModel;
import com.jfrog.build.service.BuildReadService;
import com.jfrog.build.service.BuildRepoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetJsonUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final String BUILD_NUMBER = "1";
    private static final String BUILD_STARTED_EPOCH = "1700000000000";
    private static final String BUILD_STARTED_FORMATTED = formatBuildTime(Long.parseLong(BUILD_STARTED_EPOCH));

    private BuildGetJsonUiCommand command;

    private BuildRepoService repoService;
    private BuildReadService readService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        readService = mock(BuildReadService.class);
        command = new BuildGetJsonUiCommand(repoService, readService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getBuildJson_shouldFormatDateResolveRunAndReturnJsonContent() {
        BuildRun buildRun = mock(BuildRun.class);
        when(readService.getBuildRun(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY))
                .thenReturn(buildRun);
        when(readService.getBuildAsJson(buildRun)).thenReturn("{\"version\":\"1\"}");

        ViewArtifactModel result = command.getBuildJson(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH,
                BUILDINFO_REPO_KEY);

        verify(readService).getBuildRun(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY);
        assertEquals("{\"version\":\"1\"}", result.getFileContent());
    }

    @Test
    void getBuildJson_shouldThrowNotFound_whenBuildRunMissing() {
        when(readService.getBuildRun(eq(BUILD_NAME), eq(BUILD_NUMBER), eq(BUILD_STARTED_FORMATTED),
                eq(BUILDINFO_REPO_KEY))).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> command.getBuildJson(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, BUILDINFO_REPO_KEY));
    }

    @Test
    void getBuildJson_shouldNormaliseNullRepoBeforeResolving() {
        BuildRun buildRun = mock(BuildRun.class);
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(readService.getBuildRun(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY))
                .thenReturn(buildRun);
        when(readService.getBuildAsJson(buildRun)).thenReturn("{}");

        command.getBuildJson(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_EPOCH, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(readService).getBuildRun(BUILD_NAME, BUILD_NUMBER, BUILD_STARTED_FORMATTED, BUILDINFO_REPO_KEY);
    }
}
