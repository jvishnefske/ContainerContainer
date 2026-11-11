package org.artifactory.rest.services.replication;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.replication.ReplicationConfigRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
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
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CreateReplicationServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO = "repo";
    BaseReplicationService createReplicationService;
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
        createReplicationService = spy(new CreateReplicationService());
        artifactoryResponse = new ArtifactoryRestApiResponse();
        when(artifactoryRequest.getPathParamByKey("repoKey")).thenReturn(REPO);
        createReplicationService.setSecurityService(securityService);
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
        createReplicationService.addonsManager = addonsManager;
        createReplicationService.centralConfigService = centralConfigService;
        InternalRepositoryService repositoryService = mock(InternalRepositoryService.class);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO);
        when(repositoryService.getRepositoryConfigByKey(REPO)).thenReturn(repoConfig);
        when(repositoryService.localOrFederatedRepoDescriptorByKey(REPO))
                .thenReturn(mock(LocalRepoDescriptor.class));
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(context.beanForType(CentralConfigService.class)).thenReturn(centralConfigService);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
        clearInvocations(createReplicationService);
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
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        createReplicationService.execute(artifactoryRequest, artifactoryResponse);
        assertEquals(HttpStatus.SC_CREATED, artifactoryResponse.getResponseCode());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
    }

    @Test
    void testExecuteWithUser() {
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(false);
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> createReplicationService.execute(artifactoryRequest, artifactoryResponse));
        assertEquals("Could not find repository '" + REPO + "'", badRequestException.getMessage());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
    }
}