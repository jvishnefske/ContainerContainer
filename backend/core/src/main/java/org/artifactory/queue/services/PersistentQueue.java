package org.artifactory.queue.services;

import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueOperations;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.model.NodeEventCursor;

public interface PersistentQueue extends QueueOperations {

    /**
     * entry point for the real work of the queue is done here.
     */
    TriggerResult trigger();

    /**
     * signal queue to shut down, stop all running jobs
     */
    void shutdown();

    String getName();

    String getRepoKey();

    QueueConfiguration getQueueConfiguration();

    NodeEventCursor getCurrentMarkerCursorForQueue();

    /**
     * reset queue marker to given event id
     *
     * @param repoKey - the repo key
     * @param id      the marker will be set to this id
     */
    void reset(String repoKey, long id);

    /**
     * 1. cleanup existing errors
     * 2. reset queue marker to last event
     * OR
     * delete marker related to the queue
     *
     * @param delete  true - delete marker, false - set to last event.
     */
    void reset(boolean delete);

    /**
     * Create cursor marker if not exists
     *
     * @return true if marker was created
     */
    boolean createMarkerIfNotExists();

    /**
     * Create the cursor with the marker of the cursorToCopy param
     *
     * @param cursorToCopy cursor to copy
     */
    void copyCursor(String cursorToCopy, QueueCursorType queueCursorType);

    /**
     * mark queue with provided status
     */
    void updateStatus(NodeEventCursorStatus status);

    /**
     * mark queue with provided status
     */
    void updateFromStatusToStatus(NodeEventCursorStatus sourceStatus, NodeEventCursorStatus targetStatus);

    /**
     * Checks if the queue need to be always triggered not depending on specific operation.
     * If so, set this to return true, otherwise (in most cases) this will return false.
     * See example of shift events:
     * This will trigger the queues of the repos that were involved with shift events operation, however,
     * Federation service queue requires to be triggered always since providing all the events related to federation
     * to the federation service, therefore, it's a special use case for setting as true.
     *
     * @return true if this queue need to be triggered always not depending on specific operation.
     * false otherwise
     */
    boolean shouldAlwaysTrigger();

    enum TriggerResult {
        /**
         * All events were processed successfully
         */
        FINISHED_ALL,
        /**
         * Events were process and finished with a handled QueueEventException
         * we can do more retires on this status because backoffStrategy can prevent infinite loops
         */
        FINISHED_WITH_HANDLED_ERROR,
        /**
         * Events were processed and finished with an unexpected error
         * we will not create retires for this status because there is no backoffStrategy to prevent infinite loops
         */
        FINISHED_WITH_UNHANDLED_ERROR,
        /**
         * no events found
         */
        NOTHING_TO_DO,
        /**
         * queue was shutdown or paused
         */
        NOT_AVAILABLE
    }
}
