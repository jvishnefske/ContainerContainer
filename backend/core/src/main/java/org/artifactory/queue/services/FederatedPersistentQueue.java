package org.artifactory.queue.services;

import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;

/**
 * @author Tamar Rosen
 */
public interface FederatedPersistentQueue extends PersistentQueue {
    void resetStatusIfEquals(NodeEventCursorStatus sourceStatus);

    boolean isStatus(NodeEventCursorStatus status);

    void recoverExhausted();
}
