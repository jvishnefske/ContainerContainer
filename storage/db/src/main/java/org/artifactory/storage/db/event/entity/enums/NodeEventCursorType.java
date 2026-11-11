package org.artifactory.storage.db.event.entity.enums;

import java.util.Arrays;
import java.util.List;

import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.MarkerType.EventId;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorType.MarkerType.Timestamp;

public enum NodeEventCursorType {

    INCREMENTAL_REPLICATION_PROGRESS(Timestamp, "INCREMENTAL_REPLICATION_PROGRESS"),
    INCREMENTAL_REPLICATION_START(Timestamp, "INCREMENTAL_REPLICATION_START"),
    INCREMENTAL_INDEX(EventId, "INCREMENTAL_INDEX"),
    GC(EventId, "GC", false),
    FEDERATED_REPLICATION(EventId, "FEDERATED_REPLICATION"),
    FEDERATION_SERVICE(EventId, "FEDERATION_SERVICE"),
    METADATA_MIGRATION(null, "METADATA_MIGRATION"),
    METADATA_EVENTS(Timestamp, "METADATA_EVENTS"),
    DELETE_MARKER(null, "DELETE_MARKER"),
    INCREMENTAL_REPLICATION_STATS_PROGRESS(null, "INCREMENTAL_REPLICATION_STATS_PROGRESS"),
    EVIDENCE(EventId, "EVIDENCE");

    /**
     * indicates if the type is part of eventLog (node_events) and the marker used is eventId or timestamp
     * should be null if cursor is not referencing a marker in the node_events table (e.g: DELETE_MARKER)
     * - Used when trying to clean up old events
     */
    private final MarkerType markerType;

    private final String name;
    private final boolean shouldIncludeInEventIdLogTypes;

    NodeEventCursorType(MarkerType markerType, String name, boolean shouldExcludeFromEventIdLogTypes) {
        this.shouldIncludeInEventIdLogTypes = shouldExcludeFromEventIdLogTypes;
        this.markerType = markerType;
        this.name = name;
    }

    NodeEventCursorType(MarkerType markerType, String name) {
        this(markerType, name, true);
    }

    public static NodeEventCursorType forName(String name) {
        for (NodeEventCursorType eventType : values()) {
            if (eventType.name.equals(name)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unable to find cursor type for name: " + name);
    }

    public static List<NodeEventCursorType> getTimestampLogTypes() {
        return Arrays.stream(values())
                .filter(NodeEventCursorType::isTimestampType)
                .toList();
    }

    public static List<NodeEventCursorType> getEventIdLogTypes() {
        return Arrays.stream(values())
                .filter(NodeEventCursorType::isEventIdType)
                .filter(v -> v.shouldIncludeInEventIdLogTypes)
                .toList();
    }

    public boolean isEventIdType() {
        return EventId.equals(markerType);
    }

    public boolean isTimestampType() {
        return Timestamp.equals(markerType);
    }

    public String getName() {
        return name;
    }

    enum MarkerType {
        EventId,      //use this when marker represents an eventId from node_events
        Timestamp,    //use this when marker represents a timestamp from node_events
    }
}


