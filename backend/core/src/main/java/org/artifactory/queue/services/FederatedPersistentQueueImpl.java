package org.artifactory.queue.services;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.model.NodeEventCursor;

import static org.artifactory.common.ConstantValues.federatedRepoAsyncFullSyncEnabled;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.EXHAUSTED;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.HEALTHY;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.PENDING_FS;

/**
 * @author Tamar Rosen
 */
@Slf4j
public class FederatedPersistentQueueImpl extends PersistentQueueImpl implements FederatedPersistentQueue {

    public FederatedPersistentQueueImpl(QueueConfiguration queueConfiguration, String repoKey) {
        super(queueConfiguration, repoKey);
    }

    @Override
    public boolean isStatus(NodeEventCursorStatus status) {
        NodeEventCursor nodeEventCursor = getCurrentMarkerCursorForQueue();
        log.debug("Cursor '{}' returned: {}", cursorName, nodeEventCursor.getStatus());
        return status.equals(nodeEventCursor.getStatus());
    }

    @Override
    public void recoverExhausted() {
        log.info("Attempting recovery for exhausted queue: '{}'", getName());
        persistentQueueErrorService.triggerRecovery(this);
        resetStatusIfEquals(EXHAUSTED);
    }

    @Override
    public void resetStatusIfEquals(NodeEventCursorStatus sourceStatus) {
        updateFromStatusToStatus(sourceStatus, HEALTHY);
        triggeringService.triggerQueues(repoKey, queueConfiguration.getCursorType());
    }

    @Override
    public void resetStatus() {
        updateStatus(HEALTHY);
        triggeringService.triggerQueues(repoKey, queueConfiguration.getCursorType());
    }

    @Override
    public boolean isPaused() {
        if (federatedRepoAsyncFullSyncEnabled.getBoolean()) {
            return !isStatus(HEALTHY);
        }
        return !isStatus(HEALTHY) && !isStatus(PENDING_FS);
    }
}
