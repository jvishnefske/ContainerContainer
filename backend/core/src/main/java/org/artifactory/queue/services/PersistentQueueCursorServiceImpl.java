package org.artifactory.queue.services;

import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueStatus;
import org.artifactory.queue.QueueCursorTypeMapper;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class PersistentQueueCursorServiceImpl implements PersistentQueueCursorService {

    public static final String CREATE_CURSOR_CONFLICTS_GUARD = "create-cursor";
    private final NodeEventCursorService nodeEventCursorService;
    private final LoadingCache<String, Long> repoMinEventIdCache;
    private final ConflictsGuard<String> createCursorConflictsGuard;
    private final EventsService eventsService;
    private final Supplier<Long> lastEventCache;

    public PersistentQueueCursorServiceImpl(NodeEventCursorService nodeEventCursorService,
            EventsService eventsService, AddonsManager addonsManager) {
        this.nodeEventCursorService = nodeEventCursorService;
        this.eventsService = eventsService;
        this.repoMinEventIdCache = CacheBuilder.newBuilder()
                .expireAfterAccess(ConstantValues.persistentQueueRepoMinEventCacheExpireSec.getInt(),
                        TimeUnit.SECONDS)
                .concurrencyLevel(Runtime.getRuntime().availableProcessors())
                .build(new CacheLoader<>() {
                    @Override
                    public Long load(@Nonnull String repoKey) {
                        return eventsService.getFirstEventLogId(repoKey);
                    }
                });

        this.createCursorConflictsGuard =
                addonsManager.addonByType(HaAddon.class).getConflictsGuard(CREATE_CURSOR_CONFLICTS_GUARD);
        lastEventCache = Suppliers.memoizeWithExpiration(this::getLastEventIdFromService,
                ConstantValues.lastEventIdCacheTimeoutSec.getLong(),
                TimeUnit.SECONDS
        );
    }

    /**
     * Create marker if not exists. Protected by db lock.
     *
     * @param repoKey    the repo key of the marker
     * @param cursorName rhe cursor name
     * @param type       the cursor type
     * @return true if marker was created
     */
    @Override
    public boolean createMarkerIfNotExists(String repoKey, String cursorName, QueueCursorType type) {
        if (nodeEventCursorService.cursorForOperator(cursorName) == null) {
            try {
                if (createCursorConflictsGuard.tryToLock(cursorName, 10L, TimeUnit.SECONDS)) {
                    try {
                        if (nodeEventCursorService.cursorForOperator(cursorName) == null) {
                            createCursor(cursorName, type);
                            return true;
                        }
                    } finally {
                        createCursorConflictsGuard.unlock(cursorName);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to create cursor", e);
            }
        }
        return false;
    }

    /**
     * Create new cursor in the DB with the same properties
     */
    @Override
    public void copyCursor(String repoKey, String cursorName,
            NodeEventCursor oldNodeEventCursor) {
        try {
            if (nodeEventCursorService.cursorForOperator(cursorName) == null) {
                copyCursor(cursorName, oldNodeEventCursor);
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to create cursor", e);
        }
    }

    @Override
    public void updateCursorStatus(@Nonnull String cursorName, @Nonnull NodeEventCursorStatus status) {
        nodeEventCursorService.updateCursorWithStatus(cursorName, status);
    }

    @Override
    public void updateFromStatusToStatus(@Nonnull String cursorName, @Nonnull NodeEventCursorStatus sourceStatus,
            @Nonnull NodeEventCursorStatus targetStatus) {
        nodeEventCursorService.updateCursorFromStatusToStatus(cursorName, sourceStatus, targetStatus);
    }

    @Override
    public long getCachedLatestEventId() {
        return lastEventCache.get();
    }

    @Override
    public void updateCursor(@Nonnull String cursorName, long newMarker) {
        nodeEventCursorService.updateCursorMarker(cursorName, newMarker);
    }

    @Override
    @Transactional
    public void resetMarker(String cursorName, long eventId) {
        if (eventId == -1) {
            long lastEventLogId = eventsService.getLastEventLogId();
            log.trace("event id is -1, reset the {} to {}", cursorName, lastEventLogId);
            updateCursor(cursorName, lastEventLogId);
        } else {
            //event id was provided
            NodeEventCursor cursor = nodeEventCursorService.cursorForOperator(cursorName);
            if (cursor.getEventMarker() < eventId) {
                log.trace("event id is not -1, reset the {} to {}", cursorName, eventId);
                updateCursor(cursorName, eventId);
            }
            nodeEventCursorService.updateCursorLastUpdated(cursorName);
        }
    }

    @Override
    public void deleteMarker(String cursorName) {
        log.info("delete the cursor {}", cursorName);
        nodeEventCursorService.deleteCursor(cursorName);
    }

    @Override
    public NodeEventCursor getCursorAndCreateIfNeeded(@Nonnull String cursorName,
            @Nonnull QueueCursorType type) {
        NodeEventCursor cursor = nodeEventCursorService.cursorForOperator(cursorName);
        if (cursor == null) {
            //create cursor for 1st time
            return createCursor(cursorName, type);
        }
        return cursor;
    }

    private long getCleanerBoundary() {
        return new DateTime(ClockUtils.epochMillis())
                .minusDays(ConstantValues.ageOfEventsLogEntriesToDiscardDays.getInt())
                .getMillis();
    }

    private NodeEventCursor createCursor(String cursorName, QueueCursorType type) {
        NodeEventCursor nodeEventCursor = NodeEventCursor.builder()
                .operatorId(cursorName)
                .eventMarker(-1L)
                .type(QueueCursorTypeMapper.get(type))
                .status(type == QueueCursorType.FEDERATED_REPLICATION ? NodeEventCursorStatus.PENDING_FS : null)
                .lastUpdated(ClockUtils.epochMillis())
                .build();

        //to do decide how to save time -- advice
        nodeEventCursorService.insertCursor(nodeEventCursor);
        log.debug("Cursor {} created with marker -1", cursorName);
        return nodeEventCursor;
    }

    private void copyCursor(String cursorName, NodeEventCursor oldNodeEventCursor) {
        NodeEventCursor nodeEventCursor = NodeEventCursor.builder()
                .operatorId(cursorName)
                .eventMarker(oldNodeEventCursor.getEventMarker())
                .type(oldNodeEventCursor.getType())
                .status(oldNodeEventCursor.getStatus())
                .lastUpdated(ClockUtils.epochMillis())
                .build();

        nodeEventCursorService.insertCursor(nodeEventCursor);
        log.debug("Cursor copied from {} to {} successfully with marker {} and status {}",
                oldNodeEventCursor.getOperatorId(), nodeEventCursor.getOperatorId(),
                oldNodeEventCursor.getEventMarker(), oldNodeEventCursor.getStatus());
    }

    @Override
    public QueueStatus getStatus(String repoKey, String cursorName) {
        //this is the logic returned Status
        //no cursor exist                => N/A
        //marker=X && firstEvent<X => OK
        //marker=X && firstEvent=X => OK
        //marker=X && firstEvent=X+1 => OK
        //marker=X && firstEvent=X+2 => DataLost
        NodeEventCursor cursor = nodeEventCursorService.cursorForOperator(cursorName);
        if (cursor == null) {
            return QueueStatus.NOT_AVAILABLE;
        }
        long marker = cursor.getEventMarker();
        long minEventId = getRepoMinEventId(repoKey);

        if (marker == -1) {
            log.info("Found a new marker for {}", cursorName);
            return QueueStatus.NEW_MARKER;
        }

        if (marker + 1 < minEventId) {
            log.warn("Data Lost: {}, marker: {}, minEventId:{}", cursorName, marker, minEventId);
            return QueueStatus.DATA_LOST;
        } else {
            return QueueStatus.OK;
        }
    }

    private long getRepoMinEventId(@Nonnull String repoKey) {
        try {
            return repoMinEventIdCache.get(repoKey);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to get repo min eventId", e);
        }
    }

    private Long getLastEventIdFromService() {
        return eventsService.getLastEventLogId();
    }
}
