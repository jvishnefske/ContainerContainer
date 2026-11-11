package org.artifactory.rest.resource.system.locks;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.lock.ClusterNativeDbLocksMechanismStatus;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.artifactory.lock.service.NativeDbLocksActivationService;
import org.artifactory.storage.db.locks.service.LockEntry;
import org.artifactory.storage.db.locks.service.NativeDbLocksService;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * Diagnostic REST resource exposing currently-held PostgreSQL advisory locks.
 * <p>
 * {@code GET /api/system/locks} — admin-only cluster view: aggregates local in-memory lock tracking
 * with per-node fan-out to peers.
 * <p>
 * {@code GET /api/system/locks/local} — HA-internal per-node view: returns only this node's held locks.
 * <p>
 * Locking mechanism status (AUTO / table / native readiness):
 * <ul>
 *   <li>{@code GET /api/system/locks/type} — admin cluster view; fans out to peers</li>
 *   <li>{@code GET /api/system/locks/type/local} — per-node view ({@code ROLE_HA} for fan-out)</li>
 * </ul>
 * <p>
 * See {@code ai-knowledge-base/workspace/guides-and-research/advisory-locks-diagnostic-api.md} for
 * operational guidance on held-lock diagnostics.
 */
@Slf4j
public class LocksResource {

    private static final String STATUS_OK = "ok";
    private static final String STATUS_UNREACHABLE = "unreachable";
    private static final String LOCAL_LOCKS_URI =
            "/api/" + SystemRestConstants.PATH_ROOT + "/" + SystemRestConstants.PATH_LOCKS_LOCAL;

    /**
     * Returns the locks held by this node only. Called internally by peer nodes during fan-out.
     * The endpoint path is {@code /api/system/locks/local}.
     *
     * @param categoryFilter optional category filter; when non-null, only locks matching this category
     *                       are included in the response
     */
    @GET
    @Path("local")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLocalLocks(@QueryParam("category") String categoryFilter) {
        NativeDbLocksService locksService = resolveNativeLocksService();
        if (locksService == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Native advisory locks are not active on this node").build();
        }
        String nodeId = resolveLocalNodeId();
        Collection<LockEntry> locks = locksService.getHeldLocks();
        return Response.ok(buildNodeResponse(nodeId, locks, categoryFilter)).build();
    }

    /**
     * Returns cluster-wide advisory lock state. Fans out to all peer HA nodes.
     * <p>
     * This is a best-effort point-in-time snapshot — not atomic across the cluster.
     *
     * @param categoryFilter optional category filter; when non-null, only locks matching this category
     *                       are included in {@code nodes[].locks} across all nodes
     */
    @GET
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterLocks(@QueryParam("category") String categoryFilter) {
        NativeDbLocksService locksService = resolveNativeLocksService();
        if (locksService == null) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity("Native advisory locks are not active on this node").build();
        }

        HaAddon haAddon = ContextHelper.get().beanForType(AddonsManager.class).addonByType(HaAddon.class);

        String localNodeId = resolveLocalNodeId();
        List<NodeLocksResponse> nodeResponses = new ArrayList<>();
        nodeResponses.add(buildNodeResponse(localNodeId, locksService.getHeldLocks(), categoryFilter));

        List<LocksResponseWarning> warnings = new ArrayList<>();
        boolean haEnabled = false;
        int peerTotal = 0;
        int peerResponded = 0;

        if (haAddon != null && haAddon.isHaEnabled()) {
            haEnabled = true;
            if (haAddon.isHaAuthentication()) {
                warnings.add(new LocksResponseWarning(
                        LocksResponseWarning.CODE_HA_AUTH_FAN_OUT_SKIPPED,
                        "Cluster fan-out is unavailable in HA-Authentication mode; " +
                        "only this node's locks are included."));
                log.warn("Locks cluster view on {}: category={} fanOut=0/? ({})",
                        localNodeId, categoryOrAll(categoryFilter),
                        LocksResponseWarning.CODE_HA_AUTH_FAN_OUT_SKIPPED);
            } else {
                FanOutResult fanOut = fanOutToPeers(haAddon, nodeResponses, categoryFilter);
                peerTotal = fanOut.total();
                peerResponded = fanOut.responded();
                List<String> unreachableIds = fanOut.unreachableIds();
                logFanOutResult(localNodeId, categoryFilter, peerTotal, peerResponded, unreachableIds);
            }
        }

        int totalHeldLocks = nodeResponses.stream()
                .filter(n -> n.getLocks() != null)
                .mapToInt(n -> n.getLocks().size())
                .sum();
        int nodesOk = (int) nodeResponses.stream()
                .filter(n -> STATUS_OK.equals(n.getStatus()))
                .count();
        int nodesUnreachable = (int) nodeResponses.stream()
                .filter(n -> STATUS_UNREACHABLE.equals(n.getStatus()))
                .count();

        LocksResponseMeta meta = new LocksResponseMeta(
                Instant.now().toString(),
                localNodeId,
                new LocksResponseMeta.FanOutMeta(haEnabled, peerResponded, nodesUnreachable));
        LocksResponseSummary summary = new LocksResponseSummary(totalHeldLocks, nodesOk, nodesUnreachable);

        return Response.ok(new ClusterLocksResponse(meta, summary, warnings, nodeResponses)).build();
    }

    /**
     * Returns native DB locks mechanism status for this node only. Called internally by peer nodes during fan-out.
     * The endpoint path is {@code /api/system/locks/type/local}.
     */
    @GET
    @Path("type/local")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLocalNativeLocksMechanismStatus() {
        NativeDbLocksActivationService statusService =
                ContextHelper.get().beanForType(NativeDbLocksActivationService.class);
        NativeDbLocksMechanismStatus status = statusService.resolveLocalStatus();
        return Response.ok(new NativeDbLocksMechanismStatusResponse(resolveLocalNodeId(), status)).build();
    }

    /**
     * Returns cluster-wide native DB locks mechanism status. Fans out to all peer HA nodes via
     * {@code /api/system/locks/type/local} on each peer.
     * <p>
     * Returns HTTP 200 with per-node data even when some peers are unreachable; callers should use
     * {@code unreachableNodeIds} for completeness.
     */
    @GET
    @Path("type")
    @RolesAllowed(AuthorizationService.ROLE_ADMIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClusterNativeLocksMechanismStatus() {
        ArtifactoryContext context = ContextHelper.get();
        NativeDbLocksActivationService statusService =
                context.beanForType(NativeDbLocksActivationService.class);
        ClusterNativeDbLocksMechanismStatus clusterStatus = statusService.resolveClusterStatus(context);
        List<NativeDbLocksMechanismStatusResponse> nodeResponses = clusterStatus.nodes().stream()
                .map(node -> new NativeDbLocksMechanismStatusResponse(node.nodeId(), node.status()))
                .toList();
        ClusterNativeDbLocksMechanismStatusResponse responseBody = new ClusterNativeDbLocksMechanismStatusResponse(
                nodeResponses, List.copyOf(clusterStatus.unreachableNodeIds()));
        return Response.ok(responseBody).build();
    }

    private FanOutResult fanOutToPeers(HaAddon haAddon, List<NodeLocksResponse> nodeResponses,
            String categoryFilter) {
        ArtifactoryServersCommonService serversService =
                ContextHelper.get().beanForType(ArtifactoryServersCommonService.class);
        List<ArtifactoryServer> peers = serversService.getOtherRunningHaMembers();

        String uri = buildLocalLocksUri(categoryFilter);
        List<NodeLocksResponse> peerResponses = haAddon.propagateGetRequest(
                peers, uri, "getLocalLocks", NodeLocksResponse.class);

        Set<String> respondedNodeIds = new HashSet<>();
        for (NodeLocksResponse peer : peerResponses) {
            if (peer != null) {
                nodeResponses.add(peer);
                respondedNodeIds.add(peer.getNodeId());
            }
        }

        List<String> unreachableIds = new ArrayList<>();
        for (ArtifactoryServer peer : peers) {
            if (!respondedNodeIds.contains(peer.getServerId())) {
                nodeResponses.add(new NodeLocksResponse(peer.getServerId(), STATUS_UNREACHABLE, List.of()));
                unreachableIds.add(peer.getServerId());
            }
        }

        return new FanOutResult(peers.size(), respondedNodeIds.size(), unreachableIds);
    }

    private static String buildLocalLocksUri(String categoryFilter) {
        if (categoryFilter == null) {
            return LOCAL_LOCKS_URI;
        }
        return LOCAL_LOCKS_URI + "?category=" + URLEncoder.encode(categoryFilter, StandardCharsets.UTF_8);
    }

    private void logFanOutResult(String nodeId, String categoryFilter, int total, int responded,
            List<String> unreachableIds) {
        String category = categoryOrAll(categoryFilter);
        String msg = "Locks cluster view on {}: category={} fanOut={}/{} responded";
        if (unreachableIds.isEmpty()) {
            log.debug(msg + " (all peers ok)", nodeId, category, responded, total);
        } else if (responded == 0) {
            log.warn(msg + " unreachable={}", nodeId, category, responded, total, unreachableIds);
        } else {
            log.info(msg + " unreachable={}", nodeId, category, responded, total, unreachableIds);
        }
    }

    private static String categoryOrAll(String categoryFilter) {
        return categoryFilter != null ? categoryFilter : "all";
    }

    private static NodeLocksResponse buildNodeResponse(String nodeId, Collection<LockEntry> entries,
            String categoryFilter) {
        List<LockEntryResponse> locks = entries.stream()
                .filter(e -> categoryFilter == null || categoryFilter.equals(e.category()))
                .map(e -> new LockEntryResponse(e.category(), e.key(),
                        e.lockedSinceMs(), e.threadId(), e.threadName()))
                .toList();
        return new NodeLocksResponse(nodeId, STATUS_OK, locks);
    }

    private static NativeDbLocksService resolveNativeLocksService() {
        try {
            return ContextHelper.get().beanForType(NativeDbLocksService.class);
        } catch (RuntimeException e) {
            log.debug("NativeDbLocksService not available (non-PostgreSQL or table-based mode): {}", e.getMessage());
            return null;
        }
    }

    private static String resolveLocalNodeId() {
        return ContextHelper.get().getServerId();
    }

    private record FanOutResult(int total, int responded, List<String> unreachableIds) {}
}
