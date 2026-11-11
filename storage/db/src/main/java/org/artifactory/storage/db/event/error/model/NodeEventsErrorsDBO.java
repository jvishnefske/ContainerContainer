package org.artifactory.storage.db.event.error.model;


import lombok.Builder;
import lombok.Value;

/**
 * DB model for node_events_errors
 *
 * @author Mor Merhav
 */
@Value
@Builder(toBuilder = true, builderClassName = "Builder")
public class NodeEventsErrorsDBO {
    long errorId;
    String eventCategory;
    int eventType;
    String path;
    String operationKey;
    Long eventId;
    long eventTime;
    int errorCount;
    String errorMessage;
    long firstErrorTime;
    long lastErrorTime;
}
