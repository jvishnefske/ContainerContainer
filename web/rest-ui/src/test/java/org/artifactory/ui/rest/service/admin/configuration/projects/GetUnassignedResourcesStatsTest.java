package org.artifactory.ui.rest.service.admin.configuration.projects;

import com.jfrog.build.acl.bridge.BuildFacade;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.admin.configuration.projects.UnassignedResourceStats;
import org.jfrog.access.common.ResourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetUnassignedResourcesStatsTest {

    private final static String UNASSIGNED_REPO = "repoA";
    private final static String ASSIGNED_REPO = "repoB";
    private final static String ASSIGNED_REPO2 = "virtual1";

    private GetUnassignedResourcesStats getUnassignedResourcesStats;
    private AutoCloseable autoCloseable;

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ProjectsService projectsService;
    @Mock
    private BuildFacade buildFacade;

    @BeforeEach
    public void setUp() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        getUnassignedResourcesStats = Mockito.spy(new GetUnassignedResourcesStats());
        getUnassignedResourcesStats.setBuildFacade(buildFacade);
        getUnassignedResourcesStats.setProjectsService(projectsService);
        getUnassignedResourcesStats.setRepositoryService(repositoryService);

        when(repositoryService.getAllProjectSupportedRepoKeys())
                .thenReturn(Set.of(UNASSIGNED_REPO, ASSIGNED_REPO, ASSIGNED_REPO2));
        when(projectsService.getRepoKeyToProjectMap(anySet(), anyBoolean())).thenReturn(
                Map.of(UNASSIGNED_REPO, ProjectsService.DEFAULT_PROJECT, ASSIGNED_REPO, "proj1", ASSIGNED_REPO2,
                        "proj2"));
        when(buildFacade.getTotalBuildsCount()).thenReturn(100);
        when(buildFacade.getBuildInfoRepoForDefaultProject()).thenReturn(BUILDINFO_REPO_KEY);
        when(buildFacade.getBuildsCountByRepoKey(BUILDINFO_REPO_KEY)).thenReturn(30);
        when(buildFacade.getUniqueBuildsCountByRepoKey(BUILDINFO_REPO_KEY)).thenReturn(20);
    }

    @Test
    void testGetUnassignedResourcesStats() {
        ArtifactoryRestRequest restRequest = mock(ArtifactoryRestRequest.class);
        ArtifactoryRestResponse response = new ArtifactoryRestResponse();

        getUnassignedResourcesStats.execute(restRequest, response);
        List<UnassignedResourceStats> results = (List<UnassignedResourceStats>) response.getIModel();
        Assertions.assertEquals(2, results.size());

        UnassignedResourceStats repoStats = results.get(0);
        Assertions.assertEquals(ResourceType.REPO.getKey(), repoStats.getResourceType());
        Assertions.assertEquals(1, repoStats.getUnassignedCount());
        Assertions.assertEquals(3, repoStats.getTotalCount());

        UnassignedResourceStats buildsStats = results.get(1);
        Assertions.assertEquals(ResourceType.BUILD.getKey(), buildsStats.getResourceType());
        Assertions.assertEquals(30, buildsStats.getUnassignedCount());
        Assertions.assertEquals(20, buildsStats.getUnassignedUniqueCount().intValue());
        Assertions.assertEquals(100, buildsStats.getTotalCount());
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (autoCloseable != null) {
            autoCloseable.close();
            autoCloseable = null;
        }
    }
}