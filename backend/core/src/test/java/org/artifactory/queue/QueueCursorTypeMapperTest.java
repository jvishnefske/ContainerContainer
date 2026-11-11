package org.artifactory.queue;

import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Alexei Vainshtein
 */
class QueueCursorTypeMapperTest {

    @Test
    void testMapper() {
        Assertions.assertEquals(NodeEventCursorType.FEDERATION_SERVICE,
                QueueCursorTypeMapper.get(QueueCursorType.FEDERATION_SERVICE));
        Assertions.assertEquals(QueueCursorType.FEDERATION_SERVICE,
                QueueCursorTypeMapper.get(NodeEventCursorType.FEDERATION_SERVICE));
    }

}