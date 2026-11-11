package org.artifactory.storage.db.event.service;


import lombok.NonNull;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.model.NodeEventCursor;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author Uriah Levy
 * A bussiness service that manages the node events cursor table. This service
 * keeps track and updates an indepedant cursor for the different event-based operations,
 * based on a unique operation identifier provided by the caller.
 */
public interface NodeEventCursorService {

    /**
     * Insert a new event operator cursor
     *
     * @param operationId the operation Id of the new cursor
     */
    void insertCursor(String operationId, long marker, NodeEventCursorType nodeEventCursorType);

    /**
     * Insert a new event operator cursor
     *
     */
    void insertCursor(@Nonnull NodeEventCursor nodeEventCursor);

    /**
     * Update the current cursor position.
     *
     * @param eventCursor the {@link NodeEventCursor} to set
     */
    void updateOrInsertCursor(NodeEventCursor eventCursor);

    /**
     * Get the current {@link NodeEventCursor}
     *
     * @param operationId the identifier of the event operation (metadata, replication, etc)
     * @return the current cursor position.
     */
    NodeEventCursor cursorForOperator(String operationId);

    void deleteCursor(String operationId);

    long oldestTimestampForEventLogOperator();

    /**
     * Update the cursor by ID
     */
    void updateCursor(String operatorId, long marker);

    /**
     * Get all cursors by status
     *
     * @param status the status of cursor(synced, out_of_synced)
     * @return List of cursors with the same status
     */
    List<NodeEventCursor> getAllCursorsByStatus(@Nonnull NodeEventCursorStatus status,
            @Nonnull NodeEventCursorType type);

    /**
     * Delete node event cursors
     * Such cursors are Federated, Out Of Sync, and have no matching Repository Config key
     * @return number of deleted Cursors
     */
    int deleteOrphanCursors();

    /**
     * Get all cursors that using event id and less updated than timestamp
     *
     * @param status the status of cursor(synced, out_of_synced)
     * @return List of cursors
     */
    List<NodeEventCursor> getEventIdCursorsBy(@Nonnull NodeEventCursorStatus status, long timestamp);

    /**
     * change status for cursor
     *
     * @param status update cursor to status
     */
    void updateCursorWithStatus(@Nonnull String cursorOpId, @Nonnull NodeEventCursorStatus status);

    /**
     * Updates the cursor to the specified targetStatus only if the current status matches the sourceStatus.
     *
     * @param sourceStatus The status to update the cursor from.
     * @param targetStatus The target status to update the cursor to.
     */
    int updateCursorFromStatusToStatus(@NonNull String cursorName, @NonNull NodeEventCursorStatus sourceStatus,
            @NonNull NodeEventCursorStatus targetStatus);

    /**
     * update the marker and last updated of current timestamp
     */
    void updateCursorMarker(@Nonnull String cursorName, long marker);

    /**
     * Updates the timestamp to 'now' of the selected cursor
     *
     * @param cursorName the cursor name
     */
    void updateCursorLastUpdated(String cursorName);

    /**
     * Delete all federated replication cursors that belong to a specific repository.
     * This method deletes cursors where the operator_id starts with the repoKey prefix
     * and the type is FEDERATED_REPLICATION.
     *
     * @param repoKey the repository key
     * @return the number of deleted cursors
     */
    int deleteFederatedCursorsForRepo(@Nonnull String repoKey);

    /**
     * Get all cursors matching any of the given types.
     *
     * @param types the cursor types to filter by
     * @return list of cursors matching any of the given types
     */
    List<NodeEventCursor> getAllByTypes(@Nonnull List<NodeEventCursorType> types);
}