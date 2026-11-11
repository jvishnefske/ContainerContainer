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
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.http.request.ArtifactoryRestRequest;
import org.artifactory.rest.http.response.ArtifactoryRestApiResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetAllReplicationsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_LOCAL = "repo-local";
    private static final String REPO_REMOTE = "repo-remote";
    BaseReplicationService getAllReplicationsService;
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
        getAllReplicationsService = spy(new GetAllReplicationsService());
        artifactoryResponse = new ArtifactoryRestApiResponse();
        when(artifactoryRequest.getPathParamByKey("repoKey")).thenReturn(REPO_LOCAL);
        getAllReplicationsService.setSecurityService(securityService);
        RestAddon restAddon = Mockito.mock(RestAddon.class);
        when(restAddon.isDefault()).thenReturn(false);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        getAllReplicationsService.addonsManager = addonsManager;
        getAllReplicationsService.centralConfigService = centralConfigService;
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO_LOCAL, "")))).thenReturn(true);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO_REMOTE, "")))).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
        clearInvocations(getAllReplicationsService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        verifyNoMoreInteractions(securityService);
        mocks.close();
    }

    @Test
    void testExecuteWithProjectAdmin() {
        MutableCentralConfigDescriptor mutableCentralConfigDescriptor =
                Mockito.mock(MutableCentralConfigDescriptor.class);
        when(centralConfigService.getDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(mutableCentralConfigDescriptor.getLocalReplications()).thenReturn(
                List.of(getLocalReplicationDescriptor(REPO_LOCAL), getLocalReplicationDescriptor("stam")));
        when(mutableCentralConfigDescriptor.getRemoteReplications()).thenReturn(
                List.of(getRemoteReplicationDescriptor(REPO_REMOTE), getRemoteReplicationDescriptor("stam-remote")));
        getAllReplicationsService.execute(artifactoryRequest, artifactoryResponse);
        assertEquals(HttpStatus.SC_OK, artifactoryResponse.getResponseCode());
        List<ReplicationConfigurationDto> replications =
                (List<ReplicationConfigurationDto>) artifactoryResponse.getIModel();
        assertEquals(2, replications.size());
        assertEquals(REPO_LOCAL, replications.get(0).getRepoKey());
        assertEquals(REPO_REMOTE, replications.get(1).getRepoKey());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO_LOCAL, "")));
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO_REMOTE, "")));
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl("stam", "")));
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl("stam-remote", "")));
    }

    private LocalReplicationDescriptor getLocalReplicationDescriptor(String repoKey) {
        LocalReplicationDescriptor replicationBaseDescriptor = new LocalReplicationDescriptor();
        replicationBaseDescriptor.setRepoKey(repoKey);
        return replicationBaseDescriptor;
    }

    private RemoteReplicationDescriptor getRemoteReplicationDescriptor(String repoKey) {
        RemoteReplicationDescriptor replicationBaseDescriptor = new RemoteReplicationDescriptor();
        replicationBaseDescriptor.setRepoKey(repoKey);
        return replicationBaseDescriptor;
    }

}