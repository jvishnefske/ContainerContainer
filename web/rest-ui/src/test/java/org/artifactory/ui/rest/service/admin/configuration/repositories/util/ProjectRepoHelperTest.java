package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.ProjectRepo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.jfrog.access.client.project.EnvironmentEntity;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.jfrog.access.common.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

public class ProjectRepoHelperTest {

    @Mock
    private ProjectsService projectsService;

    private ProjectRepoHelper projectRepoHelper;

    @BeforeEach
    void setUp() {
        projectsService = Mockito.mock(ProjectsService.class);
        projectRepoHelper = new ProjectRepoHelper(projectsService);
    }

    @Test
    void getProjectRepos_returnsGroupedProjectRepos() {
        var projectKey = "project1";
        var projectResourceEntityMap = Map.of(
                "repo1", Mockito.mock(ProjectResourceEntity.class),
                "repo2", mock(ProjectResourceEntity.class)
        );
        when(projectsService.getProjectResourceEntitiesMap(projectKey, ResourceType.REPO, false))
                .thenReturn(projectResourceEntityMap);

        var result = projectRepoHelper.getProjectRepos(projectKey);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("repo1"));
        assertTrue(result.containsKey("repo2"));
    }

    @Test
    void getAllProjectRepos_returnsAllProjectReposWhenNotInProjectContext() {
        RepoConfigRequest request = mock(RepoConfigRequest.class);
        when(request.isProjectContext()).thenReturn(false);

        Map<String, Map<String, ProjectResourceEntity>> allProjectResources = Map.of(
                "project1", Map.of("repo1", mock(ProjectResourceEntity.class)),
                "project2", Map.of("repo2", mock(ProjectResourceEntity.class))
        );
        when(projectsService.getAllProjectsRepositoriesMap(false)).thenReturn(allProjectResources);

        var result = projectRepoHelper.getAllProjectRepos(request);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("repo1"));
        assertTrue(result.containsKey("repo2"));
    }

    @Test
    void getAssignedProject_returnsAssignedProjectResourceEntity() {
        var repoKey = "repo1";
        var projectKey = "project1";
        var assignedEntity = mock(ProjectResourceEntity.class);
        when(assignedEntity.getResourceStatus()).thenReturn(ProjectResourceStatus.SHARED);

        List<ProjectRepo> projectRepos = List.of(
                new ProjectRepo(repoKey, projectKey, assignedEntity)
        );
        var allRepoProjects = Map.of(repoKey, projectRepos);

        var result = projectRepoHelper.getAssignedProject(repoKey, projectKey, allRepoProjects);

        assertNotNull(result);
        assertEquals(assignedEntity, result);
    }

    @Test
    void getAssignedProject_returnsNullWhenProjectReposEmpty() {
        var repoKey = "repo1";
        var projectKey = "project1";
        var assignedEntity = mock(ProjectResourceEntity.class);
        when(assignedEntity.getResourceStatus()).thenReturn(ProjectResourceStatus.SHARED);

        var result = projectRepoHelper.getAssignedProject(repoKey, projectKey, Map.of());

        assertNull(result);
    }

    @Test
    void hasAssignedStatus_returnsTrueWhenAssignedStatusExists() {
        var assignedEntity = mock(ProjectResourceEntity.class);
        when(assignedEntity.getResourceStatus()).thenReturn(ProjectResourceStatus.SHARED);

        List<ProjectRepo> projectRepos = List.of(
                new ProjectRepo("repo1", "project1", assignedEntity)
        );

        boolean result = projectRepoHelper.hasAssignedStatus(projectRepos);

        assertTrue(result);
    }

    @Test
    void hasSharedStatus_returnsTrueWhenSharedStatusExists() {
        var sharedEntity = mock(ProjectResourceEntity.class);
        var status = mock(ProjectResourceStatus.class);
        when(status.isTarget()).thenReturn(true);
        when(sharedEntity.getResourceStatus()).thenReturn(status);

        List<ProjectRepo> projectRepos = List.of(
                new ProjectRepo("repo1", "project1", sharedEntity)
        );

        boolean result = projectRepoHelper.hasSharedStatus(projectRepos);

        assertTrue(result);
    }

    @Test
    void getEnvironments_returnsEnvironmentsFromProjectRepos() {
        var resourceEntity = mock(ProjectResourceEntity.class);
        when(resourceEntity.getEnvironments()).thenReturn(Set.of("env1", "env2"));

        var projectRepos = List.of(
                new ProjectRepo("repo1", "project1", resourceEntity)
        );

        var result = projectRepoHelper.getEnvironments(mock(RepoConfig.class), projectRepos);

        assertEquals(Set.of("env1", "env2"), result);
    }

    @Test
    void getEnvironmentsForDefaultProject_returnsDefaultProjectEnvironments() {
        var repoKey = "repo1";
        var env1 = mock(EnvironmentEntity.class);
        var env2 = mock(EnvironmentEntity.class);
        when(env1.getName()).thenReturn("env1");
        when(env2.getName()).thenReturn("env2");

        when(projectsService.getEnvironmentEntities(repoKey)).thenReturn(List.of(env1, env2));

        var result = projectRepoHelper.getEnvironmentsForDefaultProject(repoKey);

        assertEquals(Set.of("env1", "env2"), result);
    }
}
