package org.artifactory.lock.service;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterManager;
import org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.lock.ClusterNativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatusNode;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.version.CompoundVersionDetails;
import org.artifactory.version.VersionProvider;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class NativeDbLocksActivationStatusServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private CentralConfigService centralConfigService;
    @Mock
    private CentralConfigDescriptor centralConfigDescriptor;
    @Mock
    private ArtifactoryContext artifactoryContext;
    @Mock
    private ArtifactoryServersCommonService serversCommonService;
    @Mock
    private VersionProvider versionProvider;
    @Mock
    private CompoundVersionDetails runningVersion;
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaAddon haAddon;

    private NativeDbLocksActivationService statusService;
    private AutoCloseable mocks;
    private ArtifactoryHomeStub spiedHome;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        statusService = new NativeDbLocksActivationService(centralConfigService);
        when(centralConfigService.getDescriptor()).thenReturn(centralConfigDescriptor);
        when(centralConfigDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(false);
        setStringSystemProperty(ConstantValues.dbLockingMechanism, DbLockingMechanism.AUTO.getName());
        bindPostgresArtifactoryHome();
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.isHaEnabled()).thenReturn(false);
        when(haAddon.isHaAuthentication()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void fromNameReturnsNullForUnknownStatus() {
        assertNull(NativeDbLocksMechanismStatus.fromName("UNKNOWN"));
        assertNull(NativeDbLocksMechanismStatus.fromName(null));
    }

    @Test
    void fromNameReturnsStatusForValidName() {
        assertEquals(NativeDbLocksMechanismStatus.IN_USE, NativeDbLocksMechanismStatus.fromName("IN_USE"));
        assertEquals(NativeDbLocksMechanismStatus.READY_NOT_IN_USE,
                NativeDbLocksMechanismStatus.fromName("READY_NOT_IN_USE"));
        assertEquals(NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY,
                NativeDbLocksMechanismStatus.fromName("SUPPORTED_NOT_READY"));
        assertEquals(NativeDbLocksMechanismStatus.NOT_SUPPORTED,
                NativeDbLocksMechanismStatus.fromName("NOT_SUPPORTED"));
    }

    @Test
    void resolveLocalStatusReturnsInUseForNativeMechanism() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, DbLockingMechanism.NATIVE.getName());

        assertEquals(NativeDbLocksMechanismStatus.IN_USE, statusService.resolveLocalStatus());
    }

    @Test
    void resolveLocalStatusReturnsInUseWhenForceNativeEnabled() {
        when(centralConfigDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(true);

        assertEquals(NativeDbLocksMechanismStatus.IN_USE, statusService.resolveLocalStatus());
    }

    @Test
    void resolveLocalStatusReturnsSupportedNotReadyForTableBasedMechanism() {
        setStringSystemProperty(ConstantValues.dbLockingMechanism, DbLockingMechanism.JFROG_TABLE_BASED.getName());

        assertEquals(NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY, statusService.resolveLocalStatus());
    }

    @Test
    void resolveLocalStatusReturnsReadyNotInUseWhenEligible() {
        assertEquals(NativeDbLocksMechanismStatus.READY_NOT_IN_USE, statusService.resolveLocalStatus());
    }

    @Test
    void resolveLocalStatusReturnsNotSupportedForNonPostgresAuto() {
        bindDbType(DbType.MYSQL);

        assertEquals(NativeDbLocksMechanismStatus.NOT_SUPPORTED, statusService.resolveLocalStatus());
    }

    @Test
    void resolveLocalStatusReturnsNotSupportedForNonPostgresEvenWhenForceNativeEnabled() {
        bindDbType(DbType.ORACLE);
        when(centralConfigDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(true);

        assertEquals(NativeDbLocksMechanismStatus.NOT_SUPPORTED, statusService.resolveLocalStatus());
    }

    @Test
    void resolveLocalStatusReturnsSupportedNotReadyForNonPostgresTableBased() {
        bindDbType(DbType.MYSQL);
        setStringSystemProperty(ConstantValues.dbLockingMechanism, DbLockingMechanism.JFROG_TABLE_BASED.getName());

        assertEquals(NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY, statusService.resolveLocalStatus());
    }

    @Test
    void resolveClusterStatusAllReadyNotInUseWhenAllPeersReady() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        stubHaFanOut(List.of(new NativeDbLocksMechanismStatusNode(
                "art-2", NativeDbLocksMechanismStatus.READY_NOT_IN_USE)));

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(2, clusterStatus.nodes().size());
        assertTrue(clusterStatus.unreachableNodeIds().isEmpty());
        assertTrue(clusterStatus.allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusAllReadyNotInUseFalseWhenPeerUnreachable() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        stubHaFanOut(List.of());

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertEquals(List.of("art-2"), clusterStatus.unreachableNodeIds());
        assertFalse(clusterStatus.allPeersResponded());
        assertFalse(clusterStatus.allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusTracksPeerReachabilityIndependently() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        ArtifactoryServer peer1 = mock(ArtifactoryServer.class);
        when(peer1.getServerId()).thenReturn("art-2");
        ArtifactoryServer peer2 = mock(ArtifactoryServer.class);
        when(peer2.getServerId()).thenReturn("art-3");
        when(serversCommonService.getOtherRunningHaMembersWithOutCache()).thenReturn(List.of(peer1, peer2));
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(eq(List.of(peer1)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(List.of(new NativeDbLocksMechanismStatusNode(
                        "art-2", NativeDbLocksMechanismStatus.READY_NOT_IN_USE)));
        when(haAddon.propagateGetRequest(eq(List.of(peer2)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(List.of());

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(2, clusterStatus.nodes().size());
        assertEquals(List.of("art-3"), clusterStatus.unreachableNodeIds());
        assertFalse(clusterStatus.allPeersResponded());
        assertFalse(clusterStatus.allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusInvokesPropagationOncePerPeer() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        ArtifactoryServer peer1 = mock(ArtifactoryServer.class);
        when(peer1.getServerId()).thenReturn("art-2");
        ArtifactoryServer peer2 = mock(ArtifactoryServer.class);
        when(peer2.getServerId()).thenReturn("art-3");
        when(serversCommonService.getOtherRunningHaMembersWithOutCache()).thenReturn(List.of(peer1, peer2));
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(eq(List.of(peer1)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(List.of(new NativeDbLocksMechanismStatusNode(
                        "art-2", NativeDbLocksMechanismStatus.READY_NOT_IN_USE)));
        when(haAddon.propagateGetRequest(eq(List.of(peer2)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(List.of(new NativeDbLocksMechanismStatusNode(
                        "art-3", NativeDbLocksMechanismStatus.READY_NOT_IN_USE)));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        statusService.resolveClusterStatus(artifactoryContext);

        verify(haAddon, times(1)).propagateGetRequest(eq(List.of(peer1)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class));
        verify(haAddon, times(1)).propagateGetRequest(eq(List.of(peer2)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenConversionRunning() {
        when(artifactoryContext.isReady()).thenReturn(true);
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);
        when(artifactoryContext.getConverterManager()).thenReturn(null);
        when(serversCommonService.isConversionRunning()).thenReturn(true);

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenConverterManagerIsConverting() {
        ConverterManager converterManager = mock(ConverterManager.class);
        when(artifactoryContext.isReady()).thenReturn(true);
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);
        when(artifactoryContext.getConverterManager()).thenReturn(converterManager);
        when(serversCommonService.isConversionRunning()).thenReturn(false);
        when(converterManager.isConverting()).thenReturn(true);

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsTrueWhenInfrastructureReady() {
        stubClusterInfrastructureReady();

        assertTrue(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void resolveClusterStatusAllReadyNotInUseWhenSingleNodeReady() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");

        assertTrue(statusService.resolveClusterStatus(artifactoryContext).allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusAllReadyNotInUseFalseWhenLocalStatusNotReady() {
        when(centralConfigDescriptor.isForceNativeDbLocksMechanismInAutoMode()).thenReturn(true);
        when(artifactoryContext.getServerId()).thenReturn("art-1");

        assertFalse(statusService.resolveClusterStatus(artifactoryContext).allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusAllReadyNotInUseFalseWhenPeerNotReady() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        stubHaFanOut(List.of(new NativeDbLocksMechanismStatusNode(
                "art-2", NativeDbLocksMechanismStatus.IN_USE)));

        assertFalse(statusService.resolveClusterStatus(artifactoryContext).allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusSkipsFanOutWhenHaAuthenticationEnabled() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        stubHaAddon(haAddon -> {
            when(haAddon.isHaEnabled()).thenReturn(true);
            when(haAddon.isHaAuthentication()).thenReturn(true);
        });

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertEquals("art-1", clusterStatus.nodes().get(0).nodeId());
        assertTrue(clusterStatus.unreachableNodeIds().isEmpty());
        assertTrue(clusterStatus.allPeersResponded());
        assertTrue(clusterStatus.allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusSkipsFanOutWhenHaDisabled() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        stubHaAddon(haAddon -> when(haAddon.isHaEnabled()).thenReturn(false));

        assertEquals(1, statusService.resolveClusterStatus(artifactoryContext).nodes().size());
    }

    @Test
    void resolveClusterStatusSkipsFanOutWhenNoHaAddon() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(null);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        assertEquals(1, statusService.resolveClusterStatus(artifactoryContext).nodes().size());
    }

    @Test
    void resolveClusterStatusTreatsPeerWithInvalidStatusAsUnreachable() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        NativeDbLocksMechanismStatusNode peerResponse = new NativeDbLocksMechanismStatusNode();
        peerResponse.setNodeId("art-2");
        peerResponse.setStatus("NOT_A_REAL_STATUS");
        stubHaFanOut(List.of(peerResponse));

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertEquals(List.of("art-2"), clusterStatus.unreachableNodeIds());
        assertFalse(clusterStatus.allPeersResponded());
        assertFalse(clusterStatus.allReadyNotInUse());
    }

    @Test
    void resolveClusterStatusTreatsPeerWithNullNodeIdAsUnreachable() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        NativeDbLocksMechanismStatusNode peerResponse = new NativeDbLocksMechanismStatusNode(
                null, NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
        stubHaFanOut(List.of(peerResponse));

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertEquals(List.of("art-2"), clusterStatus.unreachableNodeIds());
    }

    @Test
    void resolveClusterStatusAllPeersRespondedWhenNoOtherHaMembers() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);
        when(serversCommonService.getOtherRunningHaMembersWithOutCache()).thenReturn(List.of());
        stubHaAddon(haAddon -> {
            when(haAddon.isHaEnabled()).thenReturn(true);
            when(haAddon.isHaAuthentication()).thenReturn(false);
        });

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertTrue(clusterStatus.allPeersResponded());
        assertTrue(clusterStatus.allReadyNotInUse());
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenContextNotReady() {
        when(artifactoryContext.isReady()).thenReturn(false);

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenHaAuthenticationEnabled() {
        stubClusterInfrastructureReady();
        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(true);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenNoServersRegistered() {
        when(artifactoryContext.isReady()).thenReturn(true);
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);
        when(artifactoryContext.getConverterManager()).thenReturn(null);
        when(serversCommonService.isConversionRunning()).thenReturn(false);
        when(serversCommonService.getAllArtifactoryServers(false)).thenReturn(List.of());

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenServerNotRunning() {
        stubClusterInfrastructureReady();
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        when(server.getServerId()).thenReturn("art-2");
        when(server.getServerState()).thenReturn(ArtifactoryServerState.STARTING);
        when(server.getArtifactoryVersion()).thenReturn("7.90.0");
        when(server.getLastHeartbeat()).thenReturn(System.currentTimeMillis());
        when(serversCommonService.getAllArtifactoryServers(false)).thenReturn(List.of(server));

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenVersionMismatch() {
        stubClusterInfrastructureReady();
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        when(server.getServerId()).thenReturn("art-2");
        when(server.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(server.getArtifactoryVersion()).thenReturn("7.80.0");
        when(server.getLastHeartbeat()).thenReturn(System.currentTimeMillis());
        when(serversCommonService.getAllArtifactoryServers(false)).thenReturn(List.of(server));

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenHeartbeatStale() {
        stubClusterInfrastructureReady();
        ArtifactoryServer server = mock(ArtifactoryServer.class);
        when(server.getServerId()).thenReturn("art-2");
        when(server.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(server.getArtifactoryVersion()).thenReturn("7.90.0");
        when(server.getLastHeartbeat()).thenReturn(0L);
        when(serversCommonService.getAllArtifactoryServers(false)).thenReturn(List.of(server));

        assertFalse(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void resolveClusterStatusTreatsPeerWithMultipleResponsesAsUnreachable() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        ArtifactoryServer peer = mock(ArtifactoryServer.class);
        when(peer.getServerId()).thenReturn("art-2");
        when(serversCommonService.getOtherRunningHaMembersWithOutCache()).thenReturn(List.of(peer));
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(eq(List.of(peer)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(List.of(
                        new NativeDbLocksMechanismStatusNode("art-2", NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                        new NativeDbLocksMechanismStatusNode("art-2", NativeDbLocksMechanismStatus.READY_NOT_IN_USE)));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertEquals(List.of("art-2"), clusterStatus.unreachableNodeIds());
        assertFalse(clusterStatus.allPeersResponded());
    }

    @Test
    void resolveClusterStatusTreatsNullResponseAsUnreachable() {
        when(artifactoryContext.getServerId()).thenReturn("art-1");
        ArtifactoryServer peer = mock(ArtifactoryServer.class);
        when(peer.getServerId()).thenReturn("art-2");
        when(serversCommonService.getOtherRunningHaMembersWithOutCache()).thenReturn(List.of(peer));
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(eq(List.of(peer)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(java.util.Collections.singletonList(null));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(artifactoryContext);

        assertEquals(1, clusterStatus.nodes().size());
        assertEquals(List.of("art-2"), clusterStatus.unreachableNodeIds());
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsTrueWhenHaAddonIsNull() {
        stubClusterInfrastructureReady();
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(null);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);

        assertTrue(statusService.isClusterInfrastructureReadyForActivation(artifactoryContext));
    }

    @Test
    void isClusterInfrastructureReadyForActivationReturnsFalseWhenContextIsNull() {
        assertFalse(statusService.isClusterInfrastructureReadyForActivation(null));
    }

    private void stubHaFanOut(List<NativeDbLocksMechanismStatusNode> peerResponses) {
        ArtifactoryServer peer = mock(ArtifactoryServer.class);
        when(peer.getServerId()).thenReturn("art-2");
        when(serversCommonService.getOtherRunningHaMembersWithOutCache()).thenReturn(List.of(peer));
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(eq(List.of(peer)),
                eq(NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI),
                eq("getLocalNativeLocksMechanismStatus"),
                eq(NativeDbLocksMechanismStatusNode.class)))
                .thenReturn(peerResponses);

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    private void stubHaAddon(java.util.function.Consumer<HaAddon> haAddonConfigurer) {
        HaAddon haAddon = mock(HaAddon.class);
        haAddonConfigurer.accept(haAddon);
        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(artifactoryContext.beanForType(AddonsManager.class)).thenReturn(addonsManager);
    }

    private void stubClusterInfrastructureReady() {
        when(artifactoryContext.isReady()).thenReturn(true);
        when(artifactoryContext.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversCommonService);
        when(artifactoryContext.getConverterManager()).thenReturn(null);
        when(artifactoryContext.getVersionProvider()).thenReturn(versionProvider);
        when(versionProvider.getRunningServiceVersion()).thenReturn(runningVersion);
        when(runningVersion.getVersionName()).thenReturn("7.90.0");
        when(serversCommonService.isConversionRunning()).thenReturn(false);

        ArtifactoryServer server = mock(ArtifactoryServer.class);
        when(server.getServerState()).thenReturn(ArtifactoryServerState.RUNNING);
        when(server.getArtifactoryVersion()).thenReturn("7.90.0");
        when(server.getLastHeartbeat()).thenReturn(System.currentTimeMillis());
        when(serversCommonService.getAllArtifactoryServers(false)).thenReturn(List.of(server));
    }

    private void bindPostgresArtifactoryHome() {
        bindDbType(DbType.POSTGRESQL);
    }

    private void bindDbType(DbType dbType) {
        ArtifactoryDbProperties dbProperties = mock(ArtifactoryDbProperties.class);
        when(dbProperties.getDbType()).thenReturn(dbType);
        if (spiedHome == null) {
            spiedHome = Mockito.spy(getBound());
            ArtifactoryHome.bind(spiedHome);
        }
        when(spiedHome.getDBProperties()).thenReturn(dbProperties);
    }
}
