package org.artifactory.queue.services;

import jakarta.annotation.Nullable;
import lombok.NonNull;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.repo.event.EventInfo;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.EMPTY;

/**
 * A global persistent queue implementation that retrieves all events, regardless of the repository keys involved.
 * @author Mor Merhav
 */
public class GlobalPersistentQueueImpl extends PersistentQueueImpl {

    public GlobalPersistentQueueImpl(QueueConfiguration queueConfiguration, String globalKey) {
        super(queueConfiguration, globalKey);
    }

    /**
     * Skips a repository key and a path-suffix (directory path + filename),
     * so the queue handler retrieves all events regardless of
     * repositories where they have occurred.
     */
    @NonNull
    @Override
    protected List<EventInfo> getEventsAfter(
            @NonNull PersistentQueueRunningContext context,
            @Nullable String fileSuffix,
            int maxBulkSize) {
        long eventId = context.getStartingMarker();
        String cursorType = queueConfiguration.getCursorType().name();
        return eventsService.getEventsAfter(eventId, EMPTY, EMPTY, maxBulkSize, cursorType);
    }

    /**
     * Requires to trigger this queue regardless of which repository keys
     * are involved into the "shift events" operation.
     * <p>
     * Makes this queue "global" together with the {@link #getEventsAfter} implementation.
     */
    @Override
    public boolean shouldAlwaysTrigger() {
        return true;
    }
}