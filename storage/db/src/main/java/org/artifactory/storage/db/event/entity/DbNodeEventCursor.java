package org.artifactory.storage.db.event.entity;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.jfrog.common.mapper.Validatable;

import jakarta.annotation.Nullable;

import static org.jfrog.common.ArgUtils.requireNonBlank;
import static org.jfrog.common.ArgUtils.requireNonNull;

/**
 * @author Uriah Levy
 */
@Data
@NoArgsConstructor
@Builder(toBuilder = true, builderClassName = "Builder")
public class DbNodeEventCursor implements Validatable {

    private String operatorId;
    private Long eventMarker;
    private NodeEventCursorType type;
    private NodeEventCursorStatus status;
    private Long lastUpdated;

    public DbNodeEventCursor(@NonNull String operatorId,
            @NonNull Long eventMarker,
            @NonNull NodeEventCursorType type,
            @Nullable NodeEventCursorStatus status,
            @Nullable Long lastUpdated) {
        validate(operatorId, eventMarker, type, status, lastUpdated);
    }

    @Override
    public void validate() {
        validate(operatorId, eventMarker, type, status, lastUpdated);
    }

    public void validate(@NonNull String operationId,
            @NonNull Long eventTimestamp,
            @NonNull NodeEventCursorType type,
            @Nullable NodeEventCursorStatus status,
            @Nullable Long lastUpdated) {
        this.operatorId = requireNonBlank(operationId, "Operation ID is required");
        this.eventMarker = requireNonNull(eventTimestamp, "Event Marker is required");
        this.type = requireNonNull(type, "Type is required");
        this.status = status;
        this.lastUpdated = lastUpdated;
    }
}
