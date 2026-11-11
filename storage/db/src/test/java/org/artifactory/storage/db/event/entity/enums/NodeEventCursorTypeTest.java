package org.artifactory.storage.db.event.entity.enums;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author TamirH
 */
class NodeEventCursorTypeTest {
    @Test
    void testFilterEventIdTypes() {
        final List<NodeEventCursorType> eventIdLogTypes = NodeEventCursorType.getEventIdLogTypes();
        Assertions.assertFalse(eventIdLogTypes.contains(NodeEventCursorType.GC));
        Assertions.assertTrue(eventIdLogTypes.contains(NodeEventCursorType.INCREMENTAL_INDEX));
        Assertions.assertTrue(eventIdLogTypes.contains(NodeEventCursorType.FEDERATED_REPLICATION));
        Assertions.assertTrue(eventIdLogTypes.contains(NodeEventCursorType.FEDERATION_SERVICE));
    }

    @Test
    void testFilterByTimestampDoesntContainsGC() {
        final List<NodeEventCursorType> eventIdLogTypes = NodeEventCursorType.getTimestampLogTypes();
        Assertions.assertFalse(eventIdLogTypes.contains(NodeEventCursorType.GC));
    }
}