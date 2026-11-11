package org.artifactory.storage.db.event.error.dao;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.event.EventType;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorsDBO;
import org.artifactory.storage.db.event.error.model.RepoAndCategory;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.artifactory.storage.event.EventErrorStatusInfo;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.SqlDaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


/**
 * A data access object for the node_events_errors table.
 *
 * @author Mor Merhav
 */
@Repository
public class NodeEventsErrorsDAO {
    public static final String TABLE_NODE_EVENTS_ERRORS = "node_events_errors";
    private static final String SELECT_MIN_TIME_WITH_EVENT_ID = "SELECT MIN(event_time) FROM " +
            TABLE_NODE_EVENTS_ERRORS + " WHERE event_id IS NOT NULL";
    private static final String[] COLUMNS =
            {"error_id", "first_error_time", "last_error_time", "error_count",
                    "error_message", "operation_key", "event_id", "event_time",
                    "event_type", "event_category", "path"};

    /**
     * Delete from node_events_errors before event_time
     */
    private static final String DELETE_BEFORE_SQL_WITH_EVENT_ID =
            "DELETE FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE event_time < ? AND event_id IS NOT NULL";

    private static final String PAGINATION_CREATE = TABLE_NODE_EVENTS_ERRORS + " (" + String.join(",", COLUMNS) + ")";

    private final SqlDaoHelper<NodeEventsErrorsDBO> daoHelper;
    private final JdbcHelper jdbcHelper;
    private DbType dbType;

    @Autowired
    public NodeEventsErrorsDAO(JdbcHelper jdbcHelper, ArtifactoryDbProperties dbProperties) {
        this.daoHelper =
                new SqlDaoHelper<>(jdbcHelper, dbProperties.getDbType(), TABLE_NODE_EVENTS_ERRORS, this::getRowMapper,
                        100);
        this.jdbcHelper = jdbcHelper;
        this.dbType = dbProperties.getDbType();
    }

    /**
     * @return erroneous set of repos, event category and operation key
     * @throws SQLException
     */
    @Nonnull
    public Set<RepoAndCategory> getErroneousRepos() throws SQLException {
        String query = new ArtifactoryQueryWriter()
                .select(substr(dbType, "nee.path", "/") + ", nee.event_category")
                .distinct()
                .from("node_events_errors nee ")
                .where("nee.event_id IS NOT NULL")
                .build();

        Set<RepoAndCategory> results = new HashSet<>();
        try (ResultSet resultSet = jdbcHelper.executeSelect(query)) {
            while (resultSet.next()) {
                results.add(new RepoAndCategory(
                        resultSet.getString(1),
                        resultSet.getString(2)));
            }
        }
        return results;
    }

    public int insert(NodeEventsErrorsDBO dbo) throws SQLException {
        return insert(Collections.singletonList(dbo));
    }

    public int insert(List<NodeEventsErrorsDBO> dbos) throws SQLException {
        return daoHelper.paginationCreate(PAGINATION_CREATE, COLUMNS.length,
                dbo -> Stream.of(dbo.getErrorId(), dbo.getFirstErrorTime(), dbo.getLastErrorTime(), dbo.getErrorCount(),
                        dbo.getErrorMessage(), dbo.getOperationKey(), dbo.getEventId(), dbo.getEventTime(),
                        dbo.getEventType(), dbo.getEventCategory(), dbo.getPath()),
                dbos.toArray(new NodeEventsErrorsDBO[0]));
    }

    /**
     * Delete all errors with event_id before the specified endExclusive arg.
     * @param endExclusive end event_time exclusive
     * @return count of deleted
     */
    public int deleteBeforeWithEventId(long endExclusive) throws SQLException {
        return daoHelper.executeUpdate(DELETE_BEFORE_SQL_WITH_EVENT_ID, endExclusive);
    }

    public List<NodeEventsErrorsDBO> selectAll(String eventCategory) throws SQLException {
        return Lists.newArrayList(daoHelper.findAllByProperty("event_category", eventCategory));
    }

    public NodeEventsErrorsDBO select(String eventCategory, EventType eventType, String path) throws SQLException {
        return select(eventCategory, eventType, path, null);
    }

    public NodeEventsErrorsDBO select(String eventCategory, EventType eventType, String path, String operationKey)
            throws SQLException {
        String query = "SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS +
                " WHERE event_category = ? AND event_type = ? AND path = ?";
        if (StringUtils.isNotBlank(operationKey)) {
            query = query + " AND operation_key = ?";
            Optional<NodeEventsErrorsDBO> optionalDBO =
                    daoHelper.findFirstByQuery(query, eventCategory, eventType.code(), path, operationKey);
            return optionalDBO.orElse(null);
        }
        Optional<NodeEventsErrorsDBO> optionalDBO =
                daoHelper.findFirstByQuery(query, eventCategory, eventType.code(), path);
        return optionalDBO.orElse(null);
    }

    public List<NodeEventsErrorsDBO> select(String eventCategory, String operationKey, int batchSize)
            throws SQLException {
        return selectWithLimitQuery(dbType, eventCategory, operationKey, batchSize);
    }

    public List<NodeEventsErrorsDBO> select(String eventCategory, String operationKey)
            throws SQLException {
        String query = "SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE event_category = ? AND operation_key = ?";
        return daoHelper.findAllByQuery(query, eventCategory, operationKey);
    }

    public Map<String, EventErrorStatusInfo> countEventErrorsByOperationKey(List<String> operationKeys,
            Long currentTime)
            throws SQLException {
        Map<String, EventErrorStatusInfo> mappedOperationKeyToErrorEvent = new HashMap<>();
        StringBuilder condition = new StringBuilder();
        if (operationKeys.size() == 0) {
            return mappedOperationKeyToErrorEvent;
        }
        condition.append(String.format("nee.first_error_time <= %d AND (nee.operation_key = '%s' ", currentTime,
                operationKeys.get(0)));
        for (int i = 1; i < operationKeys.size(); i++) {
            condition.append(String.format("OR nee.operation_key = '%s'", operationKeys.get(i)));
        }
        condition.append(")");
        String sql = new ArtifactoryQueryWriter()
                .select("nee.operation_key, SUM(nee.error_count)")
                .from(TABLE_NODE_EVENTS_ERRORS + " nee")
                .where(condition.toString())
                .groupBy("nee.operation_key")
                .build();

        try (ResultSet resultSet = jdbcHelper.executeSelect(sql)) {
            while (resultSet.next()) {
                EventErrorStatusInfo eventErrorStatusInfo = new EventErrorStatusInfo(
                        resultSet.getString(1),
                        resultSet.getLong(2));
                mappedOperationKeyToErrorEvent.put(eventErrorStatusInfo.getCursor(), eventErrorStatusInfo);
            }
        }
        return mappedOperationKeyToErrorEvent;
    }

    public boolean delete(String eventCategory, EventType eventType, String path)
            throws SQLException {
        return delete(eventCategory, eventType, path, null);
    }

    public boolean delete(String eventCategory, EventType eventType, String path, String operationKey)
            throws SQLException {
        String query = "DELETE FROM " + TABLE_NODE_EVENTS_ERRORS +
                " WHERE event_category = ? AND event_type = ? AND path = ?";
        if (operationKey != null) {
            query = query + " AND operation_key = ?";
            return daoHelper.executeUpdate(query, eventCategory, eventType.code(), path, operationKey) > 0;
        }
        return daoHelper.executeUpdate(query, eventCategory, eventType.code(), path) > 0;
    }

    public boolean delete(long errorId) throws SQLException {
        String query = "DELETE FROM " + TABLE_NODE_EVENTS_ERRORS + " WHERE error_id = ?";
        return daoHelper.executeUpdate(query, errorId) > 0;
    }

    public int delete(String eventCategory, String operationKey) throws SQLException {
        return daoHelper.deleteByProperties(
                Lists.newArrayList(Pair.of("event_category", eventCategory), Pair.of("operation_key", operationKey)));
    }

    public int delete(String eventCategory, String operationKey, EventType eventType) throws SQLException {
        return daoHelper.deleteByProperties(
                Lists.newArrayList(Pair.of("event_category", eventCategory), Pair.of("operation_key", operationKey),
                        Pair.of("event_type", eventType.code())));
    }

    /**
     * Updates an existing error (by same event category, path, event type and operation key) record's count, error
     * message and last error time
     *
     * @param dbo - the error record to update
     * @return the number of rows affected
     */
    public int update(NodeEventsErrorsDBO dbo) throws SQLException {
        List<String> findByColumns = Lists.newArrayList("event_category", "event_type", "path");
        List<Object> findByValues = Lists.newArrayList(dbo.getEventCategory(), dbo.getEventType(), dbo.getPath());

        if (dbo.getOperationKey() != null) {
            findByColumns.add("operation_key");
            findByValues.add(dbo.getOperationKey());
        }

        return daoHelper.updateByProperties(
                findByColumns,
                findByValues.stream(),
                List.of("error_count",
                        "error_message",
                        "last_error_time"),
                Stream.of(
                        dbo.getErrorCount(),
                        dbo.getErrorMessage(),
                        dbo.getLastErrorTime()
                )
        );
    }

    /**
     * Updates an existing error (by id) record's count, error
     * message and last error time
     *
     * @param dbo - the error record to update
     * @return the number of rows affected
     */
    public int updateById(NodeEventsErrorsDBO dbo) throws SQLException {
        return daoHelper.updateByProperties(
                List.of("error_id"),
                Stream.of(dbo.getErrorId()),
                List.of("error_count", "error_message", "last_error_time"),
                Stream.of(dbo.getErrorCount(), dbo.getErrorMessage(), dbo.getLastErrorTime())
        );
    }

    /**
     * Return the first event error with event_id timestamp
     */
    public long getFirstEventErrorTimestampWithEventId() throws SQLException {
        return jdbcHelper.executeSelectLong(SELECT_MIN_TIME_WITH_EVENT_ID);
    }

    private NodeEventsErrorsDBO getRowMapper(ResultSet rs) throws SQLException {
        return NodeEventsErrorsDBO.builder()
                .errorId(rs.getLong("error_id"))
                .eventCategory(rs.getString("event_category"))
                .firstErrorTime(rs.getLong("first_error_time"))
                .lastErrorTime(rs.getLong("last_error_time"))
                .errorCount(rs.getInt("error_count"))
                .errorMessage(rs.getString("error_message"))
                .operationKey(rs.getString("operation_key"))
                .eventTime(rs.getLong("event_time"))
                .eventId(rs.getObject("event_id", Long.class))
                .eventType(rs.getInt("event_type"))
                .path(rs.getString("path"))
                .build();
    }

    private String substr(DbType dbType, String column, String until) {
        switch (dbType) {
            case MYSQL:
            case MARIADB:
                return String.format("SUBSTRING(%1$s, 1,  INSTR(%1$s, '%2$s') - 1)", column, until);
            case DERBY:
                return String.format("SUBSTR(%1$s, 1, LOCATE('%2$s', %1$s) - 1)", column, until);
            case MSSQL:
                return String.format("SUBSTRING(%1$s, 1,  CHARINDEX('%2$s', %1$s) - 1)", column, until);
            case ORACLE:
                return String.format("SUBSTR(%1$s, 1, INSTR(%1$s, '%2$s') - 1)", column, until);
            case POSTGRESQL:
                return String.format("SUBSTRING(%1$s, 1,  POSITION('%2$s' in %1$s) - 1)", column, until);
            default:
                throw new UnsupportedOperationException(String.valueOf(dbType));
        }
    }
    /**
     * Selects a limited number of error records based on the event category and operation key.
     *
     * @param dbType         The database type.
     * @param eventCategory  The event category to filter by.
     * @param operationKey   The operation key to filter by.
     * @param batchSize      The maximum number of records to return.
     * @return A list of NodeEventsErrorsDBO records.
     * @throws SQLException If an SQL error occurs.
     */
    private List<NodeEventsErrorsDBO> selectWithLimitQuery(DbType dbType, String eventCategory,
            String operationKey, int batchSize) throws SQLException {
        String base = "SELECT * FROM " + TABLE_NODE_EVENTS_ERRORS +
                " WHERE event_category = ? AND operation_key = ?";
        String query;
        Object[] params;

        switch (dbType) {
            case MYSQL:
            case MARIADB:
            case POSTGRESQL:
                query = base + " LIMIT ?";
                params = new Object[]{eventCategory, operationKey, batchSize};
                break;
            case MSSQL:
                query = "SELECT TOP (?) * FROM " + TABLE_NODE_EVENTS_ERRORS +
                        " WHERE event_category = ? AND operation_key = ?";
                params = new Object[]{batchSize, eventCategory, operationKey};
                break;
            case ORACLE:
                query = base + " AND ROWNUM <= ?";
                params = new Object[]{eventCategory, operationKey, batchSize};
                break;
            case DERBY:
                query = base + " FETCH FIRST ? ROWS ONLY";
                params = new Object[]{eventCategory, operationKey, batchSize};
                break;
            default:
                throw new UnsupportedOperationException("Unsupported DB type: " + dbType);
        }
        return daoHelper.findAllByQuery(query, params);
    }


}
