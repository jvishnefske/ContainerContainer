package org.artifactory.rest.services.replication;

import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.api.rest.replication.MultipleReplicationConfigRequest;
import org.artifactory.api.rest.replication.ReplicationConfigRequest;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.MutableCentralConfigDescriptor;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.http.request.ArtifactoryRestRequest;
import org.artifactory.rest.http.response.ArtifactoryRestApiResponse;
import org.artifactory.rest.resource.replication.ReplicationConfigRequestHelper;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.artifactory.entitlement.EntitlementType.MULTIPUSH_REPLICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CreateMultipleReplicationServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String REPO = "repo";
    BaseReplicationService createMultipleReplicationService;
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
        createMultipleReplicationService = spy(new CreateMultipleReplicationService());
        artifactoryResponse = new ArtifactoryRestApiResponse();
        when(artifactoryRequest.getPathParamByKey("repoKey")).thenReturn(REPO);
        createMultipleReplicationService.setSecurityService(securityService);
        MultipleReplicationConfigRequest multipleReplicationConfigRequest = new MultipleReplicationConfigRequest();
        multipleReplicationConfigRequest.setCronExp("1 10 20 * * ?");
        multipleReplicationConfigRequest.setEnableEventReplication(true);
        ReplicationConfigRequest replicationConfigRequest = new ReplicationConfigRequest();
        replicationConfigRequest.setEnabled(true);
        replicationConfigRequest.setCronExp("1 10 20 * * ?");
        replicationConfigRequest.setUsername("admin");
        replicationConfigRequest.setPassword("password");
        replicationConfigRequest.setUrl("https://stam.jfrogdev.org/artifactory/example-repo-local");
        multipleReplicationConfigRequest.setReplications(List.of(replicationConfigRequest));
        when(artifactoryRequest.getImodel()).thenReturn(multipleReplicationConfigRequest);
        AddonsManager addonsManager = Mockito.mock(AddonsManager.class);
        when(addonsManager.isEntitled(MULTIPUSH_REPLICATION)).thenReturn(true);
        createMultipleReplicationService.addonsManager = addonsManager;
        createMultipleReplicationService.centralConfigService = centralConfigService;
        InternalRepositoryService repositoryService = Mockito.mock(InternalRepositoryService.class);
        RepoConfig repoConfig = Mockito.mock(RepoConfig.class);
        when(repoConfig.getKey()).thenReturn(REPO);
        when(repositoryService.getRepositoryConfigByKey(REPO)).thenReturn(repoConfig);
        when(repositoryService.localOrFederatedRepoDescriptorByKey(REPO))
                .thenReturn(Mockito.mock(LocalRepoDescriptor.class));
        when(context.getRepositoryService()).thenReturn(repositoryService);
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(true);
        ArtifactoryContextThreadBinder.bind(context);
        clearInvocations(createMultipleReplicationService);
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
        when(centralConfigService.getMutableDescriptor()).thenReturn(mutableCentralConfigDescriptor);
        doNothing().when(createMultipleReplicationService).addOrReplace(any(), any());
        try (MockedStatic<ReplicationConfigRequestHelper> replicationConfigRequestHelper =
                     mockStatic(ReplicationConfigRequestHelper.class)) {
            replicationConfigRequestHelper
                    .when(() -> ReplicationConfigRequestHelper.fillLocalReplicationDescriptor(any(), any()))
                    .thenAnswer(invocationOnMock -> null);
            createMultipleReplicationService.execute(artifactoryRequest, artifactoryResponse);
            assertEquals(HttpStatus.SC_CREATED, artifactoryResponse.getResponseCode());
            verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
        }
    }

    @Test
    void testExecuteWithUser() {
        when(securityService.isProjectAdmin(refEq(new RepoPathImpl(REPO, "")))).thenReturn(false);
        BadRequestException badRequestException = assertThrows(BadRequestException.class,
                () -> createMultipleReplicationService.execute(artifactoryRequest, artifactoryResponse));
        assertEquals("Could not find repository '" + REPO + "'", badRequestException.getMessage());
        verify(securityService, times(1)).isProjectAdmin(refEq(new RepoPathImpl(REPO, "")));
    }
}