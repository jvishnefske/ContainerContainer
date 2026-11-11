package org.artifactory.event.work;

import org.artifactory.event.priority.service.model.PrioritizedNodeEvent;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;

import java.util.List;

/**
 * @author Mor Merhav
 */
public interface NodeEventsErrorService {

    /**
     * Handles a node event error, inserting a new one if does not exists, updating error count, error message and
     * last error time otherwise
     *
     * @param eventCategory the error event category
     * @param event         the failed prioritized event
     * @param e             the occurred exception
     */
    void process(String eventCategory, PrioritizedNodeEvent event, Exception e);

    /**
     * Retrieves existing errors while applying the backoff strategy
     *
     * @param eventCategory   the error event category
     * @param operatorId      the operator ID for which the error occurred
     * @param backOffStrategy the strategy which calculates backoff delay for the errors
     * @return list of existing node events errors, filtered by backoff strategy
     */
    List<NodeEventsErrorInfo> getErrors(String eventCategory, String operatorId,
            NodeEventsErrorsBackOffStrategy backOffStrategy, int batchSize);

    /**
     * Remove error (if exists) that correlates with the given category and event
     *
     * @param eventCategory the event category for which the error correlates to
     * @param event         the event for which correlated error should be removed
     */
    void removeError(String eventCategory, PrioritizedNodeEvent event);
}
