package org.artifactory.storage.db.event.error.service;

import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.repo.event.EventInfo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Mor Merhav
 */
public final class ErrorInfoConverter {

    private ErrorInfoConverter() {
    }

    public static NodeEventsErrorInfo toErrorInfo(NodeEventsErrorsDBO record) {
        return NodeEventsErrorInfo.builder()
                .eventCategory(record.getEventCategory())
                .eventType(EventType.fromCode(record.getEventType()))
                .path(RepoPathFactory.create(record.getPath()))
                .operationKey(record.getOperationKey())
                .eventTime(record.getEventTime())
                .eventId(record.getEventId())
                .errorCount(record.getErrorCount())
                .errorMessage(record.getErrorMessage())
                .firstErrorTime(record.getFirstErrorTime())
                .lastErrorTime(record.getLastErrorTime())
                .build();
    }

    public static NodeEventsErrorsDBO toDBO(long errorId, NodeEventsErrorInfo errorInfo) {
        return NodeEventsErrorsDBO.builder()
                .errorId(errorId)
                .eventCategory(errorInfo.getEventCategory())
                .eventType(errorInfo.getEventType().code())
                .path(errorInfo.getPath().toPath())
                .operationKey(errorInfo.getOperationKey())
                .eventTime(errorInfo.getEventTime())
                .eventId(errorInfo.getEventId())
                .errorCount(errorInfo.getErrorCount())
                .errorMessage(errorInfo.getErrorMessage())
                .firstErrorTime(errorInfo.getFirstErrorTime())
                .lastErrorTime(errorInfo.getLastErrorTime())
                .build();
    }


    public static List<EventInfo> toEventInfos(List<NodeEventsErrorInfo> errors) {
        return errors.stream().map(ErrorInfoConverter::toEventInfo).collect(Collectors.toList());
    }

    private static EventInfo toEventInfo(NodeEventsErrorInfo errorInfo) {
        return EventInfo.builder()
                .path(errorInfo.getPath().toPath())
                .timestamp(errorInfo.getEventTime())
                .type(errorInfo.getEventType())
                .eventId(errorInfo.getEventId() != null ? errorInfo.getEventId() : -1)
                .build();
    }
}
