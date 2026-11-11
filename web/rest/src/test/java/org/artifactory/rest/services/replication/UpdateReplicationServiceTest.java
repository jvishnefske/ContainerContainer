package org.artifactory.rest.services.replication;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.addon.smartrepo.EdgeSmartRepoAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.replication.ReplicationConfigRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.replication.LocalReplicationDescriptor;
import org.artifactory.descriptor.replication.RemoteReplicationDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.descriptor.repo.RemoteRepoDescriptor;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.exception.ForbiddenWebAppException;
import org.artifactory.rest.http.request.ArtifactoryRestRequest;
import org.artifactory.rest.http.response.ArtifactoryRestApiResponse;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UpdateReplicationServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO = "repo";
    BaseReplicationService updateReplicationService;
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
        updateReplicationService = spy(new UpdateReplicationService());
        artifactoryResponse = new ArtifactoryRestApiResponse();
        when(artifactoryRequest.getPathParamByKey("repoKey")).thenReturn(REPO);
        updateReplicationService.setSecurityService(securityService);
        ReplicationConfigRequest replicationConfigRequest = new ReplicationConfigRequest();
        replicationConfigRequest.setEnabled(true);
        replicationConfigRequest.setCronExp("1 10 20 * * ?");
        replicationConfigRequest.setUsername("admin");
        replicationConfigRequest.setPassword("password");
        replicationConfigRequest.setUrl("https://stam.jfrogdev.org/artifactory/example-repo-local");
        when(artifactoryRequest.getImodel()).thenReturn(replicationConfigRequest);
        RestAddon restAddon = mock(RestAddon.class);
        when(restAddon.isDefault()).thenReturn(false);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(restAddon);
        updateReplicationService.addonsManager = addonsManager;
        updateReplicationService.centralConfigService = centralConfigService;
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO);
        when(repositoryService.getRepositoryConfigByKey(REPO)).thenReturn(repoConfig);
        when(repositoryService.localOrFederatedRepoDescriptorByKey(REPO))
                .thenReturn(mock(LocalRepoDescriptor.class));
        when(context.getRepositoryService())
                .thenReturn(repositoryService);
        when(context.beanForType(CentralConfigService.class))
                .thenReturn(centralConfigService);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
        clearInvocations(updateReplicationService);
    }

    @AfterEach
    public void cleanup() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        verifyNoMoreInteractions(securityService);
        mocks.close();
    }

    @Test
    void testExecuteWithAdmin() {
        MutableCentralConfigDescriptor mutableCentralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(mutableCentralConfigDescriptor.getLocalReplication(REPO))
                .thenReturn(new LocalReplicationDescriptor());
        when(centralConfigService.getMutableDescriptor())
                .thenReturn(mutableCentralConfigDescriptor);
        when(centralConfigService.getDescriptor())
                .thenReturn(centralConfigDescriptor);
        updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
        assertEquals(HttpStatus.SC_OK, artifactoryResponse.getResponseCode());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
    }

    @Test
    void testExecuteWithUser() {
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(false);
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> updateReplicationService.execute(artifactoryRequest, artifactoryResponse));
        assertEquals("Could not find repository '" + REPO + "'", badRequestException.getMessage());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
    }

    @Test
    void testExecuteDisablesSyncDeletesDoesNotDisablesEventReplication() {
        initMockForAddonManager(false);
        initRepo();
        RemoteReplicationDescriptor remoteReplicationDescriptor = createCurrentRemoteRepoDescriptor(false, true);
        initDescriptor(remoteReplicationDescriptor);
        when(artifactoryRequest.getImodel()).thenReturn(createReplicationRequest(null, false));
        updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        verify(remoteReplicationDescriptor, never()).setEnableEventReplication(anyBoolean());
        verify(remoteReplicationDescriptor).setSyncDeletes(false);

    }

    @Test
    void testExecuteEnableEventReplicationIsNotEntitled() {
        initMockForAddonManager(false);
        initRepo();
        RemoteReplicationDescriptor remoteReplicationDescriptor = createCurrentRemoteRepoDescriptor(false, true);
        initDescriptor(remoteReplicationDescriptor);
        when(artifactoryRequest.getImodel()).thenReturn(createReplicationRequest(true, null));
        try {
            updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
            fail("Should throw ForbiddenWebAppException");
        } catch (ForbiddenWebAppException e) {
            verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        }
    }

    @Test
    void testExecuteWithEmptyEnableEventReplicationRequestKeepsOldValue() {
        initMockForAddonManager(false);
        initRepo();
        RemoteReplicationDescriptor remoteReplicationDescriptor = createCurrentRemoteRepoDescriptor(true, true);
        initDescriptor(remoteReplicationDescriptor);
        when(artifactoryRequest.getImodel()).thenReturn(createReplicationRequest(null, null));
        updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        verify(remoteReplicationDescriptor, never()).setEnableEventReplication(anyBoolean());
    }

    @Test
    void testExecuteDisablesEventReplicationRequest() {
        initMockForAddonManager(false);
        initRepo();
        RemoteReplicationDescriptor remoteReplicationDescriptor = createCurrentRemoteRepoDescriptor(true, true);
        initDescriptor(remoteReplicationDescriptor);
        when(artifactoryRequest.getImodel()).thenReturn(createReplicationRequest(false, null));
        updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        verify(remoteReplicationDescriptor).setEnableEventReplication(false);
    }

    @Test
    void testExecuteEnableEventReplicationRequest() {
        initMockForAddonManager(true);
        initRepo();
        RemoteReplicationDescriptor remoteReplicationDescriptor = createCurrentRemoteRepoDescriptor(false, true);
        initDescriptor(remoteReplicationDescriptor);
        when(artifactoryRequest.getImodel()).thenReturn(createReplicationRequest(true, null));
        updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        verify(remoteReplicationDescriptor).setEnableEventReplication(true);
    }

    @Test
    void testExecuteEnableEventReplicationDoesNotChange() {
        initMockForAddonManager(true);
        initRepo();
        RemoteReplicationDescriptor remoteReplicationDescriptor = createCurrentRemoteRepoDescriptor(true, true);
        initDescriptor(remoteReplicationDescriptor);
        when(artifactoryRequest.getImodel()).thenReturn(createReplicationRequest(null, null));
        updateReplicationService.execute(artifactoryRequest, artifactoryResponse);
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        verify(remoteReplicationDescriptor, never()).setEnableEventReplication(anyBoolean());
    }

    private void initDescriptor(RemoteReplicationDescriptor remoteReplicationDescriptor) {
        MutableCentralConfigDescriptor mutableCentralConfigDescriptor = mock(MutableCentralConfigDescriptor.class);
        CentralConfigDescriptor centralConfigDescriptor = mock(CentralConfigDescriptor.class);
        when(mutableCentralConfigDescriptor.getRemoteReplication(REPO)).thenReturn(remoteReplicationDescriptor);
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
    }

    private void initRepo() {
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO);
        when(repositoryService.getRepositoryConfigByKey(REPO)).thenReturn(repoConfig);
        when(repositoryService.remoteRepoDescriptorByKey(REPO)).thenReturn(mock(RemoteRepoDescriptor.class));
        when(context.getRepositoryService()).thenReturn(repositoryService);
    }

    private void initMockForAddonManager(boolean isEnabledEventReplicationEntitled) {
        AddonsManager addonsManager = mock(AddonsManager.class);
        updateReplicationService.addonsManager = addonsManager;
        when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(context.beanForType(AddonsManager.class)
                .isEntitled(EntitlementType.EVENT_BASED_PULL_REPLICATION)).thenReturn(
                isEnabledEventReplicationEntitled);
        EdgeSmartRepoAddon edgeSmartRepoAddon = mock(EdgeSmartRepoAddon.class);
        when(addonsManager.addonByType(RestAddon.class)).thenReturn(mock(RestAddon.class));
        when(addonsManager.addonByType(EdgeSmartRepoAddon.class)).thenReturn(edgeSmartRepoAddon);
        doNothing().when(edgeSmartRepoAddon).validateReplication();
    }

    private RemoteReplicationDescriptor createCurrentRemoteRepoDescriptor(Boolean isEnabledEventReplication,
            Boolean isSyncDeletes) {
        RemoteReplicationDescriptor remoteReplicationDescriptor = spy(new RemoteReplicationDescriptor());
        remoteReplicationDescriptor.setEnableEventReplication(isEnabledEventReplication);
        remoteReplicationDescriptor.setSyncDeletes(isSyncDeletes);
        remoteReplicationDescriptor.setCronExp("1 10 20 * * ?");
        Mockito.reset(remoteReplicationDescriptor);
        return remoteReplicationDescriptor;
    }

    private ReplicationConfigRequest createReplicationRequest(Boolean isEnabledEventReplication,
            Boolean isSyncDeletes) {
        ReplicationConfigRequest replicationConfigRequest = new ReplicationConfigRequest();
        replicationConfigRequest.setEnabled(true);
        replicationConfigRequest.setCronExp("1 10 20 * * ?");
        replicationConfigRequest.setUsername("admin");
        replicationConfigRequest.setPassword("password");
        replicationConfigRequest.setUrl("https://stam.jfrogdev.org/artifactory/example-repo-local");
        replicationConfigRequest.setEnableEventReplication(isEnabledEventReplication);
        replicationConfigRequest.setSyncDeletes(isSyncDeletes);
        return replicationConfigRequest;
    }
}