package com.jfrog.build.service;

import com.jfrog.build.acl.bridge.BuildBridgeToArtifactory;
import org.artifactory.exception.http.ForbiddenException;
import org.jfrog.access.client.project.ProjectEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildProjectServiceTest {

    private BuildProjectService projectService;

    private BuildBridgeToArtifactory bridgeToArtifactory;

    @BeforeEach
    void setUp() {
        bridgeToArtifactory = mock(BuildBridgeToArtifactory.class);
        projectService = new BuildProjectService(bridgeToArtifactory);
    }

    @Test
    void getProjectsByKeys_returnsMapKeyedByProjectKey() {
        ProjectEntity phoenixProject = mock(ProjectEntity.class);
        when(phoenixProject.getProjectKey()).thenReturn("phoenix");
        ProjectEntity acmeProject = mock(ProjectEntity.class);
        when(acmeProject.getProjectKey()).thenReturn("acme");
        when(bridgeToArtifactory.getAllProjects()).thenReturn(List.of(phoenixProject, acmeProject));

        Map<String, ProjectEntity> map = projectService.getProjectsByKeys();

        assertEquals(2, map.size());
        assertEquals(phoenixProject, map.get("phoenix"));
        assertEquals(acmeProject, map.get("acme"));
    }

    @Test
    void getProjectsByKeys_whenNoProjects_returnsEmptyMap() {
        when(bridgeToArtifactory.getAllProjects()).thenReturn(List.of());

        Map<String, ProjectEntity> map = projectService.getProjectsByKeys();

        assertTrue(map.isEmpty());
    }

    @Test
    void verifyProjectAdmin_whenUserIsAdmin_doesNotThrow() {
        when(bridgeToArtifactory.isProjectAdmin("phoenix")).thenReturn(true);

        projectService.verifyProjectAdmin("phoenix");

        verify(bridgeToArtifactory).isProjectAdmin("phoenix");
    }

    @Test
    void verifyProjectAdmin_whenUserIsNotAdmin_throwsForbidden() {
        when(bridgeToArtifactory.isProjectAdmin("phoenix")).thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> projectService.verifyProjectAdmin("phoenix"));

        assertEquals("User is not a project admin for project: phoenix", ex.getMessage());
    }

    @Test
    void extractProjectKeyFromReleaseBundleRepoKey_delegatesToBridge() {
        String bundleRepoKey = "release-bundles-v2-phoenix-local";
        when(bridgeToArtifactory.extractProjectKeyFromReleaseBundleRepoKey(bundleRepoKey)).thenReturn("phoenix");

        assertEquals("phoenix", projectService.extractProjectKeyFromReleaseBundleRepoKey(bundleRepoKey));
        verify(bridgeToArtifactory).extractProjectKeyFromReleaseBundleRepoKey(bundleRepoKey);
    }
}
