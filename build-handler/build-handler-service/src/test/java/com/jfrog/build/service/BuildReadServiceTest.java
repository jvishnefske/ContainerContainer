package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildRun;
import com.jfrog.build.acl.model.BuildRunImpl;
import com.jfrog.build.dao.BuildsDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildReadServiceTest {

    private BuildReadService readService;

    private BuildDatabaseService databaseService;
    private BuildPermissionService permissionService;

    @BeforeEach
    void setUp() {
        BuildBridgeToArtifactory bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        BuildsDao buildsDao = mock(BuildsDao.class);
        databaseService = mock(BuildDatabaseService.class);
        permissionService = mock(BuildPermissionService.class);
        when(bridgeToArtifactory.getRepoKeys(any(), any()))
                .thenReturn(Set.of(BUILDINFO_REPO_KEY, "phoenix-build-info", "prjx-build-info"));
        BuildRepoService repoService = new BuildRepoService(bridgeToArtifactory);
        repoService.refreshAllBuildsRepositories();
        readService = new BuildReadService(
                bridgeToArtifactory,
                buildsDao,
                databaseService,
                permissionService,
                repoService);
    }

    @Test
    void getBuildRunInternally_shouldDelegateToStoreService() {
        BuildRun buildRun = new BuildRunImpl("b", "1", "123", BUILDINFO_REPO_KEY);
        when(databaseService.getBuildRun("b", "1", "123")).thenReturn(buildRun);

        BuildRun result = readService.getBuildRunInternally("b", "1", "123");

        assertEquals(buildRun, result);
        verify(databaseService).getBuildRun("b", "1", "123");
    }

    @Test
    void getBuildRun_shouldAssertBasicReadPermissionsAndReturnRun_whenRunExists() {
        BuildRun buildRun = new BuildRunImpl("b", "1", "123", BUILDINFO_REPO_KEY);
        when(databaseService.getBuildRun("b", "1", "123")).thenReturn(buildRun);
        doNothing().when(permissionService).assertBasicReadPermissions(any(), any(), any(), any());

        BuildRun result = readService.getBuildRun("b", "1", "123", BUILDINFO_REPO_KEY);

        assertEquals(buildRun, result);
        verify(permissionService).assertBasicReadPermissions(eq("b"), eq("1"), eq("123"), eq(BUILDINFO_REPO_KEY));
    }

    @Test
    void getBuildRun_shouldAssertBasicReadPermissionsOnRequestedRepo_whenRunNull() {
        when(databaseService.getBuildRun("b", "1", "123")).thenReturn(null);
        doNothing().when(permissionService).assertBasicReadPermissions(any(), any(), any(), any());

        BuildRun result = readService.getBuildRun("b", "1", "123", "phoenix-build-info");

        assertNull(result);
        verify(permissionService).assertBasicReadPermissions(eq("b"), eq("1"), eq("123"), eq("phoenix-build-info"));
    }

    @Test
    void searchBuildsByNameAndNumbersInternal_shouldDelegateToDatabaseService() {
        BuildRun run = new BuildRunImpl("b", "1", "123", BUILDINFO_REPO_KEY);
        when(databaseService.findBuildsByNameAndNumbers("b", List.of("1"), BUILDINFO_REPO_KEY))
                .thenReturn(Set.of(run));

        Set<BuildRun> result = readService.searchBuildsByNameAndNumbersInternal("b", List.of("1"), BUILDINFO_REPO_KEY);

        assertEquals(Set.of(run), result);
        verify(databaseService).findBuildsByNameAndNumbers("b", List.of("1"), BUILDINFO_REPO_KEY);
    }

    @Test
    void buildNameExists_shouldDelegateToDatabaseService() {
        when(databaseService.buildNameExists("b", BUILDINFO_REPO_KEY)).thenReturn(true);
        assertTrue(readService.buildNameExists("b", BUILDINFO_REPO_KEY));

        when(databaseService.buildNameExists("missing", BUILDINFO_REPO_KEY)).thenReturn(false);
        assertFalse(readService.buildNameExists("missing", BUILDINFO_REPO_KEY));

        verify(databaseService).buildNameExists("b", BUILDINFO_REPO_KEY);
        verify(databaseService).buildNameExists("missing", BUILDINFO_REPO_KEY);
    }
}
