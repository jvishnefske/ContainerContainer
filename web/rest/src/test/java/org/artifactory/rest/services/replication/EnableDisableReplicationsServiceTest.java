package org.artifactory.rest.services.replication;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.replication.ReplicationEnableDisableRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RemoteRepositoryRepoTypeConfig;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class EnableDisableReplicationsServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO_LOCAL = "repo-local";
    private static final String REPO_REMOTE = "repo-remote";
    BaseReplicationService enableDisableReplicationService;
    @Mock
    ArtifactoryRestRequest artifactoryRequest;
    @Mock
    SecurityService securityService;
    @Mock
    ArtifactoryContext context;
    @Mock
    CentralConfigService centralConfigService;
    @Mock
    RepositoryService repositoryService;

    ArtifactoryRestApiResponse artifactoryResponse;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        enableDisableReplicationService = spy(new EnableDisableReplicationsService());
        artifactoryResponse = new ArtifactoryRestApiResponse();
        when(artifactoryRequest.getPathParamByKey("repoKey")).thenReturn(REPO_LOCAL);
        enableDisableReplicationService.setSecurityService(securityService);
        enableDisableReplicationService.setRepositoryService(repositoryService);
        ReplicationEnableDisableRequest replicationEnableDisableRequest = new ReplicationEnableDisableRequest();
        replicationEnableDisableRequest.setEnable(true);
        when(artifactoryRequest.getImodel()).thenReturn(replicationEnableDisableRequest);
        RestAddon restAddon = Mockito.mock(RestAddon.class);
        when(restAddon.isDefault()).thenReturn(false);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        enableDisableReplicationService.addonsManager = addonsManager;
        enableDisableReplicationService.centralConfigService = centralConfigService;
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO_LOCAL, "")))).thenReturn(true);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO_REMOTE, "")))).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
        clearInvocations(enableDisableReplicationService);
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
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(mutableCentralConfigDescriptor.getLocalReplications()).thenReturn(
                List.of(getLocalReplicationDescriptor(REPO_LOCAL), getLocalReplicationDescriptor("stam")));
        when(mutableCentralConfigDescriptor.getRemoteReplications()).thenReturn(
                List.of(getRemoteReplicationDescriptor(REPO_REMOTE), getRemoteReplicationDescriptor("stam-remote")));
        RemoteRepoConfig remoteRepo = Mockito.mock(RemoteRepoConfig.class);
        RemoteRepositoryRepoTypeConfig repoTypeConfig = Mockito.mock(RemoteRepositoryRepoTypeConfig.class);
        when(remoteRepo.getRepoTypeConfig()).thenReturn(repoTypeConfig);
        when(repoTypeConfig.getUrl()).thenReturn("http://stam.jfrogdev.org/artifactory/example-repo-local");
        when(repositoryService.remoteRepoConfigByKey("stam-remote")).thenReturn(remoteRepo);
        when(repositoryService.remoteRepoConfigByKey(REPO_REMOTE)).thenReturn(remoteRepo);
        enableDisableReplicationService.execute(artifactoryRequest, artifactoryResponse);
        assertEquals(HttpStatus.SC_CREATED, artifactoryResponse.getResponseCode());
        assertTrue(mutableCentralConfigDescriptor.getLocalReplications().get(0).isEnabled());
        assertFalse(mutableCentralConfigDescriptor.getLocalReplications().get(1).isEnabled());
        assertTrue(mutableCentralConfigDescriptor.getRemoteReplications().get(0).isEnabled());
        assertFalse(mutableCentralConfigDescriptor.getRemoteReplications().get(1).isEnabled());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO_LOCAL, "")));
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO_REMOTE, "")));
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl("stam", "")));
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl("stam-remote", "")));
        verify(centralConfigService, times(1)).saveEditedDescriptorAndReload(mutableCentralConfigDescriptor);
    }

    private LocalReplicationDescriptor getLocalReplicationDescriptor(String repoKey) {
        LocalReplicationDescriptor replicationBaseDescriptor = new LocalReplicationDescriptor();
        replicationBaseDescriptor.setRepoKey(repoKey);
        replicationBaseDescriptor.setEnabled(false);
        return replicationBaseDescriptor;
    }

    private RemoteReplicationDescriptor getRemoteReplicationDescriptor(String repoKey) {
        RemoteReplicationDescriptor replicationBaseDescriptor = new RemoteReplicationDescriptor();
        replicationBaseDescriptor.setRepoKey(repoKey);
        replicationBaseDescriptor.setEnabled(false);
        return replicationBaseDescriptor;
    }
}