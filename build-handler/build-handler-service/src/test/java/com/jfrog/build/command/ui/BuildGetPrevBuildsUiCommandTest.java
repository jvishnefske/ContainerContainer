package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildModuleService;
import com.jfrog.build.service.BuildRepoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetPrevBuildsUiCommandTest {

    private static final String BUILD_DATE = "2024-01-01T00:00:00.000+0000";

    private BuildGetPrevBuildsUiCommand command;

    private BuildRepoService repoService;
    private BuildModuleService moduleService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        moduleService = mock(BuildModuleService.class);
        command = new BuildGetPrevBuildsUiCommand(repoService, moduleService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
    }

    @Test
    void getPrevBuilds_shouldMapBuildsToGeneralBuildInfo() {
        when(moduleService.getPrevBuildsList(eq("my-build"), eq(BUILD_DATE), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(List.of(
                        generalBuild("my-build", "1", 1000L, "Released"),
                        generalBuild("my-build", "2", 2000L, "Staged")));

        List<GeneralBuildInfo> result = command.getPrevBuilds("my-build", BUILD_DATE, BUILDINFO_REPO_KEY);

        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
        assertEquals(2, result.size());
        GeneralBuildInfo first = result.getFirst();
        assertEquals("my-build", first.getBuildName());
        assertEquals("1", first.getBuildNumber());
        assertEquals("Released", first.getBuildStat());
        assertEquals(1000L, first.getTime());
    }

    @Test
    void getPrevBuilds_shouldReturnEmptyList_whenNoPreviousBuilds() {
        when(moduleService.getPrevBuildsList(eq("my-build"), eq(BUILD_DATE), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(List.of());

        List<GeneralBuildInfo> result = command.getPrevBuilds("my-build", BUILD_DATE, BUILDINFO_REPO_KEY);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPrevBuilds_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(moduleService.getPrevBuildsList(eq("my-build"), eq(BUILD_DATE), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(List.of());

        command.getPrevBuilds("my-build", BUILD_DATE, null);

        verify(repoService).normaliseBuildRepoName(null);
        verify(moduleService).getPrevBuildsList("my-build", BUILD_DATE, BUILDINFO_REPO_KEY);
    }

    private static GeneralBuild generalBuild(String name, String number, long date, String status) {
        return new GeneralBuild(name, number, date, null, status, BUILDINFO_REPO_KEY);
    }
}
