package org.artifactory.lock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.converter.ConverterManager;
import org.artifactory.descriptor.addon.ha.lock.DbLockingMechanism;
import org.artifactory.lock.ClusterNativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatusNode;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.version.VersionProvider;
import org.jfrog.storage.DbType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves per-node native DB locks mechanism status and verifies cluster-wide readiness for activation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NativeDbLocksActivationService {

    public static final String PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI =
            "/api/" + SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_LOCKS + "/type/local";

    private final CentralConfigService centralConfigService;

    public NativeDbLocksMechanismStatus resolveLocalStatus() {
        DbLockingMechanism mechanism = DbLockingMechanism.get();
        if (mechanism == DbLockingMechanism.JFROG_TABLE_BASED) {
            return NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY;
        }
        if (ArtifactoryHome.get().getDBProperties().getDbType() != DbType.POSTGRESQL) {
            return NativeDbLocksMechanismStatus.NOT_SUPPORTED;
        }
        if (mechanism == DbLockingMechanism.NATIVE
                || (mechanism == DbLockingMechanism.AUTO
                && centralConfigService.getDescriptor().isForceNativeDbLocksMechanismInAutoMode())) {
            return NativeDbLocksMechanismStatus.IN_USE;
        }
        return NativeDbLocksMechanismStatus.READY_NOT_IN_USE;
    }

    /**
     * Fans out to {@code /api/system/locks/type/local} on each HA peer when applicable.
     */
    public ClusterNativeDbLocksMechanismStatus resolveClusterStatus(ArtifactoryContext context) {
        NativeDbLocksMechanismStatus localNodeStatus = resolveLocalStatus();
        ClusterNativeDbLocksMechanismStatus.NodeStatus localNode =
                new ClusterNativeDbLocksMechanismStatus.NodeStatus(context.getServerId(), localNodeStatus);

        AddonsManager addonsManager = context.beanForType(AddonsManager.class);
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        boolean fanOutToPeers = haAddon != null && haAddon.isHaEnabled() && !haAddon.isHaAuthentication();

        PeerNativeStatusFanOut peerFanOutStatus = fanOutToPeers
                ? fanOutNativeStatusToPeers(context, haAddon)
                : PeerNativeStatusFanOut.none();

        List<ClusterNativeDbLocksMechanismStatus.NodeStatus> nodeStatuses = Stream.concat(
                Stream.of(localNode), peerFanOutStatus.peerStatuses().stream()).toList();
        return new ClusterNativeDbLocksMechanismStatus(nodeStatuses, peerFanOutStatus.unreachableNodeIds());
    }

    /**
     * Pre-activation gates (upgrade/conversion/version alignment, HA-auth) before evaluating
     * {@link #resolveClusterStatus(ArtifactoryContext)}.
     */
    public boolean isClusterInfrastructureReadyForActivation(ArtifactoryContext context) {
        if (!isClusterInfrastructureReady(context)) {
            return false;
        }
        AddonsManager addonsManager = context.beanForType(AddonsManager.class);
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        if (haAddon != null && haAddon.isHaEnabled() && haAddon.isHaAuthentication()) {
            log.warn("Cannot verify peer native locks status in HA-Authentication mode; deferring activation");
            return false;
        }
        return true;
    }

    private boolean isClusterInfrastructureReady(ArtifactoryContext context) {
        if (context == null) {
            log.info("Native DB locks activation deferred: Artifactory context is not bound");
            return false;
        }
        if (!context.isReady()) {
            log.info("Native DB locks activation deferred: Artifactory context is not ready");
            return false;
        }
        ArtifactoryServersCommonService serversService =
                context.beanForType(ArtifactoryServersCommonService.class);
        if (serversService.isConversionRunning()) {
            log.info("Native DB locks activation deferred: cluster member(s) still converting");
            return false;
        }
        ConverterManager converterManager = context.getConverterManager();
        if (converterManager != null && converterManager.isConverting()) {
            log.debug("Native DB locks activation deferred: local converters still running on this node");
            return false;
        }
        VersionProvider versionProvider = context.getVersionProvider();
        if (versionProvider == null) {
            return false;
        }
        if (versionProvider.getRunningServiceVersion() == null) {
            log.info("Native DB locks activation deferred: running service version is not available");
            return false;
        }
        String runningVersion = versionProvider.getRunningServiceVersion().getVersionName();
        List<ArtifactoryServer> servers = serversService.getAllArtifactoryServers(false);
        if (servers.isEmpty()) {
            log.info("Native DB locks activation deferred: no Artifactory servers registered");
            return false;
        }
        for (ArtifactoryServer server : servers) {
            if (!isRunningMemberWithVersion(server, runningVersion)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Fans out to {@code /api/system/locks/type/local} on each HA peer with a separate propagation call so the
     * all-or-nothing batch behavior in {@link HaAddon#propagateGetRequest} cannot mask per-peer reachability.
     */
    private PeerNativeStatusFanOut fanOutNativeStatusToPeers(ArtifactoryContext context, HaAddon haAddon) {
        ArtifactoryServersCommonService serversService = context.beanForType(ArtifactoryServersCommonService.class);
        // Uncached peer list must match the fresh snapshot used by isClusterInfrastructureReady().
        List<ArtifactoryServer> peers = serversService.getOtherRunningHaMembersWithOutCache();
        if (peers.isEmpty()) {
            return PeerNativeStatusFanOut.none();
        }

        List<ClusterNativeDbLocksMechanismStatus.NodeStatus> peerStatuses = new ArrayList<>();
        List<String> unreachableNodeIds = new ArrayList<>();
        for (ArtifactoryServer peer : peers) {
            collectPeerNativeStatus(haAddon, peer, peerStatuses, unreachableNodeIds);
        }
        logPeerFanOutResult(peers.size(), peerStatuses.size(), unreachableNodeIds);
        return new PeerNativeStatusFanOut(peerStatuses, unreachableNodeIds);
    }

    private void collectPeerNativeStatus(HaAddon haAddon, ArtifactoryServer peer,
            List<ClusterNativeDbLocksMechanismStatus.NodeStatus> peerStatuses, List<String> unreachableNodeIds) {
        String peerId = peer.getServerId();
        List<NativeDbLocksMechanismStatusNode> responses = haAddon.propagateGetRequest(
                List.of(peer), PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI, "getLocalNativeLocksMechanismStatus",
                NativeDbLocksMechanismStatusNode.class);
        if (responses.size() != 1) {
            log.info("Native locks mechanism status fan-out: peer {} unreachable (expected 1 response, got {})",
                    peerId, responses.size());
            unreachableNodeIds.add(peerId);
            return;
        }
        NativeDbLocksMechanismStatusNode response = responses.getFirst();
        if (response == null || response.getNodeId() == null) {
            log.info("Native locks mechanism status fan-out: peer {} unreachable (missing node id in response)",
                    peerId);
            unreachableNodeIds.add(peerId);
            return;
        }
        NativeDbLocksMechanismStatus peerStatus = NativeDbLocksMechanismStatus.fromName(response.getStatus());
        if (peerStatus == null) {
            log.debug("Native locks mechanism status fan-out: peer {} unreachable (invalid status '{}')",
                    peerId, response.getStatus());
            unreachableNodeIds.add(peerId);
            return;
        }
        peerStatuses.add(new ClusterNativeDbLocksMechanismStatus.NodeStatus(response.getNodeId(), peerStatus));
    }

    private void logPeerFanOutResult(int peerTotal, int responded, List<String> unreachableNodeIds) {
        if (unreachableNodeIds.isEmpty()) {
            log.info("Native locks mechanism status fan-out: {}/{} peers responded (all ok)", responded, peerTotal);
            return;
        }
        if (responded == 0) {
            log.warn("Native locks mechanism status fan-out: 0/{} peers responded, unreachable node ids={}",
                    peerTotal, unreachableNodeIds);
        } else {
            log.warn("Native locks mechanism status fan-out: {}/{} peers responded, unreachable node ids={}",
                    responded, peerTotal, unreachableNodeIds);
        }
    }

    private record PeerNativeStatusFanOut(List<ClusterNativeDbLocksMechanismStatus.NodeStatus> peerStatuses,
            List<String> unreachableNodeIds) {

        private static PeerNativeStatusFanOut none() {
            return new PeerNativeStatusFanOut(List.of(), List.of());
        }
    }

    private boolean isRunningMemberWithVersion(ArtifactoryServer server, String runningVersion) {
        if (server.getServerState() != ArtifactoryServerState.RUNNING) {
            log.info("Native DB locks activation deferred: server {} is not RUNNING (state={})",
                    server.getServerId(), server.getServerState());
            return false;
        }
        if (!ArtifactoryServersCommonService.hasHeartbeat.test(server)) {
            log.info("Native DB locks activation deferred: server {} has a stale heartbeat", server.getServerId());
            return false;
        }
        if (!runningVersion.equals(server.getArtifactoryVersion())) {
            log.info("Native DB locks activation deferred: server {} version mismatch (expected={}, actual={})",
                    server.getServerId(), runningVersion, server.getArtifactoryVersion());
            return false;
        }
        return true;
    }
}
