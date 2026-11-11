package org.artifactory.storage.db.event.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;

/**
 * @author Uriah Levy
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NodeEventCursor {
    private String operatorId;
    private Long eventMarker;
    private NodeEventCursorType type;
    private NodeEventCursorStatus status;
    private Long lastUpdated;

    public NodeEventCursor(@NonNull String operatorId, @NonNull Long eventMarker, @NonNull NodeEventCursorType type){
        this.operatorId = operatorId;
        this.eventMarker = eventMarker;
        this.type = type;
    }

}