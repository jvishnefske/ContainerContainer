package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.BuildNumberInfo;
import com.jfrog.build.model.api.BuildNumbersResponse;
import com.jfrog.build.model.api.BuildVersionSearchResults;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildSearchVersionsByNameUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final int DEFAULT_LIMIT = 1000;

    private BuildSearchVersionsByNameUiCommand command;

    private BuildRepoService repoService;
    private BuildSearchService searchService;
    private BuildPermissionService permissionService;
    private BuildSysConfigService sysConfigService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        searchService = mock(BuildSearchService.class);
        permissionService = mock(BuildPermissionService.class);
        sysConfigService = mock(BuildSysConfigService.class);
        command = new BuildSearchVersionsByNameUiCommand(repoService, searchService, permissionService,
                sysConfigService);
        when(repoService.normaliseBuildRepoName(any())).thenReturn(BUILDINFO_REPO_KEY);
        when(sysConfigService.getSearchUserQueryLimit()).thenReturn(DEFAULT_LIMIT);
        when(searchService.getBuildVersions(any(BuildsSearchFilter.class)))
                .thenReturn(BuildVersionSearchResults.builder().builds(List.of()).offset(0).build());
    }

    @Test
    void searchBuildVersionsByName_shouldBuildFilterFromParams() {
        when(repoService.normaliseBuildRepoName("repo")).thenReturn("repo");

        command.searchBuildVersionsByName(BUILD_NAME, "repo", "build_date", "asc", "100", "50", "25", "5");

        verify(repoService).normaliseBuildRepoName("repo");
        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).getBuildVersions(captor.capture());
        BuildsSearchFilter filter = captor.getValue();
        assertEquals(BUILD_NAME, filter.getName());
        assertEquals("repo", filter.getBuildRepo());
        assertEquals(25, filter.getLimit());
        assertEquals(5, filter.getOffset());
        assertEquals(100, filter.getBefore());
        assertEquals(50, filter.getAfter());
        assertEquals("build_date", filter.getOrderBy());
        assertEquals("asc", filter.getSortOrder());
    }

    @Test
    void searchBuildVersionsByName_shouldMapResultsWithCanDeleteFromPermission() {
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(false);
        when(permissionService.canDeleteBuild(any(), any(), any(), any())).thenReturn(true);
        GeneralBuild build = generalBuild();
        when(searchService.getBuildVersions(any(BuildsSearchFilter.class)))
                .thenReturn(BuildVersionSearchResults.builder().builds(List.of(build)).offset(4).build());

        BuildNumbersResponse response = command.searchBuildVersionsByName(BUILD_NAME, null, null, null, null, null,
                null, null);

        assertEquals(4, response.getOffset());
        assertEquals(1, response.getVersions().size());
        BuildNumberInfo info = response.getVersions().getFirst();
        assertEquals(BUILDINFO_REPO_KEY, info.getBuildRepo());
        assertEquals("7", info.getBuildNumber());
        assertEquals("http://ci/7", info.getCiUrl());
        assertEquals("Released", info.getBuildStat());
        assertEquals(1700000000000L, info.getTime());
        assertTrue(info.isCanDelete());
    }

    @Test
    void searchBuildVersionsByName_shouldSkipPermissionCheck_whenConfigured() {
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(true);
        GeneralBuild build = generalBuild();
        when(searchService.getBuildVersions(any(BuildsSearchFilter.class)))
                .thenReturn(BuildVersionSearchResults.builder().builds(List.of(build)).offset(0).build());

        BuildNumbersResponse response = command.searchBuildVersionsByName(BUILD_NAME, null, null, null, null, null,
                null, null);

        assertTrue(response.getVersions().getFirst().isCanDelete());
        verify(permissionService, never()).canDeleteBuild(any(), any(), any(), any());
    }

    @Test
    void searchBuildVersionsByName_shouldDefaultLimitToConfiguredLimit_whenBlank() {
        command.searchBuildVersionsByName(BUILD_NAME, null, null, null, null, null, null, null);

        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).getBuildVersions(captor.capture());
        assertEquals(DEFAULT_LIMIT, captor.getValue().getLimit());
    }

    @Test
    void searchBuildVersionsByName_shouldCapLimitToConfiguredLimit_whenLarger() {
        command.searchBuildVersionsByName(BUILD_NAME, null, null, null, null, null, "5000", null);

        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).getBuildVersions(captor.capture());
        assertEquals(DEFAULT_LIMIT, captor.getValue().getLimit());
    }

    @Test
    void searchBuildVersionsByName_shouldThrowBadRequest_whenNumOfRowsNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.searchBuildVersionsByName(BUILD_NAME, null, null, null, null, null, "abc", null));

        assertTrue(thrown.getMessage().contains("num_of_rows"));
    }

    @Test
    void searchBuildVersionsByName_shouldThrowBadRequest_whenOffsetNegative() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.searchBuildVersionsByName(BUILD_NAME, null, null, null, null, null, null, "-1"));

        assertTrue(thrown.getMessage().contains("offset"));
    }

    private static GeneralBuild generalBuild() {
        GeneralBuild build = mock(GeneralBuild.class);
        when(build.getBuildName()).thenReturn("my-build");
        when(build.getBuildNumber()).thenReturn("7");
        when(build.getBuildRepo()).thenReturn(BUILDINFO_REPO_KEY);
        when(build.getCiUrl()).thenReturn("http://ci/7");
        when(build.getStatus()).thenReturn("Released");
        when(build.getBuildDate()).thenReturn(1700000000000L);
        return build;
    }
}
