package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetHistoryUiCommandTest {

    private BuildGetHistoryUiCommand command;

    private BuildRepoService repoService;
    private BuildSearchService searchService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        searchService = mock(BuildSearchService.class);
        BuildSysConfigService sysConfigService = mock(BuildSysConfigService.class);
        command = new BuildGetHistoryUiCommand(repoService, searchService, sysConfigService);
        when(repoService.normaliseBuildRepoName(BUILDINFO_REPO_KEY)).thenReturn(BUILDINFO_REPO_KEY);
        when(sysConfigService.getDateTimeFormat()).thenReturn("dd-MM-yy HH:mm:ss z");
    }

    @Test
    void getBuildHistory_shouldMapBuildsToGeneralBuildInfoAndSetContinueState_whenPageIsFull() {
        Map<String, String[]> params = Map.of("limit", new String[] {"2"});
        when(searchService.getBuildForName(eq("my-build"), eq(BUILDINFO_REPO_KEY), any(ContinueBuildFilter.class)))
                .thenReturn(List.of(
                        generalBuild("1", 1000L, "Released", "http://ci/1", "2", "3", "4"),
                        generalBuild("2", 2000L, "Staged", "http://ci/2", "1", "1", "1")));

        ContinueResult<GeneralBuildInfo> result = command.getBuildHistory("my-build", BUILDINFO_REPO_KEY, params);

        verify(repoService).normaliseBuildRepoName(BUILDINFO_REPO_KEY);
        assertEquals(2, result.getData().size());
        GeneralBuildInfo first = result.getData().getFirst();
        assertEquals("1", first.getBuildNumber());
        assertEquals("Released", first.getReleaseStatus());
        assertEquals("http://ci/1", first.getCiUrl());
        assertEquals(1000L, first.getTime());
        assertEquals("Modules-2, Artifacts-3, Dependencies-4", first.getBuildStat());
        assertNotNull(first.getLastBuildTime());
        // A full page (size == limit) yields a continuation token for the next page.
        assertNotNull(result.getContinueState());
    }

    @Test
    void getBuildHistory_shouldNotSetContinueState_whenPageNotFull() {
        Map<String, String[]> params = Map.of("limit", new String[] {"10"});
        when(searchService.getBuildForName(eq("my-build"), eq(BUILDINFO_REPO_KEY), any(ContinueBuildFilter.class)))
                .thenReturn(List.of(generalBuild("1", 1000L, "Released", "http://ci/1", "2", "3", "4")));

        ContinueResult<GeneralBuildInfo> result = command.getBuildHistory("my-build", BUILDINFO_REPO_KEY, params);

        assertEquals(1, result.getData().size());
        assertNull(result.getContinueState());
    }

    @Test
    void getBuildHistory_shouldReturnEmptyResult_whenNoBuildsFound() {
        when(searchService.getBuildForName(eq("missing"), eq(BUILDINFO_REPO_KEY), any(ContinueBuildFilter.class)))
                .thenReturn(List.of());

        ContinueResult<GeneralBuildInfo> result = command.getBuildHistory("missing", BUILDINFO_REPO_KEY, Map.of());

        assertTrue(result.getData().isEmpty());
        assertNull(result.getContinueState());
    }

    @Test
    void getBuildHistory_shouldNormaliseNullRepoBeforeQuerying() {
        when(repoService.normaliseBuildRepoName(null)).thenReturn(BUILDINFO_REPO_KEY);
        when(searchService.getBuildForName(eq("my-build"), eq(BUILDINFO_REPO_KEY), any(ContinueBuildFilter.class)))
                .thenReturn(List.of());

        command.getBuildHistory("my-build", null, Map.of());

        verify(repoService).normaliseBuildRepoName(null);
        verify(searchService).getBuildForName(eq("my-build"), eq(BUILDINFO_REPO_KEY), any(ContinueBuildFilter.class));
    }

    private static GeneralBuild generalBuild(
            String number, long date, String status, String ciUrl,
            String modules, String artifacts, String dependencies) {
        GeneralBuild build = new GeneralBuild("my-build", number, date, ciUrl, status, BUILDINFO_REPO_KEY);
        build.setNumOfModules(modules);
        build.setNumOfArtifacts(artifacts);
        build.setNumOfDependencies(dependencies);
        return build;
    }
}
