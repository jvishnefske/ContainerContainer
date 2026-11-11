package org.artifactory.storage.db.event.service;

import com.google.common.base.Splitter;
import lombok.NonNull;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.event.dao.NodeEventsCursorDao;
import org.artifactory.storage.db.event.entity.DbNodeEventCursor;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.mapper.NodeEventCursorMapper;
import org.artifactory.storage.db.event.service.mapper.NodeEventCursorMapperImpl;
import org.jfrog.common.ClockUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Uriah Levy
 */
@Service
public class NodeEventCursorServiceImpl implements NodeEventCursorService {
    private static final Logger log = LoggerFactory.getLogger(NodeEventCursorServiceImpl.class);

    private NodeEventsCursorDao nodeEventsCursorDao;
    private EventsDao eventsDao;
    private NodeEventCursorMapper nodeEventCursorMapper;
    @Autowired
    NodeEventCursorServiceImpl() {
        this.nodeEventCursorMapper = new NodeEventCursorMapperImpl();
    }

    @Autowired
    public void setNodeEventsCursorDao(NodeEventsCursorDao nodeEventsCursorDao) {
        this.nodeEventsCursorDao = nodeEventsCursorDao;
    }

    @Autowired
    public void setEventsDao(EventsDao eventsDao) {
        this.eventsDao = eventsDao;
    }

    @Override
    public void updateOrInsertCursor(NodeEventCursor eventCursor) {
        try {
            log.debug("Updating cursor for operator '{}' with timestamp {}", eventCursor.getOperatorId(),
                    eventCursor.getEventMarker());
            DbNodeEventCursor dbCursor = nodeEventCursorMapper.nodeEventCursorToDbNodeEventCursor(eventCursor);
            int updateResult = nodeEventsCursorDao.update(dbCursor);
            if (updateResult == 0) {
                nodeEventsCursorDao.insert(dbCursor);
            }
        } catch (SQLException e) {
            throw new StorageException("Could not update cursor for operator with ID '" + eventCursor.getOperatorId() +
                    "'", e);
        }
    }

    @Override
    public void insertCursor(String operatorId, long marker, NodeEventCursorType nodeEventCursorType) {
        try {
            nodeEventsCursorDao.insert(nodeEventCursorMapper
                    .nodeEventCursorToDbNodeEventCursor(new NodeEventCursor(operatorId, marker, nodeEventCursorType)));
        } catch (SQLException e) {
            throw new StorageException("Could not insert cursor for operator with ID '" + operatorId + "'", e);
        }
    }

    @Override
    public void insertCursor(@NonNull NodeEventCursor nodeEventCursor) {
        try {
            nodeEventsCursorDao.insert(nodeEventCursorMapper
                    .nodeEventCursorToDbNodeEventCursor(nodeEventCursor));
        } catch (SQLException sqe) {
            throw new StorageException("Could not insert cursor for operator with ID '"
                    + nodeEventCursor.getOperatorId() + "'",
                    sqe
            );
        }
    }

    @Override
    public NodeEventCursor cursorForOperator(String operatorId) {
        try {
            Optional<DbNodeEventCursor> dbNodeEventCursorOptional = nodeEventsCursorDao.cursorForOperator(operatorId);
            return dbNodeEventCursorOptional
                    .map(nodeEventCursorMapper::dbNodeEventCursorToNodeEventCursor)
                    .orElse(null);
        } catch (SQLException e) {
            throw new StorageException("Unable to retrieve cursor for operator with ID " + operatorId, e);
        }
    }

    @Override
    public void deleteCursor(String operationId) {
        try {
            log.debug("Deleting cursor: {}", operationId);
            nodeEventsCursorDao.delete(operationId);
        } catch (SQLException e) {
            throw new StorageException("Unable to delete cursor with ID " + operationId, e);
        }
    }

    @Override
    public long oldestTimestampForEventLogOperator() {
        try {
            //cursor table holds marker which represent either timestamp or eventId
            //for timestamp types we just query the minimum timestamp from the cursor table
            //for event_id types we need to query the minimum event_id from the cursor table and than the timestamp
            //from node_events table
            //returning the minimum timestamp between the 2 of them.
            List<NodeEventCursorType> eventIds = NodeEventCursorType.getEventIdLogTypes();
            List<NodeEventCursorType> timestamps = NodeEventCursorType.getTimestampLogTypes();
            long oldestTimestamp = nodeEventsCursorDao.oldestMarkerForTypes(timestamps);
            long oldestEventId = nodeEventsCursorDao.oldestMarkerForTypes(eventIds);
            long oldestTimestampByEventId = eventsDao.getOldestTimestampByEventIdInclusive(oldestEventId);

            // protection if no by timestamp cursors
            if (oldestTimestamp > 0 && oldestTimestampByEventId > 0) {
                return Math.min(oldestTimestamp, oldestTimestampByEventId);
            } else if (oldestTimestamp > 0) {
                return oldestTimestamp;
            } else {
                return oldestTimestampByEventId;
            }
        } catch (SQLException e) {
            throw new StorageException("Error when trying to get oldest timestamp from event log", e);
        }
    }

    @Override
    public void updateCursor(String operatorId, long marker) {
        try {
            nodeEventsCursorDao.update(operatorId, marker);
        } catch (SQLException e) {
            throw new StorageException("Could not update cursor for operator with ID: " + operatorId, e);
        }
    }

    @Override
    public List<NodeEventCursor> getAllCursorsByStatus(@NonNull NodeEventCursorStatus status,
            @NonNull NodeEventCursorType type) {
        try {
            List<DbNodeEventCursor> nodeEventCursors = nodeEventsCursorDao.cursorForStatus(status,
                    type);
            return nodeEventCursors.stream()
                    .map(nodeEventCursorMapper::dbNodeEventCursorToNodeEventCursor)
                    .collect(Collectors.toList());
        } catch (SQLException sqe) {
            throw new StorageException("Error when trying to get all cursor by status: " + status, sqe);
        }
    }

    @Override
    public int deleteOrphanCursors(){
        List<DbNodeEventCursor> outOfSyncFederatedCursors;
        int eventualDeletions = 0;
        try {
            // cross-reference node events cursors whose repo key does not exist in the repository config
            // this is done in-memory to avoid a possible lock when performing join query on "repository_config"
            outOfSyncFederatedCursors = nodeEventsCursorDao.cursorForStatus(
                    NodeEventCursorStatus.OUT_OF_SYNC, NodeEventCursorType.FEDERATED_REPLICATION);
        } catch (SQLException sqe) {
            throw new StorageException("Error when trying to get all orphan cursors", sqe);
        }

        var repositoryConfigService = ContextHelper.get().getRepositoryConfigService();
        Set<String> orphanCursorsOperatorIds = outOfSyncFederatedCursors.stream().
                map(nodeEventCursorMapper::dbNodeEventCursorToNodeEventCursor).
                filter(nodeEventCursor -> !repositoryConfigService.existsByRepoKey(
                        this.splitCursorName(nodeEventCursor))).
                map(NodeEventCursor::getOperatorId).
                collect(Collectors.toSet());

        if (!orphanCursorsOperatorIds.isEmpty()) {
            log.debug("About to delete orphan {} cursors...", orphanCursorsOperatorIds.size());
        }
        for (String operatorId : orphanCursorsOperatorIds) {
            try {
                int deleted = nodeEventsCursorDao.delete(operatorId);
                if (deleted == 0) {
                    log.warn("{} was marked for deletion but was not deleted", operatorId);
                } else {
                    eventualDeletions++;
                    log.trace("Deleted orphan cursor {}", operatorId);
                }
            } catch (SQLException sq) {
                log.error("Error when trying to delete orphan cursor {}", operatorId);
            }
        }


        if (!orphanCursorsOperatorIds.isEmpty()) {
            log.info("Successfully deleted {}/{} orphan cursors", eventualDeletions, orphanCursorsOperatorIds.size());
        }
        return eventualDeletions;
    }

    private String splitCursorName(NodeEventCursor nodeEventCursor) {
        List<String> cursorNameSplitted = Splitter
                .on("-" + nodeEventCursor.getType().getName().toLowerCase())
                .splitToList(nodeEventCursor.getOperatorId());
        if (cursorNameSplitted.size() == 2) {
            return cursorNameSplitted.get(0);
        } else {
            log.error("Failed parse cursor name: {}", nodeEventCursor.getOperatorId());
            return null;
        }
    }

    @Override
    public List<NodeEventCursor> getEventIdCursorsBy(@NonNull NodeEventCursorStatus status,
            long timestamp) {
        try {
            List<DbNodeEventCursor> nodeEventCursors = nodeEventsCursorDao
                    .getCursorsBy(
                            NodeEventCursorType.getEventIdLogTypes(),
                            status,
                            timestamp
                    );
            return nodeEventCursors.stream()
                    .map(nodeEventCursorMapper::dbNodeEventCursorToNodeEventCursor)
                    .collect(Collectors.toList());
        } catch (SQLException sqe) {
            throw new StorageException("Error when trying to get old cursors", sqe);
        }
    }

    @Override
    public void updateCursorWithStatus(@NonNull String cursorName, @NonNull NodeEventCursorStatus status) {
        try {
            log.info("Marking queue '{}' as '{}'", cursorName, status.name());
            nodeEventsCursorDao.updateCursorsStatus(cursorName, status);
        } catch (SQLException sqe) {
            throw new StorageException("Failed to update cursor "
                    + cursorName + "with status "
                    + status
                    , sqe);
        }
    }

    @Override
    public int updateCursorFromStatusToStatus(@NonNull String cursorName, @NonNull NodeEventCursorStatus sourceStatus,
            @NonNull NodeEventCursorStatus targetStatus) {
        try {
            int rowsUpdated = nodeEventsCursorDao.updateWhereStatus(cursorName, sourceStatus, targetStatus);
            if (rowsUpdated > 0) {
                log.info("Marking queue '{}' as '{}'", cursorName, targetStatus.name());
            } else {
                log.debug("Unable to mark queue '{}' as '{}' - source status was already updated from: {})", cursorName,
                        targetStatus.name(),
                        sourceStatus.name());
            }
            return rowsUpdated;
        } catch (SQLException sqe) {
            throw new StorageException("Failed to update cursor "
                    + cursorName + " with source status "
                    + sourceStatus + "to target status "
                    + targetStatus, sqe);
        }
    }

    @Override
    public void updateCursorMarker(@NonNull String cursorName, long marker) {
        try {
            nodeEventsCursorDao.updateWithMarkerAndTimestamp(cursorName, marker, ClockUtils.epochMillis());
        } catch (SQLException sqe) {
            throw new StorageException("Failed to update cursor "
                    + cursorName + "with marker "
                    + marker
                    , sqe);
        }
    }

    @Override
    public List<NodeEventCursor> getAllByTypes(@NonNull List<NodeEventCursorType> types) {
        try {
            return nodeEventsCursorDao.getAllByTypes(types).stream()
                    .map(nodeEventCursorMapper::dbNodeEventCursorToNodeEventCursor)
                    .toList();
        } catch (SQLException e) {
            throw new StorageException("Unable to retrieve cursors for types " + types, e);
        }
    }

    @Override
    public void updateCursorLastUpdated(String cursorName) {
        try {
            log.debug("Updating cursorName: {} last_updated to 'now'", cursorName);
            nodeEventsCursorDao.updateTimestamp(cursorName, ClockUtils.epochMillis());
        } catch (SQLException e) {
            throw new StorageException("Failed to update cursor " + cursorName + " to latest timestamp");
        }
    }

    @Override
    public int deleteFederatedCursorsForRepo(@NonNull String repoKey) {
        log.info("Deleting federated replication cursors for repository: {}", repoKey);

        List<DbNodeEventCursor> cursorsToDelete;
        try {
            cursorsToDelete = nodeEventsCursorDao.cursorForRepoAndType(
                    repoKey, NodeEventCursorType.FEDERATED_REPLICATION);
        } catch (SQLException e) {
            log.warn("Failed to get federated cursors for repository: {}", repoKey, e);
            return 0;
        }

        int deleted = 0;
        for (DbNodeEventCursor cursor : cursorsToDelete) {
            try {
                deleted += nodeEventsCursorDao.delete(cursor.getOperatorId());
            } catch (SQLException e) {
                log.warn("Failed to delete cursor: {}", cursor.getOperatorId(), e);
            }
        }

        if (deleted > 0) {
            log.info("Successfully deleted {} federated cursors for repository: {}", deleted, repoKey);
        } else if (cursorsToDelete.isEmpty()) {
            log.debug("No federated cursors found to delete for repository: {}", repoKey);
        } else {
            log.warn("Failed to delete all {} federated cursors for repository: {}", cursorsToDelete.size(), repoKey);
        }
        return deleted;
    }
}