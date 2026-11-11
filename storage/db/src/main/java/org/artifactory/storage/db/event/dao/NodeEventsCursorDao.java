package org.artifactory.storage.db.event.dao;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.event.entity.DbNodeEventCursor;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.event.cursor.RawMirrorStatusInfo;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Uriah Levy
 */
@Repository
@Slf4j
public class NodeEventsCursorDao {

    public static final String EVENTS_CURSOR_TABLE = "node_event_cursor";

    private final JdbcHelper jdbcHelper;
    private final SqlDaoHelper<DbNodeEventCursor> daoHelper;
    private DbProperties dbProperties;
    private static final String INSERT_CURSOR_SQL =
            "INSERT INTO " + EVENTS_CURSOR_TABLE + " (operator_id, event_marker, type, status, last_updated) " +
                    "VALUES(?, ?, ?, ?, ?)";
    private static final String UPDATE_CURSOR_SQL =
            "UPDATE " + EVENTS_CURSOR_TABLE + " SET event_marker = ? WHERE operator_id = ?";
    private static final String UPDATE_TIMESTAMP_SQL =
            "UPDATE " + EVENTS_CURSOR_TABLE + " SET last_updated = ? where operator_id = ?";
    private static final String UPDATE_CURSOR_STATUS =
            "UPDATE " + EVENTS_CURSOR_TABLE + " SET status = ? WHERE operator_id = ?";
    private static final String UPDATE_CURSOR_WHERE_STATUS =
            "UPDATE " + EVENTS_CURSOR_TABLE + " SET status = ? WHERE operator_id = ? and status = ?";
    private static final String UPDATE_CURSOR_MARKER_AND_TIMESTAMP = "UPDATE " + EVENTS_CURSOR_TABLE
            + " SET event_marker = ?, last_updated = ? WHERE operator_id = ?";
    private static final String SELECT_CURSOR_FOR_STATUS = "SELECT * FROM " + EVENTS_CURSOR_TABLE +
            " WHERE type = ? AND status = ? ";
    private static final String SELECT_CURSORS_BY_TYPE_AND_STATUS_BEFORE_TIMESTAMP = "SELECT * FROM "
            + EVENTS_CURSOR_TABLE
            + " WHERE type in (#) AND status = ? AND last_updated < ?";

    @Autowired
    public NodeEventsCursorDao(JdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.jdbcHelper = jdbcHelper;
        this.daoHelper = new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(),
                EVENTS_CURSOR_TABLE, this::getFromResultSet, 100);
    }

    @Autowired
    public void setDbProperties(DbProperties dbProperties) {
        this.dbProperties = dbProperties;
    }

    /**
     * Insert a new metadata cursor
     *
     * @param cursor the cursor to insert
     * @return the number of rows affected
     * @throws SQLException - SQL Exception thrown if the SQL operation failed
     */
    public int insert(@Nonnull DbNodeEventCursor cursor) throws SQLException {
        return jdbcHelper.executeUpdate(INSERT_CURSOR_SQL,
                cursor.getOperatorId(),
                cursor.getEventMarker(),
                cursor.getType().getName(),
                cursor.getStatus() != null ? cursor.getStatus().name() : null,
                cursor.getLastUpdated()
        );
    }

    /**
     * Update an existing event cursor
     *
     * @param cursor the cursor to insert
     * @return the number of rows affected
     * @throws SQLException - SQL Exception thrown if the SQL operation failed
     */
    public int update(@Nonnull DbNodeEventCursor cursor) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_CURSOR_SQL, cursor.getEventMarker(), cursor.getOperatorId());
    }

    public int update(@Nonnull String operatorId, long eventMarker) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_CURSOR_SQL, eventMarker, operatorId);
    }

    public void updateWithMarkerAndTimestamp(@NonNull String cursorName,
            long marker,
            long timestamp) throws SQLException {
        jdbcHelper.executeUpdate(UPDATE_CURSOR_MARKER_AND_TIMESTAMP, marker, timestamp, cursorName);
    }

    public List<DbNodeEventCursor> getAll() throws SQLException {
        return daoHelper.getAll();
    }

    public List<DbNodeEventCursor> getAllByTypes(@NonNull List<NodeEventCursorType> types) throws SQLException {
        QueryWriter queryWriter = new QueryWriter(this.dbProperties.getDbType());
        queryWriter.select().from(EVENTS_CURSOR_TABLE).where("type in (#)");
        List<String> typeNames = types.stream().map(NodeEventCursorType::getName).toList();
        return daoHelper.findAllByQuery(queryWriter.build(), typeNames);
    }

    @Nonnull
    public Optional<DbNodeEventCursor> cursorForOperator(String operatorId) throws SQLException {
        return daoHelper.findFirstByQuery("SELECT * FROM " + EVENTS_CURSOR_TABLE + " WHERE operator_id = ?",
                operatorId);
    }

    public List<DbNodeEventCursor> cursorForStatus(@NonNull NodeEventCursorStatus status,
            @NonNull NodeEventCursorType type) throws SQLException {
        return daoHelper.findAllByQuery(SELECT_CURSOR_FOR_STATUS,
                type.getName(), status.name());
    }

    public List<DbNodeEventCursor> cursorForRepoAndType(@NonNull String repoKey,
            @NonNull NodeEventCursorType type) throws SQLException {
        return daoHelper.findAllByQuery(
                "SELECT * FROM " + EVENTS_CURSOR_TABLE + " WHERE type = ? AND operator_id LIKE ?",
                type.getName(), repoKey + "-%");
    }

    public List<DbNodeEventCursor> getCursorsBy(@NonNull List<NodeEventCursorType> type,
            @NonNull NodeEventCursorStatus status,
            long timestamp)
            throws SQLException {
        List<String> typesNames = type.stream().map(NodeEventCursorType::getName).collect(Collectors.toList());
        return daoHelper.findAllByQuery(SELECT_CURSORS_BY_TYPE_AND_STATUS_BEFORE_TIMESTAMP,
                typesNames,
                status.name(),
                timestamp);
    }

    public int deleteAll() throws SQLException {
        return daoHelper.deleteAll();
    }

    public long oldestMarkerForTypes(List<NodeEventCursorType> types) throws SQLException {
        ResultSet resultSet = null;
        try {
            List<String> typesNames = types.stream().map(NodeEventCursorType::getName).collect(Collectors.toList());
            resultSet = jdbcHelper
                    .executeSelect("SELECT MIN(event_marker) FROM " + EVENTS_CURSOR_TABLE + " WHERE type in (#)",
                            typesNames);
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0;
        } finally {
            DbUtils.close(resultSet);
        }
    }

    public int delete(String operationId) throws SQLException {
        return jdbcHelper.executeUpdate("DELETE FROM " + EVENTS_CURSOR_TABLE + " WHERE operator_id = ?", operationId);
    }

    public void updateCursorsStatus(@NonNull String operatorId,
            @NonNull NodeEventCursorStatus status) throws SQLException {
        jdbcHelper.executeUpdate(UPDATE_CURSOR_STATUS, status.name(), operatorId);
    }

    public int updateWhereStatus(@NonNull String operatorId, @NonNull NodeEventCursorStatus sourceStatus,
            @NonNull NodeEventCursorStatus targetStatus) throws SQLException {
        return jdbcHelper.executeUpdate(UPDATE_CURSOR_WHERE_STATUS, targetStatus.name(), operatorId,
                sourceStatus.name());
    }

    public void updateTimestamp(String cursorName, long timestamp) throws SQLException {
        int updated = jdbcHelper.executeUpdate(UPDATE_TIMESTAMP_SQL, timestamp, cursorName);
        log.debug("Tried to update cursorName: {} with timestamp: {}, updated rows: {}",
                cursorName, timestamp, updated);
    }

    private DbNodeEventCursor getFromResultSet(ResultSet resultSet) throws SQLException {
        return new DbNodeEventCursor(resultSet.getString("operator_id"),
                resultSet.getLong("event_marker"),
                NodeEventCursorType.forName(resultSet.getString("type")),
                NodeEventCursorStatus.forName(resultSet.getString("status")),
                resultSet.getLong("last_updated"));
    }

    /**
     * @return lists of mirrors that are of provided type and are not with provided status
     */
    public List<RawMirrorStatusInfo> getMirrorsByTypeAndDifferentProvidedStatus(String type, String status)
            throws SQLException {
        List<RawMirrorStatusInfo> unavailableMirrors = new ArrayList<>();
        QueryWriter queryWriter = new QueryWriter(this.dbProperties.getDbType());
        queryWriter.select("operator_id, status").from(EVENTS_CURSOR_TABLE)
                .where("type=? and status <> ?");

        try (ResultSet rs = jdbcHelper.executeSelect(queryWriter.build(), type, status)) {
            while (rs.next()) {
                String operatorId = rs.getString("operator_id");
                String statusValue = rs.getString("status");
                unavailableMirrors.add(new RawMirrorStatusInfo(operatorId, statusValue));
            }
        }
        return unavailableMirrors;
    }
}
