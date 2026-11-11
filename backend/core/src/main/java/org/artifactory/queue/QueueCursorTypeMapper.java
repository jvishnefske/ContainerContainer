package org.artifactory.queue;

import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;

import java.util.EnumMap;

/**
 * Two direction mapping utility between {@link NodeEventCursorType} and {@link QueueCursorType}
 */
public abstract class QueueCursorTypeMapper {

    private static final EnumMap<NodeEventCursorType, QueueCursorType> Node2QueueCursors =
            new EnumMap<>(NodeEventCursorType.class);
    private static final EnumMap<QueueCursorType, NodeEventCursorType> Queue2NodeCursors =
            new EnumMap<>(QueueCursorType.class);

    static {
        Queue2NodeCursors.put(QueueCursorType.FEDERATED_REPLICATION, NodeEventCursorType.FEDERATED_REPLICATION);
        Queue2NodeCursors.put(QueueCursorType.INCREMENTAL_INDEX, NodeEventCursorType.INCREMENTAL_INDEX);
        Queue2NodeCursors.put(QueueCursorType.GC, NodeEventCursorType.GC);
        Queue2NodeCursors.put(QueueCursorType.FEDERATION_SERVICE, NodeEventCursorType.FEDERATION_SERVICE);
        Queue2NodeCursors.put(QueueCursorType.EVIDENCE, NodeEventCursorType.EVIDENCE);
        Queue2NodeCursors.forEach((queue, node) -> Node2QueueCursors.put(node, queue));
    }

    private QueueCursorTypeMapper() {
    }

    public static NodeEventCursorType get(QueueCursorType queueCursorType) {
        return Queue2NodeCursors.get(queueCursorType);
    }

    public static QueueCursorType get(NodeEventCursorType queueCursorType) {
        return Node2QueueCursors.get(queueCursorType);
    }
}
