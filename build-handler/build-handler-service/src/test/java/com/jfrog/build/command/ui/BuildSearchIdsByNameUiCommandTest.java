package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.model.api.BuildIdInfo;
import com.jfrog.build.model.api.BuildIdResponse;
import com.jfrog.build.model.api.BuildIdSearchResults;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import org.artifactory.exception.http.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
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

class BuildSearchIdsByNameUiCommandTest {

    private static final String BUILD_NAME = "my-build";
    private static final long DEFAULT_LIMIT = 1000;

    private BuildSearchIdsByNameUiCommand command;

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
        command = new BuildSearchIdsByNameUiCommand(repoService, searchService, permissionService, sysConfigService);
        when(sysConfigService.getSearchUserQueryLimit()).thenReturn((int) DEFAULT_LIMIT);
        when(searchService.findBuildIDsByNameCaseInsensitive(any(BuildsSearchFilter.class)))
                .thenReturn(BuildIdSearchResults.builder().builds(List.of()).offset(0).build());
    }

    @Test
    void searchBuildIdsByName_shouldBuildFilterFromParams() {
        when(repoService.getBuildRepoForSearch("repo", "proj")).thenReturn(BUILDINFO_REPO_KEY);

        command.searchBuildIdsByName(BUILD_NAME, "repo", "proj", "build_date", "asc", "100", "50", "25", "5");

        verify(repoService).getBuildRepoForSearch("repo", "proj");
        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).findBuildIDsByNameCaseInsensitive(captor.capture());
        BuildsSearchFilter filter = captor.getValue();
        assertEquals(BUILD_NAME, filter.getName());
        assertEquals(BUILDINFO_REPO_KEY, filter.getBuildRepo());
        assertEquals("proj", filter.getProjectKey());
        assertEquals(100, filter.getBefore());
        assertEquals(50, filter.getAfter());
        assertEquals(25, filter.getLimit());
        assertEquals(5, filter.getOffset());
        assertEquals("build_date", filter.getOrderBy());
        assertEquals("asc", filter.getSortOrder());
    }

    @Test
    void searchBuildIdsByName_shouldMapResultsWithCanDeleteFromPermission() {
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(false);
        when(permissionService.canDeleteBuild("my-build", "7", "2024-01-01T00:00:00.000Z", BUILDINFO_REPO_KEY))
                .thenReturn(true);
        BuildId build = buildId("my-build", "7", "2024-01-01T00:00:00.000Z", BUILDINFO_REPO_KEY, new Date(1700000000000L));
        when(searchService.findBuildIDsByNameCaseInsensitive(any(BuildsSearchFilter.class)))
                .thenReturn(BuildIdSearchResults.builder().builds(List.of(build)).offset(3).build());

        BuildIdResponse response = command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, null,
                null);

        assertEquals(3, response.getOffset());
        assertEquals(1, response.getBuilds().size());
        BuildIdInfo info = response.getBuilds().getFirst();
        assertEquals("my-build", info.getBuildName());
        assertEquals("7", info.getLastBuildNumber());
        assertEquals(BUILDINFO_REPO_KEY, info.getBuildRepo());
        assertEquals(1700000000000L, info.getTime());
        assertTrue(info.isCanDelete());
        assertTrue(info.getReleaseBundles().isEmpty());
    }

    @Test
    void searchBuildIdsByName_shouldSkipPermissionCheck_whenConfigured() {
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(true);
        BuildId build = buildId("my-build", "7", "2024-01-01T00:00:00.000Z", BUILDINFO_REPO_KEY, new Date(0));
        when(searchService.findBuildIDsByNameCaseInsensitive(any(BuildsSearchFilter.class)))
                .thenReturn(BuildIdSearchResults.builder().builds(List.of(build)).offset(0).build());

        BuildIdResponse response = command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, null,
                null);

        assertTrue(response.getBuilds().getFirst().isCanDelete());
        verify(permissionService, never()).canDeleteBuild(any(), any(), any(), any());
    }

    @Test
    void searchBuildIdsByName_shouldCapLimitToConfiguredDefault_whenNoLimitGiven() {
        command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, null, null);

        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).findBuildIDsByNameCaseInsensitive(captor.capture());
        assertEquals(DEFAULT_LIMIT, captor.getValue().getLimit());
    }

    @Test
    void searchBuildIdsByName_shouldClampNegativeLimitToDefault() {
        command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, "-5", null);

        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).findBuildIDsByNameCaseInsensitive(captor.capture());
        assertEquals(DEFAULT_LIMIT, captor.getValue().getLimit());
    }

    @Test
    void searchBuildIdsByName_shouldDefaultOffsetToZero_whenBlank() {
        command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, null, null);

        ArgumentCaptor<BuildsSearchFilter> captor = ArgumentCaptor.forClass(BuildsSearchFilter.class);
        verify(searchService).findBuildIDsByNameCaseInsensitive(captor.capture());
        assertEquals(0, captor.getValue().getOffset());
    }

    @Test
    void searchBuildIdsByName_shouldThrowBadRequest_whenNumOfRowsNotANumber() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, "abc", null));

        assertTrue(thrown.getMessage().contains("num_of_rows"));
    }

    @Test
    void searchBuildIdsByName_shouldThrowBadRequest_whenOffsetNegative() {
        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, null, "-1"));

        assertTrue(thrown.getMessage().contains("offset"));
    }

    @Test
    void searchBuildIdsByName_shouldThrowBadRequest_whenOffsetNotANumber() {
        assertThrows(BadRequestException.class,
                () -> command.searchBuildIdsByName(BUILD_NAME, null, null, null, null, null, null, null, "xyz"));
    }

    private static BuildId buildId(String name, String number, String started, String buildRepo, Date startedDate) {
        BuildId build = mock(BuildId.class);
        when(build.getName()).thenReturn(name);
        when(build.getNumber()).thenReturn(number);
        when(build.getStarted()).thenReturn(started);
        when(build.getBuildRepo()).thenReturn(buildRepo);
        when(build.getStartedDate()).thenReturn(startedDate);
        return build;
    }
}
