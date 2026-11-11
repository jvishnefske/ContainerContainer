package org.artifactory.ui.rest.resource.artifacts.browse.treebrowser.tabs.generalinfo;

import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.virtualrepositories.VirtualRepository;
import org.artifactory.ui.rest.service.artifacts.browse.BrowseServiceFactory;
import org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general.GetAssociatedVirtualReposService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Amir Avidan
 */
class GeneralArtifactResourceTest {

    @Mock
    private BrowseServiceFactory browseServiceFactory;
    @Mock
    private RepositoryService repositoryService;
    private GeneralArtifactResource generalArtifactResource;
    private AutoCloseable autoCloseable;

    @BeforeEach
    public void setup() {
        autoCloseable = MockitoAnnotations.openMocks(this);
        when(browseServiceFactory.getAssociatedVirtualReposService()).thenReturn(
                new GetAssociatedVirtualReposService(repositoryService));
    }

    @AfterEach
    public void cleanup() throws Exception {
        autoCloseable.close();
    }

    @Test
    void executeRequestWithNullRepoKey() {
        generalArtifactResource =
                new GeneralArtifactResourceSpy(new ArtifactoryRestResponse(), mock(ArtifactoryRestRequest.class));
        ReflectionTestUtils.setField(generalArtifactResource, "browseFactory", browseServiceFactory);
        Response response = generalArtifactResource.getAssociatedVirtualRepos();
        assertEquals(SC_OK, response.getStatus());
        Map<String, VirtualRepository> responseIModel = (Map<String, VirtualRepository>) response.getEntity();
        List<VirtualRepository> virtualRepositoryList =
                (List<VirtualRepository>) responseIModel.get("virtualRepositories");
        assertEquals(SC_OK, response.getStatus());
        assertTrue(virtualRepositoryList.isEmpty());
    }

    @Test
    void executeRequest() {
        ArtifactoryRestRequest request = mock(ArtifactoryRestRequest.class);
        when(request.getPathParamByKey("repoKey")).thenReturn("repoKey");
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("repoKey")).thenReturn(repoDescriptor);
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor.getKey()).thenReturn("repoKey");
        List<VirtualRepoDescriptor> virtualRepoDescriptorList = List.of(virtualRepoDescriptor);
        when(repositoryService.getVirtualReposContainingRepo(repoDescriptor)).thenReturn(
                virtualRepoDescriptorList);
        generalArtifactResource = new GeneralArtifactResourceSpy(new ArtifactoryRestResponse(), request);
        ReflectionTestUtils.setField(generalArtifactResource, "browseFactory", browseServiceFactory);
        Response response = generalArtifactResource.getAssociatedVirtualRepos();
        Map<String, VirtualRepository> responseIModel = (Map<String, VirtualRepository>) response.getEntity();
        List<VirtualRepository> virtualRepositoryList =
                (List<VirtualRepository>) responseIModel.get("virtualRepositories");
        assertEquals(SC_OK, response.getStatus());
        assertEquals(1, virtualRepositoryList.size());
        assertEquals("repoKey", virtualRepositoryList.get(0).getRepoKey());
        assertEquals("/repoKey", virtualRepositoryList.get(0).getLinkUrl());
    }

    static class GeneralArtifactResourceSpy extends GeneralArtifactResource {

        private ArtifactoryRestRequest request;
        private ArtifactoryRestResponse response;

        public GeneralArtifactResourceSpy(ArtifactoryRestResponse response, ArtifactoryRestRequest request) {
            this.response = response;
            this.request = request;
        }

        @Override
        protected <Y> Response runService(RestService<Y> service) {
            service.execute(request, response);
            if (response.getResponseCode() == SC_NOT_FOUND) {
                return Response.status(SC_NOT_FOUND).entity(response.getEntity()).build();
            }
            return Response.ok().entity(response.getIModel()).build();
        }
    }
}
