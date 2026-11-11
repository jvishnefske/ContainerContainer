package org.artifactory.lock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterNativeDbLocksMechanismStatusTest {

    @Test
    void allPeersRespondedTrueWhenUnreachableListEmpty() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of());

        assertTrue(status.allPeersResponded());
    }

    @Test
    void allPeersRespondedFalseWhenUnreachablePeersPresent() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(),
                List.of("art-2"));

        assertFalse(status.allPeersResponded());
    }

    @Test
    void allReadyNotInUseTrueWhenAllPeersReady() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of());

        assertTrue(status.allReadyNotInUse());
    }

    @Test
    void allReadyNotInUseFalseWhenAnyPeerNotReady() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                NativeDbLocksMechanismStatus.IN_USE)),
                List.of());

        assertFalse(status.allReadyNotInUse());
    }

    @Test
    void allReadyNotInUseFalseWhenUnreachablePeersPresentEvenIfLocalReady() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                        NativeDbLocksMechanismStatus.READY_NOT_IN_USE)),
                List.of("art-2"));

        assertFalse(status.allReadyNotInUse());
        assertFalse(status.allPeersResponded());
    }

    @Test
    void allReadyNotInUseFalseWhenAnyNodeSupportedNotReady() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                                NativeDbLocksMechanismStatus.READY_NOT_IN_USE),
                        new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-2",
                                NativeDbLocksMechanismStatus.SUPPORTED_NOT_READY)),
                List.of());

        assertTrue(status.allPeersResponded());
        assertFalse(status.allReadyNotInUse());
    }

    @Test
    void allReadyNotInUseFalseWhenAnyNodeNotSupported() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(new ClusterNativeDbLocksMechanismStatus.NodeStatus("art-1",
                        NativeDbLocksMechanismStatus.NOT_SUPPORTED)),
                List.of());

        assertFalse(status.allReadyNotInUse());
    }

    @Test
    void allReadyNotInUseTrueWhenNodesEmptyAndNoUnreachable() {
        ClusterNativeDbLocksMechanismStatus status = new ClusterNativeDbLocksMechanismStatus(
                List.of(), List.of());

        assertTrue(status.allPeersResponded());
        assertTrue(status.allReadyNotInUse());
    }
}
