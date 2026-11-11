package com.jfrog.build.command.cleanup;

import com.jfrog.build.model.BuildNumberBundleRecord;
import com.jfrog.build.model.BuildNumberRecord;
import com.jfrog.build.model.api.cleanup.BuildBundleEntry;
import com.jfrog.build.model.api.cleanup.BuildNumberEntry;
import com.jfrog.build.model.api.cleanup.BuildNumbersResponse;
import com.jfrog.build.model.api.BuildNumbersModel;
import com.jfrog.build.service.BuildDatabaseService;
import com.jfrog.build.service.BuildProjectService;
import com.jfrog.build.service.BuildRepoService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetNumbersForCleanupCommandTest {

    private BuildGetNumbersForCleanupCommand command;

    private BuildRepoService repoService;
    private BuildProjectService projectService;
    private BuildDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        projectService = mock(BuildProjectService.class);
        databaseService = mock(BuildDatabaseService.class);
        command = new BuildGetNumbersForCleanupCommand(repoService, projectService, databaseService);
    }

    @Test
    void getBuildNumbers_success() {
        long startedBefore = System.currentTimeMillis();
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .limit(1000L)
                .startedBefore(startedBefore)
                .build();
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(databaseService.getBuildNumbers(
                eq("phoenix-build-info"),
                eq("some-build-name"),
                eq(startedBefore),
                eq(false),
                eq(0L),
                eq(1000L),
                eq(20L)))
                .thenReturn(List.of(new BuildNumberRecord(
                        "phoenix-build-info",
                        "some-build-name",
                        "some-build-number",
                        123L,
                        "some-ci-user",
                        List.of())));

        BuildNumbersResponse response = command.getBuildNumbers(numbersModel);

        assertEquals("phoenix-build-info", response.getRepo());
        assertEquals("some-build-name", response.getBuildName());
        assertEquals(1, response.getBuildNumbers().size());
        assertEquals(123L, response.getNextStartedAfter());
    }

    @Test
    void getBuildNumbers_withInvalidLimit_throwsException() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .limit(5001L)
                .startedBefore(System.currentTimeMillis())
                .build();

        assertThrows(BadRequestException.class, () -> command.getBuildNumbers(numbersModel));
    }

    @Test
    void getBuildNumbers_withNonAdminUser_throwsException() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .limit(1000L)
                .startedBefore(System.currentTimeMillis())
                .build();

        doThrow(new ForbiddenException("not admin")).when(projectService).verifyProjectAdmin("phoenix");

        assertThrows(ForbiddenException.class, () -> command.getBuildNumbers(numbersModel));
    }

    @Test
    void getBuildNumbers_throwsBadRequest_whenBuildNameBlank() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("  ")
                .startedBefore(System.currentTimeMillis())
                .build();

        assertThrows(BadRequestException.class, () -> command.getBuildNumbers(numbersModel));
        verify(projectService, never()).verifyProjectAdmin(anyString());
    }

    @Test
    void getBuildNumbers_throwsBadRequest_whenProjectBlank() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("")
                .buildName("some-build-name")
                .startedBefore(System.currentTimeMillis())
                .build();

        assertThrows(BadRequestException.class, () -> command.getBuildNumbers(numbersModel));
        verify(projectService, never()).verifyProjectAdmin(anyString());
    }

    @Test
    void getBuildNumbers_throwsBadRequest_whenStartedBeforeNull() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(null)
                .build();

        assertThrows(BadRequestException.class, () -> command.getBuildNumbers(numbersModel));
        verify(projectService, never()).verifyProjectAdmin(anyString());
    }

    @Test
    void getBuildNumbers_throwsBadRequest_whenLimitLessThanOne() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .limit(0L)
                .startedBefore(System.currentTimeMillis())
                .build();

        assertThrows(BadRequestException.class, () -> command.getBuildNumbers(numbersModel));
        verify(projectService, never()).verifyProjectAdmin(anyString());
    }

    @Test
    void getBuildNumbers_throwsBadRequest_whenBundlesLimitOutOfRange() {
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(System.currentTimeMillis())
                .releaseBundlesLimit(1001L)
                .build();

        assertThrows(BadRequestException.class, () -> command.getBuildNumbers(numbersModel));
        verify(projectService, never()).verifyProjectAdmin(anyString());
    }

    @Test
    void getBuildNumbers_usesDefaultLimitAndBundlesLimit_whenNotProvided() {
        long startedBefore = System.currentTimeMillis();
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(startedBefore)
                .build();
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(databaseService.getBuildNumbers(
                eq("phoenix-build-info"),
                eq("some-build-name"),
                eq(startedBefore),
                eq(false),
                eq(0L),
                eq(1000L),
                eq(20L)))
                .thenReturn(List.of());

        command.getBuildNumbers(numbersModel);

        verify(databaseService).getBuildNumbers(
                "phoenix-build-info",
                "some-build-name",
                startedBefore,
                false,
                0L,
                1000L,
                20L);
    }

    @Test
    void getBuildNumbers_passesStartedAfterAndExcludeInReleaseBundles_toDatabase() {
        long startedBefore = System.currentTimeMillis();
        long startedAfter = 42L;
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(startedBefore)
                .startedAfter(startedAfter)
                .excludeInReleaseBundles(true)
                .limit(50L)
                .releaseBundlesLimit(100L)
                .build();
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(databaseService.getBuildNumbers(
                eq("phoenix-build-info"),
                eq("some-build-name"),
                eq(startedBefore),
                eq(true),
                eq(startedAfter),
                eq(50L),
                eq(100L)))
                .thenReturn(List.of());

        command.getBuildNumbers(numbersModel);

        verify(databaseService).getBuildNumbers(
                "phoenix-build-info",
                "some-build-name",
                startedBefore,
                true,
                startedAfter,
                50L,
                100L);
    }

    @Test
    void getBuildNumbers_nextStartedAfterZero_whenNoBuildNumbers() {
        long startedBefore = System.currentTimeMillis();
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(startedBefore)
                .build();
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(databaseService.getBuildNumbers(
                anyString(), anyString(), anyLong(), anyBoolean(), anyLong(), anyLong(), anyLong()))
                .thenReturn(List.of());

        BuildNumbersResponse response = command.getBuildNumbers(numbersModel);

        assertEquals(0L, response.getNextStartedAfter());
    }

    @Test
    void getBuildNumbers_nextStartedAfterUsesLastEntry_whenMultipleBuildNumbers() {
        long startedBefore = System.currentTimeMillis();
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(startedBefore)
                .build();
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(databaseService.getBuildNumbers(
                anyString(), anyString(), anyLong(), anyBoolean(), anyLong(), anyLong(), anyLong()))
                .thenReturn(List.of(
                                new BuildNumberRecord(
                                        "phoenix-build-info",
                                        "some-build-name",
                                        "1",
                                        100L,
                                        "u1",
                                        List.of()
                                ),
                                new BuildNumberRecord(
                                        "phoenix-build-info",
                                        "some-build-name",
                                        "2",
                                        200L,
                                        "u2",
                                        List.of()
                                )
                        )
                );

        BuildNumbersResponse response = command.getBuildNumbers(numbersModel);

        assertEquals(200L, response.getNextStartedAfter());
    }

    @Test
    void getBuildNumbers_mapsReleaseBundlesThroughProjectService() {
        long startedBefore = System.currentTimeMillis();
        BuildNumbersModel numbersModel = BuildNumbersModel.builder()
                .project("phoenix")
                .buildName("some-build-name")
                .startedBefore(startedBefore)
                .build();
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", false)).thenReturn("phoenix-build-info");
        when(projectService.extractProjectKeyFromReleaseBundleRepoKey("phoenix-release-bundles"))
                .thenReturn("phoenix");
        when(databaseService.getBuildNumbers(
                anyString(), anyString(), anyLong(), anyBoolean(), anyLong(), anyLong(), anyLong()))
                .thenReturn(List.of(new BuildNumberRecord(
                        "phoenix-build-info",
                        "some-build-name",
                        "some-build-number",
                        300L,
                        "some-ci-user",
                        List.of(new BuildNumberBundleRecord(
                                "phoenix-release-bundles",
                                "bundle-name",
                                "1.0.0")))));

        BuildNumbersResponse response = command.getBuildNumbers(numbersModel);

        assertEquals(1, response.getBuildNumbers().size());
        BuildNumberEntry entry = response.getBuildNumbers().getFirst();
        assertEquals(1, entry.getReleaseBundles().size());
        BuildBundleEntry bundle = entry.getReleaseBundles().getFirst();
        assertEquals("phoenix", bundle.getProjectKey());
        assertEquals("bundle-name", bundle.getName());
        assertEquals("1.0.0", bundle.getVersion());
        verify(projectService).extractProjectKeyFromReleaseBundleRepoKey("phoenix-release-bundles");
    }
}
