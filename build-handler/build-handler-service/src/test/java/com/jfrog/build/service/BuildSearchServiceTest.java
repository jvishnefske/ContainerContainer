package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildsSearchFilter;
import com.jfrog.build.acl.model.GeneralBuild;
import com.jfrog.build.model.api.BuildVersionSearchResults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildSearchServiceTest {

    private BuildBridgeToArtifactory bridgeToArtifactory;
    private BuildDatabaseService databaseService;
    private BuildSearchService searchService;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        BuildPermissionService permissionService = mock(BuildPermissionService.class);
        databaseService = mock(BuildDatabaseService.class);
        BuildSysConfigService sysConfigService = mock(BuildSysConfigService.class);
        when(sysConfigService.getBuildSearchMaxBatchSize()).thenReturn(100);
        searchService = new BuildSearchService(
                bridgeToArtifactory, permissionService, databaseService, sysConfigService);
    }

    @Test
    void getBuildVersions_populatesLatestPromotionStatuses() {
        GeneralBuild first = GeneralBuild.builder().buildId(1L).buildName("a").buildNumber("1").buildDate(10L).build();
        GeneralBuild second = GeneralBuild.builder().buildId(2L).buildName("a").buildNumber("2").buildDate(20L).build();
        when(bridgeToArtifactory.isAdmin()).thenReturn(true);
        when(databaseService.getBuildNumbersByBuildName(any())).thenReturn(List.of(first, second));
        // only the first build has a promotion status
        when(databaseService.getLatestPromotionStatusForBuildIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, "Released"));

        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name("a").buildRepo("r").limit(2).offset(0).build();
        BuildVersionSearchResults results = searchService.getBuildVersions(filter);

        assertEquals(2, results.getBuilds().size());
        assertEquals("Released", first.getStatus());
        assertNull(second.getStatus());
        verify(databaseService).getLatestPromotionStatusForBuildIds(List.of(1L, 2L));
    }

    @Test
    void getBuildVersions_whenNoBuilds_returnsEmptyAndQueriesNoStatuses() {
        when(databaseService.getBuildNumbersByBuildName(any())).thenReturn(List.of());
        when(databaseService.getLatestPromotionStatusForBuildIds(List.of())).thenReturn(Map.of());

        BuildsSearchFilter filter = BuildsSearchFilter.builder()
                .name("a").buildRepo("r").limit(10).offset(0).build();
        BuildVersionSearchResults results = searchService.getBuildVersions(filter);

        assertTrue(results.getBuilds().isEmpty());
        verify(databaseService).getLatestPromotionStatusForBuildIds(List.of());
    }
}
