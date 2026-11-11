package org.artifactory.storage.db.event.error.factory;

import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;

import jakarta.annotation.Nullable;

/**
 * @author Mor Merhav
 * @author avishayh
 */
public final class NodeEventsErrorInfoFactory {

    private NodeEventsErrorInfoFactory() {
    }

    public static NodeEventsErrorInfo create(String eventCategory, EventType eventType, RepoPath repoPath,
            String operationKey, @Nullable Long eventId, long eventTime, int errorCount, String errMsg,
            long firstErrorTime, long lastErrorTime) {
        return NodeEventsErrorInfo.builder()
                .eventCategory(eventCategory)
                .errorCount(errorCount)
                .errorMessage(errMsg)
                .operationKey(operationKey)
                .firstErrorTime(firstErrorTime)
                .lastErrorTime(lastErrorTime)
                .eventType(eventType)
                .eventTime(eventTime)
                .eventId(eventId)
                .path(repoPath)
                .build();
    }

    public static EventAndErrorInfo createNew(EventInfo eventInfo, long currentTime) {
        return EventAndErrorInfo.builder()
                .eventInfo(eventInfo)
                .firstErrorTime(currentTime)
                .errorCount(1)
                .build();
    }

    public static EventAndErrorInfo updateExisting(EventAndErrorInfo error) {
        return EventAndErrorInfo.builder()
                .eventInfo(error.getEventInfo())
                .errorId(error.getErrorId())
                .firstErrorTime(error.getFirstErrorTime())
                .errorCount(error.getErrorCount() + 1)
                .build();
    }

    public static EventAndErrorInfo resetExisting(EventAndErrorInfo error) {
        return EventAndErrorInfo.builder()
                .eventInfo(error.getEventInfo())
                .errorId(error.getErrorId())
                .firstErrorTime(error.getFirstErrorTime())
                .errorCount(0)
                .build();
    }
}
