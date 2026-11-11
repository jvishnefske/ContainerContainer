package org.artifactory.rest.resource.system.locks;

import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.lock.ClusterNativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.service.NativeDbLocksActivationService;
import org.artifactory.storage.db.locks.service.LockEntry;
import org.artifactory.storage.db.locks.service.NativeDbLocksService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocksResourceTest {

    private LocksResource resource;
    private ArtifactoryContext context;
    private NativeDbLocksService locksService;

    @BeforeEach
    void setUp() {
        resource = new LocksResource();
        context = mock(ArtifactoryContext.class);
        locksService = mock(NativeDbLocksService.class);
        when(context.getServerId()).thenReturn("art-1");
    }

    // -------------------------------------------------------------------------
    // getLocalLocks
    // -------------------------------------------------------------------------

    @Test
    void getLocalLocks_returnsServiceUnavailable_whenNativeLocksNotActive() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenThrow(new RuntimeException("not available"));

            Response response = resource.getLocalLocks(null);

            assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void getLocalLocks_returnsNodeLocks_noLocks() {
        when(locksService.getHeldLocks()).thenReturn(List.of());
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);

            Response response = resource.getLocalLocks(null);

            assertEquals(200, response.getStatus());
            NodeLocksResponse body = (NodeLocksResponse) response.getEntity();
            assertNotNull(body);
            assertEquals("art-1", body.getNodeId());
            assertEquals("ok", body.getStatus());
            assertTrue(body.getLocks().isEmpty());
        }
    }

    @Test
    void getLocalLocks_returnsNodeLocks_withOneLock() {
        LockEntry entry = new LockEntry("cat", "key", 1000L, 42L, "main");
        when(locksService.getHeldLocks()).thenReturn(List.of(entry));
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);

            Response response = resource.getLocalLocks(null);

            assertEquals(200, response.getStatus());
            NodeLocksResponse body = (NodeLocksResponse) response.getEntity();
            assertEquals(1, body.getLocks().size());
            LockEntryResponse lock = body.getLocks().get(0);
            assertEquals("cat", lock.getCategory());
            assertEquals("key", lock.getKey());
            assertEquals(1000L, lock.getLockedSinceEpochMs());
            assertEquals(42L, lock.getThreadId());
            assertEquals("main", lock.getThreadName());
        }
    }

    @Test
    void getLocalLocks_categoryFilter_excludesNonMatchingLocks() {
        LockEntry replication = new LockEntry("replication", "repo-1", 1000L, 1L, "t1");
        LockEntry indexing = new LockEntry("indexing", "art-1", 2000L, 2L, "t2");
        when(locksService.getHeldLocks()).thenReturn(List.of(replication, indexing));
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);

            Response response = resource.getLocalLocks("replication");

            NodeLocksResponse body = (NodeLocksResponse) response.getEntity();
            assertEquals(1, body.getLocks().size());
            assertEquals("replication", body.getLocks().get(0).getCategory());
        }
    }

    // -------------------------------------------------------------------------
    // getClusterLocks
    // -------------------------------------------------------------------------

    @Test
    void getClusterLocks_returnsServiceUnavailable_whenNativeLocksNotActive() {
        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenThrow(new RuntimeException("not available"));

            Response response = resource.getClusterLocks(null);

            assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
        }
    }

    @Test
    void getClusterLocks_singleNode_haAddonNull() {
        when(locksService.getHeldLocks()).thenReturn(List.of());

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(null);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            Response response = resource.getClusterLocks(null);

            assertEquals(200, response.getStatus());
            ClusterLocksResponse body = (ClusterLocksResponse) response.getEntity();
            assertNotNull(body);
            assertEquals(1, body.getNodes().size());
            assertEquals("art-1", body.getNodes().get(0).getNodeId());
            assertNotNull(body.getMeta());
            assertNotNull(body.getSummary());
            assertTrue(body.getWarnings().isEmpty());
        }
    }

    @Test
    void getClusterLocks_haDisabled_returnsOnlyLocalNode() {
        when(locksService.getHeldLocks()).thenReturn(List.of());

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(false);

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            Response response = resource.getClusterLocks(null);

            assertEquals(200, response.getStatus());
            ClusterLocksResponse body = (ClusterLocksResponse) response.getEntity();
            assertEquals(1, body.getNodes().size());
        }
    }

    @Test
    void getClusterLocks_haEnabled_peerResponds_andOneUnreachable() {
        when(locksService.getHeldLocks()).thenReturn(List.of());

        NodeLocksResponse peerResponse = new NodeLocksResponse("art-2", "ok", List.of());

        ArtifactoryServer unreachablePeer = mock(ArtifactoryServer.class);
        when(unreachablePeer.getServerId()).thenReturn("art-3");

        List<ArtifactoryServer> peers = List.of(mock(ArtifactoryServer.class), unreachablePeer);
        when(peers.get(0).getServerId()).thenReturn("art-2");

        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        when(serversService.getOtherRunningHaMembers()).thenReturn(peers);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(peers, "/api/system/locks/local", "getLocalLocks",
                NodeLocksResponse.class)).thenReturn(List.of(peerResponse));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            Response response = resource.getClusterLocks(null);

            assertEquals(200, response.getStatus());
            ClusterLocksResponse body = (ClusterLocksResponse) response.getEntity();
            // local + art-2 (responded) + art-3 (unreachable)
            assertEquals(3, body.getNodes().size());
            assertTrue(body.getNodes().stream().anyMatch(n -> "unreachable".equals(n.getStatus())));
            assertEquals(1, body.getSummary().getNodesUnreachable());
            assertEquals("art-1", body.getMeta().getQueriedByNodeId());
            assertEquals(1, body.getMeta().getFanOut().getUnreachable());
        }
    }

    @Test
    void getClusterLocks_haAuth_setsWarningAndSkipsFanOut() {
        when(locksService.getHeldLocks()).thenReturn(List.of());

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(true);

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            Response response = resource.getClusterLocks(null);

            assertEquals(200, response.getStatus());
            ClusterLocksResponse body = (ClusterLocksResponse) response.getEntity();
            assertEquals(1, body.getNodes().size(), "Only local node expected — fan-out skipped in HA-auth mode");
            assertEquals(1, body.getWarnings().size());
            assertEquals(LocksResponseWarning.CODE_HA_AUTH_FAN_OUT_SKIPPED, body.getWarnings().get(0).getCode());
        }
    }

    @Test
    void getClusterLocks_categoryFilter_appliedAcrossNodes() {
        LockEntry replication = new LockEntry("replication", "repo-1", 1000L, 1L, "t1");
        LockEntry indexing = new LockEntry("indexing", "art-1", 2000L, 2L, "t2");
        when(locksService.getHeldLocks()).thenReturn(List.of(replication, indexing));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(null);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            Response response = resource.getClusterLocks("replication");

            ClusterLocksResponse body = (ClusterLocksResponse) response.getEntity();
            assertEquals(1, body.getNodes().size());
            assertEquals(1, body.getNodes().get(0).getLocks().size());
            assertEquals("replication", body.getNodes().get(0).getLocks().get(0).getCategory());
            assertEquals(1, body.getSummary().getTotalHeldLocks());
        }
    }

    @Test
    void getClusterLocks_categoryFilter_urlEncodesFanOutUri() {
        String category = "replication&includeOrphans=false?x=1";
        when(locksService.getHeldLocks()).thenReturn(List.of());

        ArtifactoryServer peer = mock(ArtifactoryServer.class);
        when(peer.getServerId()).thenReturn("art-2");
        List<ArtifactoryServer> peers = List.of(peer);

        ArtifactoryServersCommonService serversService = mock(ArtifactoryServersCommonService.class);
        when(serversService.getOtherRunningHaMembers()).thenReturn(peers);

        HaAddon haAddon = mock(HaAddon.class);
        when(haAddon.isHaEnabled()).thenReturn(true);
        when(haAddon.isHaAuthentication()).thenReturn(false);
        when(haAddon.propagateGetRequest(peers,
                "/api/system/locks/local?category=replication%26includeOrphans%3Dfalse%3Fx%3D1",
                "getLocalLocks", NodeLocksResponse.class)).thenReturn(List.of(
                        new NodeLocksResponse("art-2", "ok", List.of())));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(ArtifactoryServersCommonService.class)).thenReturn(serversService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            Response response = resource.getClusterLocks(category);

            assertEquals(200, response.getStatus());
            verify(haAddon).propagateGetRequest(peers,
                    "/api/system/locks/local?category=replication%26includeOrphans%3Dfalse%3Fx%3D1",
                    "getLocalLocks", NodeLocksResponse.class);
        }
    }

    @Test
    void getClusterLocks_summary_countsAllHeldLocks() {
        LockEntry lock1 = new LockEntry("cat", "k1", 1000L, 1L, "t1");
        LockEntry lock2 = new LockEntry("cat", "k2", 2000L, 2L, "t2");
        when(locksService.getHeldLocks()).thenReturn(List.of(lock1, lock2));

        AddonsManager addonsManager = mock(AddonsManager.class);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(null);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksService.class)).thenReturn(locksService);
            when(context.beanForType(AddonsManager.class)).thenReturn(addonsManager);

            ClusterLocksResponse body = (ClusterLocksResponse) resource.getClusterLocks(null).getEntity();

            assertEquals(2, body.getSummary().getTotalHeldLocks());
            assertEquals(1, body.getSummary().getNodesOk());
            assertEquals(0, body.getSummary().getNodesUnreachable());
        }
    }

    @Test
    void getLocalNativeLocksMechanismStatus_returnsLocalStatus() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.READY_NOT_IN_USE);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getLocalNativeLocksMechanismStatus();

            assertEquals(200, response.getStatus());
            NativeDbLocksMechanismStatusResponse body = (NativeDbLocksMechanismStatusResponse) response.getEntity();
            assertEquals("art-1", body.getNodeId());
            assertEquals("READY_NOT_IN_USE", body.getStatus());
        }
    }

    @Test
    void getClusterNativeLocksMechanismStatus_returnsAllNodesWhenAllPeersReady() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        ClusterNativeDbLocksMechanismStatus clusterStatus = new ClusterNativeDbLocksMechanismStatus(
                List.of(
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of());
        when(statusService.resolveClusterStatus(context)).thenReturn(clusterStatus);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getClusterNativeLocksMechanismStatus();
            ClusterNativeDbLocksMechanismStatusResponse body =
                    (ClusterNativeDbLocksMechanismStatusResponse) response.getEntity();

            assertEquals(200, response.getStatus());

            assertEquals(2, body.getNodes().size());
            assertNotNull(body.getUnreachableNodeIds());
            assertTrue(body.getUnreachableNodeIds().isEmpty());
        }
    }

    @Test
    void getClusterNativeLocksMechanismStatus_listsUnreachablePeers() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        ClusterNativeDbLocksMechanismStatus clusterStatus = new ClusterNativeDbLocksMechanismStatus(
                List.of(new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of("art-2"));
        when(statusService.resolveClusterStatus(context)).thenReturn(clusterStatus);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getClusterNativeLocksMechanismStatus();
            ClusterNativeDbLocksMechanismStatusResponse body =
                    (ClusterNativeDbLocksMechanismStatusResponse) response.getEntity();

            assertEquals(200, response.getStatus());
            assertEquals(1, body.getNodes().size());
            assertEquals(List.of("art-2"), body.getUnreachableNodeIds());
        }
    }

    @Test
    void getClusterNativeLocksMechanismStatus_returnsOkWhenAllPeersInUse() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        ClusterNativeDbLocksMechanismStatus clusterStatus = new ClusterNativeDbLocksMechanismStatus(
                List.of(
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                NativeDbLocksMechanismStatus.IN_USE),
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                NativeDbLocksMechanismStatus.IN_USE)),
                List.of());
        when(statusService.resolveClusterStatus(context)).thenReturn(clusterStatus);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getClusterNativeLocksMechanismStatus();
            ClusterNativeDbLocksMechanismStatusResponse body =
                    (ClusterNativeDbLocksMechanismStatusResponse) response.getEntity();

            assertEquals(200, response.getStatus());
            assertEquals(2, body.getNodes().size());
            assertNotNull(body.getUnreachableNodeIds());
            assertTrue(body.getUnreachableNodeIds().isEmpty());
        }
    }

    @Test
    void getClusterNativeLocksMechanismStatus_listsUnreachablePeersInResponse() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        ClusterNativeDbLocksMechanismStatus clusterStatus = new ClusterNativeDbLocksMechanismStatus(
                List.of(new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of("art-2", "art-3"));
        when(statusService.resolveClusterStatus(context)).thenReturn(clusterStatus);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            ClusterNativeDbLocksMechanismStatusResponse body =
                    (ClusterNativeDbLocksMechanismStatusResponse) resource.getClusterNativeLocksMechanismStatus()
                            .getEntity();

            assertEquals(1, body.getNodes().size());
            assertEquals(List.of("art-2", "art-3"), body.getUnreachableNodeIds());
        }
    }

    @Test
    void getLocalNativeLocksMechanismStatus_returnsSupportedNotReady() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getLocalNativeLocksMechanismStatus();
            NativeDbLocksMechanismStatusResponse body = (NativeDbLocksMechanismStatusResponse) response.getEntity();

            assertEquals(200, response.getStatus());
            assertEquals("SUPPORTED_NOT_READY", body.getStatus());
        }
    }

    @Test
    void getLocalNativeLocksMechanismStatus_returnsInUseWhenActivated() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.IN_USE);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getLocalNativeLocksMechanismStatus();
            NativeDbLocksMechanismStatusResponse body = (NativeDbLocksMechanismStatusResponse) response.getEntity();

            assertEquals(200, response.getStatus());
            assertEquals("IN_USE", body.getStatus());
        }
    }

    @Test
    void getLocalNativeLocksMechanismStatus_returnsNotSupported() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        when(statusService.resolveLocalStatus()).thenReturn(NativeDbLocksMechanismStatus.NOT_SUPPORTED);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            Response response = resource.getLocalNativeLocksMechanismStatus();
            NativeDbLocksMechanismStatusResponse body = (NativeDbLocksMechanismStatusResponse) response.getEntity();

            assertEquals(200, response.getStatus());
            assertEquals("NOT_SUPPORTED", body.getStatus());
        }
    }

    @Test
    void getClusterNativeLocksMechanismStatus_mapsAllNodeStatuses() {
        NativeDbLocksActivationService statusService = mock(NativeDbLocksActivationService.class);
        ClusterNativeDbLocksMechanismStatus clusterStatus = new ClusterNativeDbLocksMechanismStatus(
                List.of(
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY)),
                List.of());
        when(statusService.resolveClusterStatus(context)).thenReturn(clusterStatus);

        try (MockedStatic<ContextHelper> staticHelper = mockStatic(ContextHelper.class)) {
            staticHelper.when(ContextHelper::get).thenReturn(context);
            when(context.beanForType(NativeDbLocksActivationService.class)).thenReturn(statusService);

            ClusterNativeDbLocksMechanismStatusResponse body =
                    (ClusterNativeDbLocksMechanismStatusResponse) resource.getClusterNativeLocksMechanismStatus()
                            .getEntity();

            assertEquals(2, body.getNodes().size());
            assertEquals("READY_NOT_IN_USE", body.getNodes().get(0).getStatus());
            assertEquals("SUPPORTED_NOT_READY", body.getNodes().get(1).getStatus());
            assertTrue(body.getUnreachableNodeIds().isEmpty());
        }
    }
}
