package org.artifactory.queue.services;

import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.model.NodeEventCursor;

import jakarta.annotation.Nonnull;

/**
 * A "bridge" service interface between Events services and PersistentQueue framework
 * contains all the methods needed to handle the cursor.
 *
 * @author avishayh
 */
public interface PersistentQueueCursorService {

    /**
     * will update cursors marker and last_updated
     */
    void updateCursor(@Nonnull String cursorName, long newMarker);

    void resetMarker(String cursorName, long eventId);

    void deleteMarker(String cursorName);

    /**
     * @param cursorName - name of cursor
     * @param type       - type of cursor if need to create one
     * @return the current marker (eventId) for a cursor, if cursor doesn't exist creates one with initial value.
     */
    NodeEventCursor getCursorAndCreateIfNeeded(@Nonnull String cursorName,
            @Nonnull QueueCursorType type);

    QueueStatus getStatus(String repoKey, String cursorName);

    /**
     * Create cursor marker if not exists
     *
     * @return true if marker was created
     */
    boolean createMarkerIfNotExists(String repoKey, String cursorName, QueueCursorType type);

    /**
     * Create new cursor in the DB with the same properties
     */
    void copyCursor(String repoKey, String cursorName, NodeEventCursor oldNodeEventCursor);

    void updateCursorStatus(@Nonnull String cursorName, @Nonnull NodeEventCursorStatus status);

    void updateFromStatusToStatus(@Nonnull String cursorName, @Nonnull NodeEventCursorStatus sourceStatus,
            @Nonnull NodeEventCursorStatus targetStatus);

    long getCachedLatestEventId();
}
