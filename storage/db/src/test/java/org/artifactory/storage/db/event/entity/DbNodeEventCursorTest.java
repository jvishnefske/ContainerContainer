package org.artifactory.storage.db.event.entity;

import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.junit.jupiter.api.Test;

import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.FEDERATED_REPLICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Alexei Vainshtein
 */
class DbNodeEventCursorTest {

    @Test
    void testValidateWithNegativeMarker() {
        DbNodeEventCursor dbNodeEventCursor =
                new DbNodeEventCursor("operator",
                        Long.MIN_VALUE,
                        FEDERATED_REPLICATION,
                        NodeEventCursorStatus.HEALTHY,
                        1L);
        assertEquals(Long.MIN_VALUE, dbNodeEventCursor.getEventMarker());
        assertEquals(NodeEventCursorStatus.HEALTHY, dbNodeEventCursor.getStatus());
        assertEquals(1L, dbNodeEventCursor.getLastUpdated());
        DbNodeEventCursor dbNodeEventCursor2 =
                new DbNodeEventCursor("operator",
                        Long.MIN_VALUE,
                        FEDERATED_REPLICATION,
                        null,
                        null);
        assertNull(dbNodeEventCursor2.getStatus());
        assertNull(dbNodeEventCursor2.getLastUpdated());
    }
}