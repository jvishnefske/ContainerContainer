package org.artifactory.unifiedevent.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

class JfbusTopicConfigTest {

    /**
     * Guards against two enum entries sharing the same entityType with different partitionCount.
     * If that happened, entityTypePartitions() would silently pick whichever entry was first
     * (putIfAbsent), and createTopic would register the wrong partition count for the later entry.
     */
    @Test
    void noTwoEntriesShareSameEntityTypeWithDifferentPartitionCount() {
        Map<String, Integer> seen = new HashMap<>();
        for (JfbusTopicConfig config : JfbusTopicConfig.values()) {
            String entityType = config.getEntityType();
            if (entityType == null) {
                continue;
            }
            Integer existing = seen.get(entityType);
            if (existing != null && !existing.equals(config.getPartitionCount())) {
                fail("entityType '" + entityType + "' in " + config.name()
                        + " conflicts: partitionCount " + existing + " vs " + config.getPartitionCount());
            }
            seen.put(entityType, config.getPartitionCount());
        }
    }
}
