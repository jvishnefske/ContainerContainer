package org.artifactory.rest.services.replication;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.http.request.ArtifactoryRestRequest;
import org.artifactory.rest.http.response.ArtifactoryRestApiResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DeleteReplicationsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO = "repo";
    BaseReplicationService deleteReplicationService;
    @Mock
    ArtifactoryRestRequest artifactoryRequest;
    @Mock
    SecurityService securityService;
    @Mock
    ArtifactoryContext context;
    @Mock
    CentralConfigService centralConfigService;

    ArtifactoryRestApiResponse artifactoryResponse;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        deleteReplicationService = spy(new DeleteReplicationsService());
        artifactoryResponse = new ArtifactoryRestApiResponse();
        when(artifactoryRequest.getPathParamByKey("repoKey")).thenReturn(REPO);
        deleteReplicationService.setSecurityService(securityService);
        RestAddon restAddon = Mockito.mock(RestAddon.class);
        when(restAddon.isDefault()).thenReturn(false);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        deleteReplicationService.addonsManager = addonsManager;
        deleteReplicationService.centralConfigService = centralConfigService;
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        RepoConfig repoConfig = Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO);
        when(repositoryService.getRepositoryConfigByKey(REPO)).thenReturn(repoConfig);
        when(repositoryService.localOrFederatedRepoDescriptorByKey(REPO))
                .thenReturn(Mockito.mock(LocalRepoDescriptor.class));
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
        clearInvocations(deleteReplicationService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        verifyNoMoreInteractions(securityService);
        mocks.close();
    }

    @Test
    void testExecuteWithAdmin() {
        MutableCentralConfigDescriptor mutableCentralConfigDescriptor =
                Mockito.mock(MutableCentralConfigDescriptor.class);
        LocalReplicationDescriptor localReplicationDescriptor = new LocalReplicationDescriptor();
        List<LocalReplicationDescriptor> localReplicationDescriptors = new ArrayList<>();
        localReplicationDescriptors.add(localReplicationDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(mutableCentralConfigDescriptor.getLocalReplications()).thenReturn(localReplicationDescriptors);
        when(mutableCentralConfigDescriptor.getMultiLocalReplications(REPO)).thenReturn(localReplicationDescriptors);
        when(artifactoryRequest.getQueryParamByKey("url")).thenReturn("");
        deleteReplicationService.execute(artifactoryRequest, artifactoryResponse);
        assertEquals(HttpStatus.SC_OK, artifactoryResponse.getResponseCode());
        assertTrue(localReplicationDescriptors.isEmpty());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        verify(centralConfigService, times(1)).saveEditedDescriptorAndReload(any());
    }

    @Test
    void testExecuteWithUser() {
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(false);
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> deleteReplicationService.execute(artifactoryRequest, artifactoryResponse));
        assertEquals("Could not find repository '" + REPO + "'", badRequestException.getMessage());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
    }

}