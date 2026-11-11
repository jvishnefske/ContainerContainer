package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectSlimEntity;
import org.jfrog.access.common.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Set;

import static com.jfrog.build.acl.util.BuildConstants.BUILDINFO_REPO_KEY;
import static org.jfrog.access.common.ProjectConstants.DEFAULT_PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildRepoServiceTest {

    private BuildRepoService repoService;

    private BuildBridgeToArtifactory bridgeToArtifactory;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        when(bridgeToArtifactory.getRepoKeys(any(), any()))
                .thenReturn(Set.of(BUILDINFO_REPO_KEY, "phoenix-build-info", "prjx-build-info"));
        when(bridgeToArtifactory.getProjectSpec("phoenix")).thenReturn(
                Optional.of(ProjectSlimEntity.builder().projectKey("phoenix").name("Phoenix").build()));
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(bridgeToArtifactory).doAsSystem(any(Runnable.class));
        repoService = new BuildRepoService(bridgeToArtifactory);
        repoService.refreshAllBuildsRepositories();
    }

    @Test
    void getBuildInfoRepoForDefaultProject_shouldReturnArtifactoryBuildInfo_whenDefaultProject() {
        assertEquals(BUILDINFO_REPO_KEY, repoService.getBuildInfoRepoForDefaultProject());
    }

    @Test
    void getBuildInfoRepoKeyByProject_shouldReturnPhoenixBuildInfo_whenProjectExistsInRepos() {
        assertEquals("phoenix-build-info", repoService.getBuildInfoRepoKeyByProject("phoenix"));
    }

    @Test
    void getBuildInfoRepoKeyByProject_shouldThrow_whenProjectNotFound() {
        when(bridgeToArtifactory.getProjectSpec("projectZ")).thenReturn(Optional.empty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> repoService.getBuildInfoRepoKeyByProject("projectZ"));
        assertEquals("Project projectZ was not found.", e.getMessage());
    }

    @Test
    void getBuildInfoRepoKeyByProject_shouldCreateRepoAndReturnKey_whenProjectExistsButRepoMissing() {
        when(bridgeToArtifactory.getProjectSpec("newproj")).thenReturn(
                Optional.of(ProjectSlimEntity.builder().projectKey("newproj").name("New Project").build()));
        when(bridgeToArtifactory.getRepoDescriptor("newproj-build-info")).thenReturn(Optional.empty());

        String repoKey = repoService.getBuildInfoRepoKeyByProject("newproj");

        assertEquals("newproj-build-info", repoKey);
        verify(bridgeToArtifactory).createBuildRepo("newproj-build-info");
        verify(bridgeToArtifactory).upsertProjectResource(
                eq("newproj"), eq(ResourceType.REPO), any(ProjectResourceEntity.class), eq(true));
    }

    @Test
    void isBuildInfoRepo_shouldReturnTrue_whenRepoInAllBuildsRepos() {
        assertTrue(repoService.isBuildInfoRepo(BUILDINFO_REPO_KEY));
        assertTrue(repoService.isBuildInfoRepo("phoenix-build-info"));
    }

    @Test
    void isBuildInfoRepo_shouldReturnFalse_whenRepoNotInAllBuildsRepos() {
        assertFalse(repoService.isBuildInfoRepo("generic-repo"));
    }

    @Test
    void getAllBuildInfoRepos_shouldReturnReposFromBridge() {
        Set<String> repos = repoService.getAllBuildInfoRepos();
        assertTrue(repos.contains(BUILDINFO_REPO_KEY));
        assertTrue(repos.contains("phoenix-build-info"));
        assertTrue(repos.contains("prjx-build-info"));
        assertEquals(3, repos.size());
    }

    @Test
    void createProjectRelatedBuildInfoRepo_shouldReturnArtifactoryBuildInfo_whenDefaultProject() {
        assertEquals(BUILDINFO_REPO_KEY, repoService.createProjectRelatedBuildInfoRepo(DEFAULT_PROJECT));
        verify(bridgeToArtifactory, never()).doAsSystem(any(Runnable.class));
    }

    @Test
    void createProjectRelatedBuildInfoRepo_shouldCallDoAsSystemAndReturnRepoKey_whenNonDefaultProject() {
        when(bridgeToArtifactory.getRepoDescriptor("prjx-build-info")).thenReturn(Optional.empty());

        String repoKey = repoService.createProjectRelatedBuildInfoRepo("prjx");

        assertEquals("prjx-build-info", repoKey);
        verify(bridgeToArtifactory).doAsSystem(any(Runnable.class));
        verify(bridgeToArtifactory).createBuildRepo("prjx-build-info");
        verify(bridgeToArtifactory).upsertProjectResource(
                eq("prjx"), eq(ResourceType.REPO), any(ProjectResourceEntity.class), eq(true));
    }

    @Test
    void assignBuildInfoRepoToProject_shouldCallBridgeDoAsSystemAndUpsertProjectResource() {
        repoService.assignBuildInfoRepoToProject("phoenix-build-info", "phoenix");

        verify(bridgeToArtifactory).doAsSystem(any(Runnable.class));
        verify(bridgeToArtifactory).upsertProjectResource(
                eq("phoenix"), eq(ResourceType.REPO), any(ProjectResourceEntity.class), eq(true));
    }

    @Test
    void getPreferredBuildRepo_shouldReturnBuildRepo_whenBuildRepoProvided() {
        assertEquals("my-repo-build-info", repoService.getPreferredBuildRepo("my-repo-build-info", null));
        assertEquals(BUILDINFO_REPO_KEY, repoService.getPreferredBuildRepo(BUILDINFO_REPO_KEY, "phoenix"));
    }

    @Test
    void getPreferredBuildRepo_shouldReturnProjectBuildInfoRepo_whenBuildRepoEmpty() {
        assertEquals(BUILDINFO_REPO_KEY, repoService.getPreferredBuildRepo(null, DEFAULT_PROJECT));
        assertEquals("phoenix-build-info", repoService.getPreferredBuildRepo(null, "phoenix"));
    }

    @Test
    void normaliseBuildRepoName_shouldReturnInput_whenNonEmpty() {
        assertEquals("repo-build-info", repoService.normaliseBuildRepoName("repo-build-info"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void normaliseBuildRepoName_shouldReturnDefaultBuildInfoRepo_whenEmptyOrBlank(String input) {
        assertEquals(BUILDINFO_REPO_KEY, repoService.normaliseBuildRepoName(input));
    }

    @Test
    void refreshAllBuildsRepositories_shouldPopulateFromBridge() {
        when(bridgeToArtifactory.getRepoKeys(any(), any())).thenReturn(Set.of("custom-build-info"));

        repoService.refreshAllBuildsRepositories();

        assertTrue(repoService.isBuildInfoRepo("custom-build-info"));
        assertEquals(1, repoService.getAllBuildInfoRepos().size());
    }

    @Test
    void getBuildRepoForSearch_shouldReturnNull_whenNoRepoAndNoProject() {
        assertNull(repoService.getBuildRepoForSearch(null, null));
    }

    @Test
    void getBuildRepoForSearch_shouldNormaliseExplicitBuildRepo() {
        assertEquals("phoenix-build-info", repoService.getBuildRepoForSearch("phoenix-build-info", "ignored"));
    }
}
