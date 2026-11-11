package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildId;
import com.jfrog.build.model.BuildIdImpl;
import com.jfrog.build.model.api.ContinueBuildFilter;
import com.jfrog.build.model.api.GeneralBuildInfo;
import com.jfrog.build.service.BuildPermissionService;
import com.jfrog.build.service.BuildRepoService;
import com.jfrog.build.service.BuildSearchService;
import com.jfrog.build.service.BuildSysConfigService;
import org.jfrog.commons.continues.ContinueResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetAllUiCommandTest {

    private static final long BUILD_TIME = 1704067200000L;

    private BuildGetAllUiCommand command;

    private BuildRepoService repoService;
    private BuildSearchService searchService;
    private BuildSysConfigService sysConfigService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        repoService = mock(BuildRepoService.class);
        searchService = mock(BuildSearchService.class);
        sysConfigService = mock(BuildSysConfigService.class);
        permissionService = mock(BuildPermissionService.class);
        command = new BuildGetAllUiCommand(repoService, searchService, sysConfigService, permissionService);
        when(sysConfigService.getDateTimeFormat()).thenReturn("dd-MM-yy HH:mm:ss z");
    }

    @Test
    void getAllBuilds_shouldMapBuildIdsToGeneralBuildInfo() {
        BuildId buildId = buildId("some-build-name", "1", BUILD_TIME);
        when(searchService.getLatestBuildIDsPaging(any(ContinueBuildFilter.class))).thenReturn(List.of(buildId));
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(false);
        when(permissionService.canDeleteBuild(
                eq("some-build-name"), eq("1"), eq(buildId.getStarted()), eq(BUILDINFO_REPO_KEY)))
                .thenReturn(true);

        ContinueResult<GeneralBuildInfo> result = command.getAllBuilds(Map.of("limit", new String[] {"10"}));

        assertEquals(1, result.getData().size());
        GeneralBuildInfo buildInfo = result.getData().getFirst();
        assertEquals("some-build-name", buildInfo.getBuildName());
        assertEquals("1", buildInfo.getBuildNumber());
        assertEquals(BUILD_TIME, buildInfo.getTime());
        assertEquals(BUILDINFO_REPO_KEY, buildInfo.getBuildRepo());
        assertTrue(buildInfo.getCanDelete());
        assertNotNull(buildInfo.getLastBuildTime());
    }

    @Test
    void getAllBuilds_shouldAllowDelete_whenSkipDeletePermissionCheckEnabled() {
        BuildId buildId = buildId("some-build-name", "1", BUILD_TIME);
        when(searchService.getLatestBuildIDsPaging(any(ContinueBuildFilter.class))).thenReturn(List.of(buildId));
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(true);

        ContinueResult<GeneralBuildInfo> result = command.getAllBuilds(Map.of());

        assertTrue(result.getData().getFirst().getCanDelete());
    }

    @Test
    void getAllBuilds_shouldDenyDelete_whenPermissionCheckFailsAndSkipDisabled() {
        BuildId buildId = buildId("some-build-name", "1", BUILD_TIME);
        when(searchService.getLatestBuildIDsPaging(any(ContinueBuildFilter.class))).thenReturn(List.of(buildId));
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(false);
        when(permissionService.canDeleteBuild(any(), any(), any(), any())).thenReturn(false);

        ContinueResult<GeneralBuildInfo> result = command.getAllBuilds(Map.of());

        assertFalse(result.getData().getFirst().getCanDelete());
    }

    @Test
    void getAllBuilds_shouldSetNextContinueState_whenResultSizeEqualsLimit() {
        BuildId firstBuild = buildId("build-a", "1", BUILD_TIME);
        BuildId secondBuild = buildId("build-b", "2", BUILD_TIME + 1);
        when(searchService.getLatestBuildIDsPaging(any(ContinueBuildFilter.class)))
                .thenReturn(List.of(firstBuild, secondBuild));
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(true);

        ContinueResult<GeneralBuildInfo> result = command.getAllBuilds(Map.of("limit", new String[] {"2"}));

        assertNotNull(result.getContinueState());
        assertEquals(2, result.getData().size());
    }

    @Test
    void getAllBuilds_shouldNotSetNextContinueState_whenResultSizeLessThanLimit() {
        BuildId buildId = buildId("some-build-name", "1", BUILD_TIME);
        when(searchService.getLatestBuildIDsPaging(any(ContinueBuildFilter.class))).thenReturn(List.of(buildId));
        when(sysConfigService.isBuildUiSkipDeletePermissionCheck()).thenReturn(true);

        ContinueResult<GeneralBuildInfo> result = command.getAllBuilds(Map.of("limit", new String[] {"5"}));

        assertNull(result.getContinueState());
    }

    @Test
    void getAllBuilds_shouldResolveProjectKeyThroughRepoService() {
        when(repoService.getBuildInfoRepoKeyByProject("phoenix", true)).thenReturn("phoenix-build-info");
        when(searchService.getLatestBuildIDsPaging(any(ContinueBuildFilter.class))).thenReturn(List.of());
        ArgumentCaptor<ContinueBuildFilter> filterCaptor = ArgumentCaptor.forClass(ContinueBuildFilter.class);

        command.getAllBuilds(Map.of("projectKey", new String[] {"phoenix"}));

        verify(searchService).getLatestBuildIDsPaging(filterCaptor.capture());
        assertEquals("phoenix-build-info", filterCaptor.getValue().getBuildRepo());
        verify(repoService).getBuildInfoRepoKeyByProject("phoenix", true);
    }

    private static BuildId buildId(String name, String number, long started) {
        return new BuildIdImpl(1L, name, number, started, BUILDINFO_REPO_KEY);
    }
}
