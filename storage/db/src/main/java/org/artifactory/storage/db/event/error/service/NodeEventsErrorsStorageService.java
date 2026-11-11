package org.artifactory.storage.db.event.error.service;

import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.model.RepoAndCategory;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service to persist and retrieve node events errors
 *
 * @author Mor Merhav
 */
public interface NodeEventsErrorsStorageService {

    /**
     * Persist node events error
     *
     * @param errorInfo The node events error information
     */
    void add(NodeEventsErrorInfo errorInfo);

    /**
     * Retrieve all existing node events errors while maintaining their order by event time and then error ID
     *
     * @param eventCategory the category which this node events error belongs to
     * @return all existing node events errors
     */
    List<NodeEventsErrorInfo> getErrors(String eventCategory);

    /**
     * Retrieve all existing node events errors for a specific category and operation key.
     * Results are ordered by event time and error ID.
     *
     * @param eventCategory The category of the node events errors.
     * @param operationKey The operation key to filter errors by.
     * @param batchSize The maximum number of errors to retrieve.
     * @return A list of node events errors.
     */
    List<NodeEventsErrorInfo> getErrors(String eventCategory, String operationKey, Integer batchSize);

    /**
     * Retrieve all existing node events errors while maintaining their order filtered by the given operation key
     *
     * @param eventCategory the category which this node events error belongs to
     * @param operationKey  the operation key to filter errors by
     * @return all existing node events errors
     */
    @Nonnull
    List<NodeEventsErrorInfo> getErrors(String eventCategory, String operationKey);

    /**
     * Retrieves the error record from the db with the given type, path and event category
     *
     * @param eventCategory the category which this node events error belongs to
     * @param eventType     error type
     * @param eventPath     error artifact path
     * @return the error object or null if one doesn't exist
     */
    Optional<NodeEventsErrorInfo> getError(String eventCategory, EventType eventType, String eventPath);

    /**
     * Retrieves the error record from the db with the given type, path, event category and operation key
     *
     * @param eventCategory the category which this node events error belongs to
     * @param eventType     error type
     * @param eventPath     error artifact path
     * @param operationKey  the unique operation key of the event
     * @return the error object or null if one doesn't exist
     */
    Optional<NodeEventsErrorInfo> getError(String eventCategory, EventType eventType, String eventPath,
            String operationKey);


    /**
     * delete error by id
     *
     * @param errorId - unique error id
     * @return true if succeeded, false otherwise
     */
    boolean delete(long errorId);

    /**
     * Delete unique node events error
     *
     * @param errorInfo the error info of the node events error to delete
     * @return true if succeeded, false otherwise
     */
    boolean delete(NodeEventsErrorInfo errorInfo);

    /**
     * Delete unique node events error
     *
     * @param eventType     the type of the event
     * @param eventPath     the event RepoPath from the source repository
     * @param eventCategory the category which this node events error belongs to
     * @return true if succeeded, false otherwise
     */
    boolean delete(String eventCategory, EventType eventType, String eventPath);

    /**
     * Delete unique node events error
     *
     * @param eventType     the type of the event
     * @param eventPath     the event RepoPath from the source repository
     * @param eventCategory the category which this node events error belongs to
     * @param operationKey  the unique operation key combining both source and target information
     * @return true if succeeded, false otherwise
     */
    boolean delete(String eventCategory, EventType eventType, String eventPath, String operationKey);

    /**
     * Delete all node events errors that match a specific operation-key
     *
     * @param eventCategory the category which this node events error belongs to
     * @param operationKey  the operation key whose errors should be deleted
     * @return number of deleted node events errors
     */
    int deleteAll(String eventCategory, String operationKey);

    /**
     * Delete all node events errors that match a specific operation-key and are from the given type
     *
     * @param eventCategory the category which this node events error belongs to
     * @param eventType     the type of the originating event
     * @param operationKey  the operation key whose errors should be deleted
     * @return number of deleted node events errors
     */
    int deleteAll(String eventCategory, EventType eventType, String operationKey);

    /**
     * Updates an existing error (same type, path, event category and (optionally) operation-key) with the given new
     * error (updating its message, retry count and last error time)
     *
     * @param errorInfo the error to update
     * @return the number of rows affected
     */
    int update(NodeEventsErrorInfo errorInfo);

    /**
     * Updates an existing error (by Id)
     * with the given new error (updating its message, retry count and last error time)
     *
     * @param errorInfo the error to update
     * @param errorId the id of the error to update
     * @return the number of rows affected
     */
    int updateById(NodeEventsErrorInfo errorInfo, long errorId);

    /**
     * Delete all errors with event id before the endExclusive
     * @param endExclusive end event_time exclusive
     * @return count of deleted
     */
    int deleteBeforeWithEventId(long endExclusive);

    /**
     * get the timestamp of the first event error with event_id
     */
    long getFirstEventErrorTimestampWithEventId();

    /**
     * Return set of erroneous repos
     */
    @Nonnull
    Set<RepoAndCategory> getErroneousRepos();
}

