package com.jfrog.build.command.cleanup;

import com.jfrog.build.model.BuildDeleteNumberResult;
import com.jfrog.build.service.BuildDeletionService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.exception.http.InternalServerException;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jfrog.build.model.BuildDeleteNumberResult.Status.FAILURE_ARTIFACTS;
import static com.jfrog.build.model.BuildDeleteNumberResult.Status.FAILURE_BUILD_INFO;
import static com.jfrog.build.model.BuildDeleteNumberResult.Status.SUCCESS;
import static com.jfrog.build.acl.util.BuildInfoUtils.formatBuildTime;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildDeleteForCleanupCommandTest {

    private BuildDeleteForCleanupCommand command;

    private BuildRepoService repoService;
    private BuildProjectService projectService;
    private BuildDeletionService deletionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        projectService = mock(BuildProjectService.class);
        deletionService = mock(BuildDeletionService.class);
        command = new BuildDeleteForCleanupCommand(repoService, projectService, deletionService);
    }

    @Test
    void deleteBuild_success() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        String projectKey = "phoenix";

        when(repoService.getBuildInfoRepoKeyByProject(projectKey, false)).thenReturn("phoenix-build-info");
        BuildDeleteNumberResult expected = new BuildDeleteNumberResult(SUCCESS, 2, 2, 100);
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq(buildName),
                eq(buildNumber),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(expected);

        BuildDeleteNumberResult result =
                command.deleteBuild(buildName, buildNumber, startedRaw, projectKey, false, true, false);

        assertEquals(expected, result);
        verify(deletionService).deleteBuild(
                "phoenix-build-info",
                buildName,
                buildNumber,
                startedFormatted,
                false,
                true,
                false);
    }

    @Test
    void deleteBuild_partialArtifactsDeletes() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        String projectKey = "phoenix";

        when(repoService.getBuildInfoRepoKeyByProject(projectKey, false)).thenReturn("phoenix-build-info");
        BuildDeleteNumberResult expected = new BuildDeleteNumberResult(FAILURE_ARTIFACTS, 2, 1, 50);
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq(buildName),
                eq(buildNumber),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(expected);

        BuildDeleteNumberResult result =
                command.deleteBuild(buildName, buildNumber, startedRaw, projectKey, false, true, false);

        assertEquals(expected, result);
    }

    @Test
    void deleteBuild_allArtifactsDeletesBuildInfoFailed() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        String projectKey = "phoenix";

        when(repoService.getBuildInfoRepoKeyByProject(projectKey, false)).thenReturn("phoenix-build-info");
        BuildDeleteNumberResult expected = new BuildDeleteNumberResult(FAILURE_BUILD_INFO, 2, 2, 100);
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq(buildName),
                eq(buildNumber),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(expected);

        BuildDeleteNumberResult result =
                command.deleteBuild(buildName, buildNumber, startedRaw, projectKey, false, true, false);

        assertEquals(expected, result);
    }

    @Test
    void deleteBuild_failedAllArtifactsDeletes() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        String projectKey = "phoenix";

        when(repoService.getBuildInfoRepoKeyByProject(projectKey, false)).thenReturn("phoenix-build-info");
        BuildDeleteNumberResult expected = new BuildDeleteNumberResult(FAILURE_ARTIFACTS, 2, 0, 0);
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq(buildName),
                eq(buildNumber),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(expected);

        assertThrows(InternalServerException.class,
                () -> command.deleteBuild(buildName, buildNumber, startedRaw, projectKey, false, true, false));
    }

    @Test
    void deleteBuild_failedBuildInfoDelete() {
        String buildName = "some-build-name";
        String buildNumber = "some-build-number";
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        String projectKey = "phoenix";

        when(repoService.getBuildInfoRepoKeyByProject(projectKey, false)).thenReturn("phoenix-build-info");
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq(buildName),
                eq(buildNumber),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenReturn(BuildDeleteNumberResult.FAILED_BUILD_INFO);

        assertThrows(InternalServerException.class,
                () -> command.deleteBuild(buildName, buildNumber, startedRaw, projectKey, false, true, false));
    }

    @Test
    void deleteBuild_deletionServiceThrowsRuntimeException() {
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq("some-build-name"),
                eq("some-build-number"),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenThrow(new IllegalStateException("simulated deletion failure"));

        assertThrows(IllegalStateException.class,
                () -> command.deleteBuild(
                        "some-build-name",
                        "some-build-number",
                        startedRaw,
                        "phoenix",
                        false,
                        true,
                        false));
    }

    @Test
    void deleteBuild_deletionServiceThrowsNotFoundException() {
        String startedRaw = Long.toString(System.currentTimeMillis());
        String startedFormatted = formatBuildTime(startedRaw);
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(deletionService.deleteBuild(
                eq("phoenix-build-info"),
                eq("some-build-name"),
                eq("some-build-number"),
                eq(startedFormatted),
                eq(false),
                eq(true),
                eq(false)))
                .thenThrow(new NotFoundException("build not found"));

        assertThrows(NotFoundException.class,
                () -> command.deleteBuild(
                        "some-build-name",
                        "some-build-number",
                        startedRaw,
                        "phoenix",
                        false,
                        true,
                        false));
    }

    @Test
    void deleteBuild_withNoProject_throwsException() {
        assertThrows(BadRequestException.class,
                () -> command.deleteBuild(
                        "some-build-name",
                        "some-build-number",
                        Long.toString(System.currentTimeMillis()),
                        null,
                        false,
                        true,
                        false));
    }

    @Test
    void deleteBuild_withNoStarted_throwsException() {
        assertThrows(BadRequestException.class,
                () -> command.deleteBuild("some-build-name", "some-build-number", null, "phoenix", false, true, false));
    }

    @Test
    void deleteBuild_withInvalidStarted_throwsException() {
        assertThrows(BadRequestException.class,
                () -> command.deleteBuild(
                        "some-build-name",
                        "some-build-number",
                        "not-a-valid-timestamp",
                        "phoenix",
                        false,
                        true,
                        false));
    }

    @Test
    void deleteBuild_withNonAdminUser_throwsException() {
        String startedRaw = Long.toString(System.currentTimeMillis());
        doThrow(new ForbiddenException("not a project admin")).when(projectService).verifyProjectAdmin("phoenix");

        assertThrows(ForbiddenException.class,
                () -> command.deleteBuild(
                        "some-build-name",
                        "some-build-number",
                        startedRaw,
                        "phoenix",
                        false,
                        true,
                        false));
    }
}
