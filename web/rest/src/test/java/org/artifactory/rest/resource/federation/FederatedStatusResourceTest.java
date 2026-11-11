package org.artifactory.rest.resource.federation;

import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.event.migration.EventTableRepoKeyMigrationImpl;
import org.artifactory.federation.FederatedArtifactsStatusInfo;
import org.artifactory.federation.FederatedMirrorEventsStatusInfo;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.federation.FederatedRepoStatusInfo;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.federation.FederatedStatusService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.ConnectionStatesRtfsResponse;
import org.artifactory.repo.MemberConnection;
import org.artifactory.repo.RepoBinariesStateRtfsResponse;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.StateListRtfsResponse;
import org.artifactory.repo.StateSummaryRtfsResponse;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Stream;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.artifactory.rest.resource.federation.FederatedStatusResource.BINARIES;
import static org.artifactory.rest.resource.federation.FederatedStatusResource.MIRRORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedStatusResourceTest {

    @Mock
    private FederatedStatusService federatedStatusService;
    @Mock
    private FederatedRepoService federatedRepoService;
    @Mock
    private InternalRepositoryService internalRepositoryService;
    @Mock
    private EventTableRepoKeyMigrationImpl eventTableRepoKeyMigrationImpl;
    @Mock
    private SecurityService securityService;

    private FederatedStatusResource federatedStatusResource;
    AutoCloseable closeable;

    MockedStatic<ContextHelper> contextHelperStatic = mockStatic(ContextHelper.class);

    @BeforeEach
    void setup() {
        ArtifactoryContext context = mock(ArtifactoryContext.class);
        when(context.getCentralConfig()).thenReturn(mock(CentralConfigService.class));
        when(context.getCentralConfig().getDescriptor()).thenReturn(mock(CentralConfigDescriptor.class));
        when(context.getCentralConfig().getDescriptor()
                .getFederatedRepoUrlBaseOrDefault()).thenReturn("https://baseurl.com");
        contextHelperStatic.when(ContextHelper::get).thenReturn(context);

        closeable = MockitoAnnotations.openMocks(this);
        federatedStatusResource = new FederatedStatusResource();
        federatedStatusResource.setService(federatedStatusService);
        federatedStatusResource.setFederatedRepoService(federatedRepoService);
        federatedStatusResource.setInternalRepositoryService(internalRepositoryService);
        federatedStatusResource.setEventTableRepoKeyMigrationImpl(eventTableRepoKeyMigrationImpl);
        federatedStatusResource.setSecurityService(securityService);
    }

    @AfterEach
    void teardown() throws Exception {
        contextHelperStatic.close();
        closeable.close();
    }

    private void mockFederatedRepoConfig(String repoKey) {
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.isFederated()).thenReturn(true);
        when(internalRepositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
    }

    @Test
    void testGetStateList() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(federatedStatusService.getFederationStateList(List.of())).thenReturn(mock(StateListRtfsResponse.class));
        federatedStatusResource.getStateList(null);
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getFederationStateList(null);
    }

    @Test
    void testGetStateListWhenRTFSNotEnabled() {
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        when(federatedStatusService.getFederationStateList(List.of())).thenReturn(mock(StateListRtfsResponse.class));
        Response stateListResponse = federatedStatusResource.getStateList(null);
        assertEquals(SC_METHOD_NOT_ALLOWED, stateListResponse.getStatus());
        verify(federatedStatusService, times(0)).getFederationStateList(List.of());
    }

    @Test
    void testGetStateListFails() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(federatedStatusService.getFederationStateList(null)).thenThrow(BadRequestException.class);
        Response stateListResponse = federatedStatusResource.getStateList(null);
        assertEquals(SC_BAD_REQUEST, stateListResponse.getStatus());
        assertTrue(stateListResponse.getEntity().toString().contains("Failed to get federation state list due to:"));
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getFederationStateList(null);
    }

    @Test
    void testGetStateSummary() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(federatedStatusService.getFederationSummary()).thenReturn(mock(StateSummaryRtfsResponse.class));
        federatedStatusResource.getStateSummary();
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getFederationSummary();
    }

    @Test
    void testGetStateSummaryWhenRTFSNotEnabled() {
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        when(federatedStatusService.getFederationSummary()).thenReturn(mock(StateSummaryRtfsResponse.class));
        Response stateSummary = federatedStatusResource.getStateSummary();
        assertEquals(SC_METHOD_NOT_ALLOWED, stateSummary.getStatus());
        verify(federatedStatusService, times(0)).getFederationStateList(List.of());
    }

    @Test
    void testGetStateSummaryFails() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(federatedStatusService.getFederationSummary()).thenThrow(BadRequestException.class);
        Response stateSummary = federatedStatusResource.getStateSummary();
        assertEquals(SC_BAD_REQUEST, stateSummary.getStatus());
        assertTrue(stateSummary.getEntity().toString().contains("Failed to get federation state summary due to:"));
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getFederationSummary();
    }

    @Test
    void testGetConnectionStates() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        mockFederatedRepoConfig("il-repo");
        MemberConnection memberConnection =
                new MemberConnection("il-repo", "https://il.com", "us-repo", "https://us.com", "HEALTHY", 0L, 0L, false,
                        true);
        ConnectionStatesRtfsResponse connectionStatesRtfsResponse =
                new ConnectionStatesRtfsResponse(List.of(memberConnection));
        when(federatedStatusService.getConnectionStates("il-repo")).thenReturn(connectionStatesRtfsResponse);
        Response response = federatedStatusResource.getConnectionStates("il-repo");
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getConnectionStates("il-repo");
        ConnectionStatesRtfsResponse actualConnectionStates = (ConnectionStatesRtfsResponse) response.getEntity();
        assertEquals(connectionStatesRtfsResponse, actualConnectionStates);
    }

    @Test
    void testGetConnectionStatesForRemoteFederatedRepo() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        mockFederatedRepoConfig("remote-fed");
        ConnectionStatesRtfsResponse connectionStatesRtfsResponse =
                new ConnectionStatesRtfsResponse(List.of());
        when(federatedStatusService.getConnectionStates("remote-fed")).thenReturn(connectionStatesRtfsResponse);
        Response response = federatedStatusResource.getConnectionStates("remote-fed");
        assertEquals(SC_OK, response.getStatus());
        verify(federatedStatusService).getConnectionStates("remote-fed");
    }

    @Test
    void testGetConnectionStatesForNonFederatedRepo() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.isFederated()).thenReturn(false);
        when(internalRepositoryService.getRepoConfigByKey("plain-local")).thenReturn(repoConfig);
        Response response = federatedStatusResource.getConnectionStates("plain-local");
        assertEquals(SC_OK, response.getStatus());
        assertTrue(((ConnectionStatesRtfsResponse) response.getEntity()).memberConnections().isEmpty());
        verify(federatedStatusService, times(0)).getConnectionStates(anyString());
    }

    @Test
    void testGetConnectionStatesForUnknownRepo() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(internalRepositoryService.getRepoConfigByKey("missing")).thenReturn(null);
        Response response = federatedStatusResource.getConnectionStates("missing");
        assertEquals(SC_OK, response.getStatus());
        verify(federatedStatusService, times(0)).getConnectionStates(anyString());
    }

    @Test
    void testGetConnectionStatesWhenNotProjectAdmin() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(false);
        assertThrows(AuthorizationRestException.class,
                () -> federatedStatusResource.getConnectionStates("local"));
        verify(securityService).isProjectAdmin(any(RepoPath.class));
        verify(federatedRepoService, times(0)).isMigrationOver();
        verify(federatedStatusService, times(0)).getConnectionStates(anyString());
    }

    @Test
    void testGetConnectionStatesWhenProjectAdmin() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        mockFederatedRepoConfig("local");
        MemberConnection memberConnection =
                new MemberConnection("local", "https://local.com", "remote", "https://remote.com", "HEALTHY", 0L, 0L, false,
                        true);
        ConnectionStatesRtfsResponse connectionStatesRtfsResponse =
                new ConnectionStatesRtfsResponse(List.of(memberConnection));
        when(federatedStatusService.getConnectionStates("local")).thenReturn(connectionStatesRtfsResponse);
        Response response = federatedStatusResource.getConnectionStates("local");
        assertEquals(SC_OK, response.getStatus());
        verify(securityService).isProjectAdmin(any(RepoPath.class));
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getConnectionStates("local");
    }

    @Test
    void testGetConnectionStatesWhenRTFSNotEnabled() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        when(federatedStatusService.getConnectionStates("local")).thenReturn(mock(ConnectionStatesRtfsResponse.class));
        Response connectionStatesResponse = federatedStatusResource.getConnectionStates("local");
        assertEquals(SC_METHOD_NOT_ALLOWED, connectionStatesResponse.getStatus());
        verify(federatedStatusService, times(0)).getConnectionStates(anyString());
    }

    @Test
    void testGetConnectionStatesFails() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        mockFederatedRepoConfig("local");
        when(federatedStatusService.getConnectionStates("local")).thenThrow(BadRequestException.class);
        Response connectionStatesResponse = federatedStatusResource.getConnectionStates("local");
        assertEquals(SC_BAD_REQUEST, connectionStatesResponse.getStatus());
        assertTrue(connectionStatesResponse.getEntity().toString()
                .contains("Failed to get connection states for local due to"));
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getConnectionStates("local");
    }

    @Test
    void testGetRepoBinariesStateWhenNotProjectAdmin() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(false);
        assertThrows(AuthorizationRestException.class,
                () -> federatedStatusResource.getRepoBinariesState("local", null));
        verify(securityService).isProjectAdmin(any(RepoPath.class));
        verify(federatedRepoService, times(0)).isMigrationOver();
        verify(federatedStatusService, times(0)).getRepoBinariesState(anyString(), anyString());
    }

    @Test
    void testGetRepoBinariesStateWhenProjectAdmin() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        RepoBinariesStateRtfsResponse expectedResponse = mock(RepoBinariesStateRtfsResponse.class);
        when(federatedStatusService.getRepoBinariesState("local", null)).thenReturn(expectedResponse);
        Response repoStateResponse = federatedStatusResource.getRepoBinariesState("local", null);
        assertEquals(SC_OK, repoStateResponse.getStatus());
        assertEquals(expectedResponse, repoStateResponse.getEntity());
        verify(securityService).isProjectAdmin(any(RepoPath.class));
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getRepoBinariesState("local", null);
    }

    @Test
    void testGetRepoBinariesStateWhenRTFSNotEnabled() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        when(federatedStatusService.getRepoBinariesState("local", null)).thenReturn(
                mock(RepoBinariesStateRtfsResponse.class));
        Response repoStateResponse = federatedStatusResource.getRepoBinariesState("local", null);
        assertEquals(SC_METHOD_NOT_ALLOWED, repoStateResponse.getStatus());
        verify(federatedStatusService, times(0)).getRepoBinariesState(anyString(), anyString());
    }

    @Test
    void testGetRepoBinariesStateFails() {
        when(securityService.isProjectAdmin(any(RepoPath.class))).thenReturn(true);
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        when(federatedStatusService.getRepoBinariesState("local", null)).thenThrow(BadRequestException.class);
        Response repoStateResponse = federatedStatusResource.getRepoBinariesState("local", null);
        assertEquals(SC_BAD_REQUEST, repoStateResponse.getStatus());
        assertTrue(repoStateResponse.getEntity().toString()
                .contains("Could not get repo binaries state for local due to"));
        verify(federatedRepoService).isMigrationOver();
        verify(federatedStatusService).getRepoBinariesState("local", null);
    }

    @Test
    void testGetMirrorsLagIsBlockedByRtfsService() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        Response mirrorsLagResponse = federatedStatusResource.getMirrorsLag(2000L);
        assertEquals(SC_METHOD_NOT_ALLOWED, mirrorsLagResponse.getStatus());
    }

    @Test
    void testGetUnavailableMirrorsIsBlockedByRtfsService() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        Response unavailableMirrorsResponse = federatedStatusResource.getUnavailableMirrors();
        assertEquals(SC_METHOD_NOT_ALLOWED, unavailableMirrorsResponse.getStatus());
    }

    @Test
    void testGetFederatedRepoStatusIsBlockedByRtfsService() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        Response federatedRepoStatus = federatedStatusResource.getFederatedRepoStatus("repo");
        assertEquals(SC_METHOD_NOT_ALLOWED, federatedRepoStatus.getStatus());
    }

    @Test
    void testGetJpdStatusIsBlockedByRtfsService() {
        when(federatedRepoService.isMigrationOver()).thenReturn(true);
        Response jpdFederationStatus = federatedStatusResource.getJpdFederationStatus();
        assertEquals(SC_METHOD_NOT_ALLOWED, jpdFederationStatus.getStatus());
    }

    @ParameterizedTest
    @MethodSource("provideRepoStatusFlags")
    void testGetFederatedRepoStatus(boolean v2, boolean fedClientEnabled, boolean migrationFinished) {
        String repoKey = "repoKey";

        when(federatedRepoService.isMigrationOver()).thenReturn(
                fedClientEnabled);
        mockFederatedRepoConfig(repoKey);
        when(eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()).thenReturn(migrationFinished);
        FederatedRepoStatusInfo statusInfo = mock(FederatedRepoStatusInfo.class);
        when(federatedStatusService.getFederatedRepoStatus(eq(repoKey), anyLong(), eq(v2))).thenReturn(statusInfo);

        Response response = federatedStatusResource.getFederatedRepoStatus(repoKey, v2);

        Assertions.assertNotNull(response);
        if (fedClientEnabled) {
            assertEquals(HttpStatus.SC_METHOD_NOT_ALLOWED, response.getStatus());
        } else if (!migrationFinished) {
            assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        } else {
            assertEquals(HttpStatus.SC_OK, response.getStatus());
            assertEquals(response.getEntity(), statusInfo);
        }
    }

    @Test
    void testGetFederatedRepoStatus() {
        String repoKey = "repoKey";
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        when(internalRepositoryService.getRepoConfigByKey(repoKey)).thenReturn(null);
        when(internalRepositoryService.localFederatedOrCachedRepoDescriptorByKey(repoKey)).thenReturn(null);
        assertThrows(NotFoundException.class, () -> federatedStatusResource.getFederatedRepoStatus(repoKey, false));
    }

    @Test
    void testGetFederatedRepoStatusServedForNonFederatedButExistingRepo() {
        String repoKey = "local-cache";
        when(federatedRepoService.isMigrationOver()).thenReturn(false);
        RepoConfig repoConfig = mock(RepoConfig.class);
        when(repoConfig.isFederated()).thenReturn(false);
        when(internalRepositoryService.getRepoConfigByKey(repoKey)).thenReturn(repoConfig);
        when(internalRepositoryService.localFederatedOrCachedRepoDescriptorByKey(repoKey))
                .thenReturn(mock(LocalRepoDescriptor.class));
        when(eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()).thenReturn(true);
        FederatedRepoStatusInfo statusInfo = mock(FederatedRepoStatusInfo.class);
        when(federatedStatusService.getFederatedRepoStatus(eq(repoKey), anyLong(), eq(false))).thenReturn(statusInfo);

        Response response = federatedStatusResource.getFederatedRepoStatus(repoKey, false);

        assertEquals(SC_OK, response.getStatus());
        assertEquals(statusInfo, response.getEntity());
    }

    @ParameterizedTest
    @MethodSource("provideRepoStatusByFedKeyFlags")
    void testGetFederatedRepoStatusByFedKey(boolean v2, boolean isReady, String fedKey, int expectedStatus) {
        String repoKey = "repoKey";

        when(federatedRepoService.isMigrationOver()).thenReturn(isReady);
        mockFederatedRepoConfig(repoKey);
        when(eventTableRepoKeyMigrationImpl.getIfEventTableRepoKeyMigrationFinished()).thenReturn(true);
        FederatedMirrorEventsStatusInfo statusInfo = mock(FederatedMirrorEventsStatusInfo.class);
        when(federatedStatusService.getFederatedMirrorEventsStatus(eq(repoKey), anyLong(), eq(v2))).thenReturn(
                statusInfo);
        FederatedArtifactsStatusInfo artifactStatusInfo = mock(FederatedArtifactsStatusInfo.class);
        when(federatedStatusService.getFederatedArtifactsRepoStatus(eq(repoKey))).thenReturn(artifactStatusInfo);

        Response response = federatedStatusResource.getFederatedRepoStatusByFedKey(repoKey, fedKey, v2);

        Assertions.assertNotNull(response);
        assertEquals(expectedStatus, response.getStatus());
        if (expectedStatus == SC_OK) {
            assertEquals(BINARIES.equals(fedKey) ? artifactStatusInfo : statusInfo, response.getEntity());
        }
    }

    private static Stream<Arguments> provideRepoStatusFlags() {
        return Stream.of(
                Arguments.of(true, true, true),
                Arguments.of(false, true, true),
                Arguments.of(true, false, true),
                Arguments.of(false, false, true),
                Arguments.of(false, false, false),
                Arguments.of(false, false, false)
        );
    }

    private static Stream<Arguments> provideRepoStatusByFedKeyFlags() {
        return Stream.of(
                Arguments.of(true, true, BINARIES, SC_METHOD_NOT_ALLOWED),
                Arguments.of(false, true, MIRRORS, SC_METHOD_NOT_ALLOWED),
                Arguments.of(true, false, BINARIES, SC_OK),
                Arguments.of(false, false, MIRRORS, SC_OK),
                Arguments.of(false, false, "", SC_BAD_REQUEST)
        );
    }
}