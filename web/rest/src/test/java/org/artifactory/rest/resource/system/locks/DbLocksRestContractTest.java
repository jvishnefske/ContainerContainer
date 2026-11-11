package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.lock.NativeDbLocksMechanismStatus;
import org.junit.jupiter.api.Test;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Schema-contract regression tests for the {@code GET /api/system/locks} REST API.
 * <p>
 * Guards against accidental regressions that would break JSON consumers or change security:
 * <ol>
 *   <li>JSON field renaming or removal in any response model class.</li>
 *   <li>Unexpected field additions (consumers must update this test to acknowledge new fields).</li>
 *   <li>Path or HTTP-method change on the cluster or local endpoints.</li>
 *   <li>Permission downgrade on either endpoint.</li>
 * </ol>
 * This test has no Spring context dependency and runs as a pure unit test.
 */
class DbLocksRestContractTest {

    private static final Map<String, Set<String>> EXPECTED_FIELDS = Map.of(
            "LockEntryResponse", Set.of(
                    "category", "key", "lockedSinceEpochMs", "lockedSinceIso", "threadId", "threadName"),
            "NodeLocksResponse", Set.of("nodeId", "status", "locks"),
            "ClusterLocksResponse", Set.of("meta", "summary", "warnings", "nodes"),
            "LocksResponseWarning", Set.of("code", "message"),
            "LocksResponseSummary", Set.of("totalHeldLocks", "nodesOk", "nodesUnreachable"),
            "NativeDbLocksMechanismStatusResponse", Set.of("nodeId", "status"),
            "ClusterNativeDbLocksMechanismStatusResponse", Set.of("nodes", "unreachableNodeIds")
    );

    @Test
    void responseModelFieldNamesAreStable() {
        assertFieldContract(LockEntryResponse.class, "LockEntryResponse");
        assertFieldContract(NodeLocksResponse.class, "NodeLocksResponse");
        assertFieldContract(ClusterLocksResponse.class, "ClusterLocksResponse");
        assertFieldContract(LocksResponseWarning.class, "LocksResponseWarning");
        assertFieldContract(LocksResponseSummary.class, "LocksResponseSummary");
        assertFieldContract(NativeDbLocksMechanismStatusResponse.class, "NativeDbLocksMechanismStatusResponse");
        assertFieldContract(ClusterNativeDbLocksMechanismStatusResponse.class,
                "ClusterNativeDbLocksMechanismStatusResponse");
    }

    @Test
    void clusterEndpointAnnotationsAreStable() throws Exception {
        Method method = findMethod(LocksResource.class, "getClusterLocks");
        assertNotNull(method, "getClusterLocks method must exist");

        assertNotNull(method.getAnnotation(GET.class), "getClusterLocks must be annotated with @GET");

        RolesAllowed roles = method.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "getClusterLocks must have @RolesAllowed");
        Set<String> allowedRoles = Set.of(roles.value());
        assertTrue(allowedRoles.contains(AuthorizationService.ROLE_ADMIN),
                "getClusterLocks must require ROLE_ADMIN");
        assertEquals(1, allowedRoles.size(), "getClusterLocks must require ONLY ROLE_ADMIN (not HA role)");
    }

    @Test
    void localEndpointAnnotationsAreStable() throws Exception {
        Method method = findMethod(LocksResource.class, "getLocalLocks");
        assertNotNull(method, "getLocalLocks method must exist");

        assertNotNull(method.getAnnotation(GET.class), "getLocalLocks must be annotated with @GET");

        Path path = method.getAnnotation(Path.class);
        assertNotNull(path, "getLocalLocks must have @Path");
        assertEquals("local", path.value(), "getLocalLocks path must be 'local'");

        RolesAllowed roles = method.getAnnotation(RolesAllowed.class);
        assertNotNull(roles, "getLocalLocks must have @RolesAllowed");
        Set<String> allowedRoles = Set.of(roles.value());
        assertTrue(allowedRoles.contains(AuthorizationService.ROLE_ADMIN),
                "getLocalLocks must permit ROLE_ADMIN");
        assertTrue(allowedRoles.contains(HaRestConstants.ROLE_HA),
                "getLocalLocks must permit ROLE_HA for inter-node fan-out");
    }

    @Test
    void nativeStatusLocalEndpointAnnotationsAreStable() throws Exception {
        Method method = findMethod(LocksResource.class, "getLocalNativeLocksMechanismStatus");
        assertNotNull(method, "getLocalNativeLocksMechanismStatus method must exist");

        assertNotNull(method.getAnnotation(GET.class));

        Path path = method.getAnnotation(Path.class);
        assertNotNull(path);
        assertEquals("type/local", path.value());

        RolesAllowed roles = method.getAnnotation(RolesAllowed.class);
        assertNotNull(roles);
        Set<String> allowedRoles = Set.of(roles.value());
        assertTrue(allowedRoles.contains(AuthorizationService.ROLE_ADMIN));
        assertTrue(allowedRoles.contains(HaRestConstants.ROLE_HA));
    }

    @Test
    void nativeStatusClusterEndpointAnnotationsAreStable() throws Exception {
        Method method = findMethod(LocksResource.class, "getClusterNativeLocksMechanismStatus");
        assertNotNull(method, "getClusterNativeLocksMechanismStatus method must exist");

        assertNotNull(method.getAnnotation(GET.class));

        Path path = method.getAnnotation(Path.class);
        assertNotNull(path);
        assertEquals("type", path.value());

        RolesAllowed roles = method.getAnnotation(RolesAllowed.class);
        assertNotNull(roles);
        Set<String> allowedRoles = Set.of(roles.value());
        assertTrue(allowedRoles.contains(AuthorizationService.ROLE_ADMIN));
        assertEquals(1, allowedRoles.size());
    }

    @Test
    void peerFanOutUriConstantMatchesLocalStatusEndpoint() {
        assertEquals("/api/system/locks/type/local",
                org.artifactory.lock.service.NativeDbLocksActivationService.PEER_FAN_OUT_NATIVE_LOCKS_STATUS_URI);
    }

    @Test
    void jacksonRoundTripPreservesNativeStatusFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClusterNativeDbLocksMechanismStatusResponse cluster = new ClusterNativeDbLocksMechanismStatusResponse(
                List.of(new NativeDbLocksMechanismStatusResponse("art-1",
                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of());

        String json = mapper.writeValueAsString(cluster);
        assertTrue(json.contains("\"unreachableNodeIds\":[]"),
                "unreachableNodeIds must serialize as an empty array when no peers are unreachable");
        ClusterNativeDbLocksMechanismStatusResponse deserialized =
                mapper.readValue(json, ClusterNativeDbLocksMechanismStatusResponse.class);

        assertEquals(1, deserialized.getNodes().size());
        assertEquals("art-1", deserialized.getNodes().get(0).getNodeId());
        assertEquals("READY_NOT_IN_USE", deserialized.getNodes().get(0).getStatus());
        assertNotNull(deserialized.getUnreachableNodeIds());
        assertTrue(deserialized.getUnreachableNodeIds().isEmpty());
    }

    @Test
    void jacksonRoundTripPreservesUnreachableNodeIds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClusterNativeDbLocksMechanismStatusResponse cluster = new ClusterNativeDbLocksMechanismStatusResponse(
                List.of(new NativeDbLocksMechanismStatusResponse("art-1",
                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of("art-2", "art-3"));

        String json = mapper.writeValueAsString(cluster);

        assertTrue(json.contains("\"unreachableNodeIds\":[\"art-2\",\"art-3\"]"));
        ClusterNativeDbLocksMechanismStatusResponse deserialized =
                mapper.readValue(json, ClusterNativeDbLocksMechanismStatusResponse.class);
        assertEquals(List.of("art-2", "art-3"), deserialized.getUnreachableNodeIds());
    }

    @Test
    void clusterNativeStatusResponseCoercesNullUnreachableNodeIdsToEmptyList() {
        ClusterNativeDbLocksMechanismStatusResponse response =
                new ClusterNativeDbLocksMechanismStatusResponse(List.of(), null);

        assertNotNull(response.getUnreachableNodeIds());
        assertTrue(response.getUnreachableNodeIds().isEmpty());
    }

    @Test
    void jacksonRoundTripPreservesAllFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        LockEntryResponse lock = new LockEntryResponse("cat", "k", 1000L, 42L, "my-thread");
        NodeLocksResponse node = new NodeLocksResponse("node-1", "ok", List.of(lock));
        LocksResponseMeta meta = new LocksResponseMeta("2026-06-18T00:00:00Z", "art-1",
                new LocksResponseMeta.FanOutMeta(true, 2, 1));
        LocksResponseSummary summary = new LocksResponseSummary(3, 2, 1);
        LocksResponseWarning warning = new LocksResponseWarning(
                LocksResponseWarning.CODE_HA_AUTH_FAN_OUT_SKIPPED, "fan-out skipped");
        ClusterLocksResponse cluster = new ClusterLocksResponse(meta, summary, List.of(warning), List.of(node));

        String json = mapper.writeValueAsString(cluster);
        ClusterLocksResponse deserialized = mapper.readValue(json, ClusterLocksResponse.class);

        assertEquals(1, deserialized.getNodes().size());
        NodeLocksResponse dNode = deserialized.getNodes().get(0);
        assertEquals("node-1", dNode.getNodeId());
        assertEquals(1, dNode.getLocks().size());
        LockEntryResponse dLock = dNode.getLocks().get(0);
        assertEquals("cat", dLock.getCategory());
        assertEquals(1000L, dLock.getLockedSinceEpochMs());
        assertEquals(42L, dLock.getThreadId());
        assertEquals("my-thread", dLock.getThreadName());

        assertEquals(1, deserialized.getWarnings().size());
        assertEquals(LocksResponseWarning.CODE_HA_AUTH_FAN_OUT_SKIPPED, deserialized.getWarnings().get(0).getCode());

        assertEquals(3, deserialized.getSummary().getTotalHeldLocks());
        assertEquals(2, deserialized.getSummary().getNodesOk());
        assertEquals(1, deserialized.getSummary().getNodesUnreachable());

        assertEquals("art-1", deserialized.getMeta().getQueriedByNodeId());
        assertEquals(2, deserialized.getMeta().getFanOut().getResponded());
    }

    @Test
    void jacksonRoundTrip_warningsEmptyAndOmittedFromJson_whenNoWarnings() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LocksResponseMeta meta = new LocksResponseMeta("2026-06-18T00:00:00Z", "art-1",
                new LocksResponseMeta.FanOutMeta(false, 0, 0));
        ClusterLocksResponse cluster = new ClusterLocksResponse(meta,
                new LocksResponseSummary(0, 1, 0), List.of(), List.of());

        String json = mapper.writeValueAsString(cluster);
        assertTrue(!json.contains("\"warnings\""), "warnings field must be absent from JSON when empty");
    }

    private static void assertFieldContract(Class<?> clazz, String modelName) {
        Set<String> expectedNames = EXPECTED_FIELDS.get(modelName);
        assertNotNull(expectedNames, "No expected fields registered for " + modelName);

        Set<String> actualJsonNames = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(JsonProperty.class))
                .map(f -> f.getAnnotation(JsonProperty.class).value())
                .collect(Collectors.toSet());

        assertEquals(expectedNames.size(), actualJsonNames.size(),
                modelName + " field count drift: a @JsonProperty field was added or removed. " +
                "Update EXPECTED_FIELDS in " + DbLocksRestContractTest.class.getSimpleName() +
                " to acknowledge the change. " +
                "Expected: " + expectedNames + " — Actual: " + actualJsonNames);

        for (String expected : expectedNames) {
            assertTrue(actualJsonNames.contains(expected),
                    modelName + ": expected @JsonProperty(\"" + expected + "\") but it was not found. " +
                    "This is likely a renaming — renaming is a breaking change for JSON consumers.");
        }
    }

    private static Method findMethod(Class<?> clazz, String name) {
        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
