package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.generalinfo;

import java.util.Set;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.security.ArtifactoryPermission;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.includedrepositories.Repository;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.virtualrepositories.VirtualRepository;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetIncludedReposService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class GetIncludedReposServiceTest {

    @Mock
    private BrowseServiceFactory browseServiceFactory;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private AuthorizationService authorizationService;
    private GeneralArtifactResource generalArtifactResource;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(browseServiceFactory.getIncludedReposService()).thenReturn(new GetIncludedReposService(repositoryService,
                authorizationService));
    }

    @AfterEach
    public void cleanup() throws Exception {
        autoCloseable.close();
    }

    @Test
    void executeRequestWithNullRepoDescriptor() {
        generalArtifactResource =
                new GeneralArtifactResourceTest.GeneralArtifactResourceSpy(new ArtifactoryRestResponse(), mock(
                        ArtifactoryRestRequest.class));
        ReflectionTestUtils.setField(generalArtifactResource, "browseFactory", browseServiceFactory);
        Response response = generalArtifactResource.getIncludedRepos();
        assertEquals(SC_NOT_FOUND, response.getStatus());
        assertEquals("{\"error\":\"Unable to find repository descriptor for repo key 'null'\"}",
                response.getEntity().toString());
    }

    @Test
    void executeRequest() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn("repoKey");
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("repoKey")).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getKey()).thenReturn("repoKey");
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("repoKey"),
                eq(Set.of(ArtifactoryPermission.READ)), anyMap())).thenReturn(true);
        List<RepoDescriptor> virtualRepoConfigList = List.of(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getRepositories()).thenReturn(virtualRepoConfigList);
        Response response = executeRequest(request);
        Map<String, VirtualRepository> responseIModel = (Map<String, VirtualRepository>) response.getEntity();
        List<Repository> repositories = (List<Repository>) responseIModel.get("includedRepositories");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(1, repositories.size());
        assertEquals("repoKey", repositories.get(0).getRepoKey());
        assertEquals("virtual", repositories.get(0).getType());
        assertEquals("/repoKey", repositories.get(0).getLinkUrl());
    }

    @Test
    void executeRequestWhenEmptyIncludeList() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn("repoKey");
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("repoKey")).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getKey()).thenReturn("repoKey");
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("repoKey"),
                eq(Set.of(ArtifactoryPermission.READ)), anyMap())).thenReturn(true);
        Response response = executeRequest(request);
        Map<String, VirtualRepository> responseIModel = (Map<String, VirtualRepository>) response.getEntity();
        List<Repository> repositories = (List<Repository>) responseIModel.get("includedRepositories");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(0, repositories.size());
    }

    @Test
    void executeRequestWithNoneVirtualRepoDescriptor() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn("repoKey");
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("repoKey")).thenReturn(localRepoDescriptor);
        Response response = executeRequest(request);
        Map<String, VirtualRepository> responseIModel = (Map<String, VirtualRepository>) response.getEntity();
        List<Repository> repositories = (List<Repository>) responseIModel.get("includedRepositories");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(0, repositories.size());
    }

    @Test
    void executeRequestOneWithoutPermissions() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn("repoKey");
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        LocalRepoDescriptor localRepoDescriptor = mock(LocalRepoDescriptor.class);
        RemoteRepoDescriptor repoDescriptor = mock(RemoteRepoDescriptor.class);

        when(repositoryService.repoDescriptorByKey("repoKey")).thenReturn(virtualRepoDescriptor);
        when(virtualRepoDescriptor.getKey()).thenReturn("repoKey");
        when(localRepoDescriptor.getKey()).thenReturn("localKey");
        when(repoDescriptor.getKey()).thenReturn("remoteKey");

        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("localKey"),
                eq(Set.of(ArtifactoryPermission.READ)), anyMap())).thenReturn(true);
        when(authorizationService.userHasPermissionsOnRepositoryRoot(eq("remoteKey"),
                eq(Set.of(ArtifactoryPermission.READ)), anyMap())).thenReturn(false);
        List<RepoDescriptor> virtualRepoConfigList = List.of(localRepoDescriptor, repoDescriptor);

        when(virtualRepoDescriptor.getRepositories()).thenReturn(virtualRepoConfigList);
        Response response = executeRequest(request);
        Map<String, VirtualRepository> responseIModel = (Map<String, VirtualRepository>) response.getEntity();
        List<Repository> repositories = (List<Repository>) responseIModel.get("includedRepositories");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(1, repositories.size());
        assertEquals("localKey", repositories.get(0).getRepoKey());
    }

    private Response executeRequest(ArtifactoryRestRequest request) {
        generalArtifactResource =
                new GeneralArtifactResourceTest.GeneralArtifactResourceSpy(new ArtifactoryRestResponse(), request);
        ReflectionTestUtils.setField(generalArtifactResource, "browseFactory", browseServiceFactory);
        return generalArtifactResource.getIncludedRepos();
    }
}
