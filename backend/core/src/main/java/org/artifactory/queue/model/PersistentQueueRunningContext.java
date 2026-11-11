package org.artifactory.queue.model;

import lombok.Data;
import org.artifactory.queue.QueueEventImpl;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * holds the data collected in a specific run
 * @author avishayh
 */
@Data
public class PersistentQueueRunningContext {
    /**
     * the marker_id in the beginning of the run
     */
    long startingMarker;
    /**
     * the last marker_id fetched in this run
     * marker is taken from the last fetched events from the db
     */
    long lastMarker;

    /**
     * the events handled by this run
     */
    List<QueueEventImpl> events;
    /**
     * a map (eventId -> error) of existing errors in the beginning of the run
     */
    Map<Long, EventAndErrorInfo> existingErrors;
    /**
     * all failed results of this run
     */
    Set<PersistentQueueRunningResult> failedResults;
    /**
     * the exhausted error ids
     */
    Set<Long> exhaustedErrors = new HashSet<>();
    /**
     * last updated of the marker
     */
    Long cursorsLastUpdated;
}
