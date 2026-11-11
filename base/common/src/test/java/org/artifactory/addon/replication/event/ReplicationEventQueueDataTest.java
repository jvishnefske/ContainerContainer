package org.artifactory.addon.replication.event;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Tomer Slutsky
 */
class ReplicationEventQueueDataTest {

    @Test
    void isEmpty() {
        var data = new ReplicationEventQueueData("key", List.of());
        assertTrue(data.isEmpty());
        data = new ReplicationEventQueueData();
        assertTrue(data.isEmpty());
    }
}