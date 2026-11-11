package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.tabs.general;

import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.RepoDescriptor;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.ArtifactoryRestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.general.virtualrepositories.VirtualRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Amir Avidan
 */
class GetAssociatedVirtualReposServiceTest {

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private ArtifactoryRestRequest request;
    private AutoCloseable autoCloseable;
    private ArtifactoryRestResponse<?> response;
    private GetAssociatedVirtualReposService getAssociatedVirtualReposService;

    @BeforeEach
    void setup() {
        autoCloseable = openMocks(this);
        response = new ArtifactoryRestResponse<>();
        getAssociatedVirtualReposService =
                new GetAssociatedVirtualReposService(repositoryService);
    }

    @AfterEach
    void tearDown() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testGetAssociatedVirtualReposService() {
        when(request.getPathParamByKey("repoKey")).thenReturn("hey");
        RepoDescriptor repoDescriptor = mock(RepoDescriptor.class);
        when(repositoryService.repoDescriptorByKey("hey")).thenReturn(repoDescriptor);
        VirtualRepoDescriptor virtualRepoDescriptor = mock(VirtualRepoDescriptor.class);
        when(virtualRepoDescriptor.getKey()).thenReturn("hey");
        List<VirtualRepoDescriptor> virtualRepoDescriptorList = List.of(virtualRepoDescriptor);
        when(repositoryService.getVirtualReposContainingRepo(repoDescriptor)).thenReturn(
                virtualRepoDescriptorList);
        when(repositoryService.isRepoPathVisible(isA(RepoPath.class))).thenReturn(true);
        getAssociatedVirtualReposService.execute(request, response);
        Map<String, VirtualRepository> responseIModel = response.getIModel();
        List<VirtualRepository> virtualRepositoryList =
                (List<VirtualRepository>) responseIModel.get("virtualRepositories");
        assertEquals(HttpStatus.SC_OK, response.getResponseCode());
        assertEquals(1, virtualRepositoryList.size());
        assertEquals("hey", virtualRepositoryList.get(0).getRepoKey());
        assertEquals("/hey", virtualRepositoryList.get(0).getLinkUrl());
    }

    @Test
    void testRepoDescriptorNull() {
        when(request.getPathParamByKey("repoKey")).thenReturn("hey");
        when(repositoryService.repoDescriptorByKey("hey")).thenReturn(null);
        getAssociatedVirtualReposService.execute(request, response);
        Map<String, VirtualRepository> responseIModel = response.getIModel();
        List<VirtualRepository> virtualRepositoryList =
                (List<VirtualRepository>) responseIModel.get("virtualRepositories");
        assertTrue(virtualRepositoryList.isEmpty());
    }
}
