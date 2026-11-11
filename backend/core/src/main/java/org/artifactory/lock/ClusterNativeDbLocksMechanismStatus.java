package org.artifactory.lock;

import java.util.List;

/**
 * Cluster-wide native DB locks mechanism status resolved on the querying node.
 */
public record ClusterNativeDbLocksMechanismStatus(
        List<NodeStatus> nodes,
        List<String> unreachableNodeIds) {

    public boolean allPeersResponded() {
        return unreachableNodeIds.isEmpty();
    }

    public boolean allReadyNotInUse() {
        return allPeersResponded() && nodes.stream()
                .allMatch(node -> node.status() == NativeDbLocksMechanismStatus.READY_NOT_IN_USE);
    }

    public record NodeStatus(String nodeId, NativeDbLocksMechanismStatus status) {
    }
}
