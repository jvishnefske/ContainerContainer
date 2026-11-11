package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import com.jfrog.build.acl.model.BuildNode;
import com.jfrog.build.acl.model.BuildRepoPath;
import com.jfrog.build.acl.model.BuildWrapper;
import com.jfrog.build.dao.BuildsDao;
import org.artifactory.exception.http.BadRequestException;
import org.jfrog.build.api.Build;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildArtifactServiceTest {

    private BuildArtifactService artifactService;

    private BuildBridgeToArtifactory bridgeToArtifactory;

    private BuildAqlService aqlService;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        BuildsDao buildsDao = mock(BuildsDao.class);
        BuildDatabaseService databaseService = mock(BuildDatabaseService.class);
        BuildPermissionService permissionService = mock(BuildPermissionService.class);
        aqlService = mock(BuildAqlService.class);
        BuildSysConfigService sysConfigService = mock(BuildSysConfigService.class);
        when(bridgeToArtifactory.getRepoKeys(any(), any()))
                .thenReturn(Set.of(BUILDINFO_REPO_KEY, "phoenix-build-info", "prjx-build-info"));
        BuildRepoService repoService = new BuildRepoService(bridgeToArtifactory);
        repoService.refreshAllBuildsRepositories();
        BuildReadService readService = new BuildReadService(
                bridgeToArtifactory,
                buildsDao,
                databaseService,
                permissionService,
                repoService);
        artifactService = new BuildArtifactService(
                bridgeToArtifactory,
                aqlService,
                permissionService,
                readService,
                repoService,
                sysConfigService);
    }

    @Test
    void getFileNodesBySha1_shouldReturnMapFromBridgeNodes() {
        String sha1 = "abc123";
        BuildNode node = BuildNode.builder()
                .repo("repo1")
                .path("path/to/file.jar")
                .sha1Actual(sha1)
                .build();
        when(bridgeToArtifactory.getNodesBySha1(List.of(sha1))).thenReturn(List.of(node));

        var result = artifactService.getFileNodesBySha1(List.of(sha1));

        assertEquals(1, result.size());
        assertEquals(new BuildRepoPath("repo1", "path/to/file.jar"), result.get(sha1));
        verify(bridgeToArtifactory).getNodesBySha1(List.of(sha1));
    }

    @Test
    void getFileNodesBySha1_shouldReturnEmptyMap_whenNoNodes() {
        when(bridgeToArtifactory.getNodesBySha1(any())).thenReturn(List.of());

        var result = artifactService.getFileNodesBySha1(List.of("sha1"));

        assertTrue(result.isEmpty());
    }

    @Test
    void getBuildArtifactsFileInfos_shouldReturnEmptySet_whenBuildIsNull() {
        Build build = null;
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);

        var result = artifactService.getBuildArtifactsFileInfos(wrapper, null, null, false);

        assertTrue(result.isEmpty());
        verify(aqlService, never()).getDistinctBuildArtifactsByAQL(any(), any(), any(), anyBoolean());
    }

    @Test
    void getBuildArtifactsFileInfos_shouldReturnEmptySet_whenBuildModulesAreNull() {
        Build build = new Build();
        build.setName("b");
        build.setNumber("1");
        build.setModules(null);
        BuildWrapper wrapper = new BuildWrapper(build, BUILDINFO_REPO_KEY);

        var result = artifactService.getBuildArtifactsFileInfos(wrapper, null, null, false);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveArchiveEntryFile_shouldResolvePlainRelativePath(@TempDir File archiveTmpDir) throws IOException {
        File resolved = BuildArtifactService.resolveArchiveEntryFile(archiveTmpDir, "some/nested/file.jar");

        assertEquals(new File(archiveTmpDir, "some/nested/file.jar").getCanonicalFile(), resolved);
    }

    @Test
    void resolveArchiveEntryFile_shouldReject_literalDotDotTraversal(@TempDir File archiveTmpDir) {
        // Covers both a literal "../" input and a mapping output where a "$N" placeholder has already been
        // substituted with "/" before this method is called - both arrive here as the same kind of plain
        // string, so there's nothing to distinguish between the two cases at this layer (RTDEV-93954).
        assertThrows(BadRequestException.class, () ->
                BuildArtifactService.resolveArchiveEntryFile(archiveTmpDir, "../../../etc/passwd"));
    }

    @Test
    void resolveArchiveEntryFile_shouldReject_siblingDirectorySharingPrefix(@TempDir File archiveTmpDir) {
        // Regression guard for the classic startsWith() bypass: a sibling dir whose name extends the base dir's
        // name as a string prefix (e.g. "<archiveTmpDir>-evil") must not be treated as "inside" the base dir.
        File sibling = new File(archiveTmpDir.getParentFile(), archiveTmpDir.getName() + "-evil");
        String relativePath = "../" + sibling.getName() + "/cert.crt";

        assertThrows(BadRequestException.class, () ->
                BuildArtifactService.resolveArchiveEntryFile(archiveTmpDir, relativePath));
    }
}
