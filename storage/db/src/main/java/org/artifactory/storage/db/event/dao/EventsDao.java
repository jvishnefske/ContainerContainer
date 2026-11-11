/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.event.dao;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventType;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.event.entity.EventRecord;
import org.artifactory.storage.db.event.entity.EventsDaoParams;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.common.ResultSetStream;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.jfrog.storage.util.querybuilder.QueryWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A data access object for the events table.
 *
 * @author Yossi Shaul
 */
@Slf4j
@Repository
public class EventsDao extends BaseDao {

    public static final String EVENTS_TABLE = "node_events";
    public static final String EVENTS_TEMP_TABLE = "node_events_tmp";

    private static final String INSERT_EVENTS_INTO_NODE_EVENTS_TEMP = """
            INSERT INTO node_events_tmp
            (tmp_event_id, timestamp, event_type, path, hints)
            VALUES(?, ?, ?, ?, ?)
            """;
    /**
     * Delete range on node_events which doesn't have reference in node_events_errors
     */
    private static final String DELETE_RANGE_SQL = """
            DELETE FROM node_events
            WHERE timestamp >= ? AND timestamp < ?
            AND event_id NOT IN
                (SELECT event_id
                FROM node_events_errors
                WHERE event_id IS NOT NULL
                AND event_time >= ? AND event_time < ?)
            """;

    @VisibleForTesting
    static final String SELECT_ALL_EVENTS_FROM_CURSOR_TO_PROVIDED_EVENT = """
            SELECT * FROM node_events
            WHERE event_id > (SELECT event_marker FROM node_event_cursor WHERE operator_id LIKE ? AND type=?)
            AND event_id <= ?
            AND
            """;

    private static final String INSERT_EVENT_SQL = """
            INSERT INTO node_events
            (timestamp, event_type, path, hints %1$s)
            VALUES (?, ?, ?, ? %2$s)
            """;

    private static final String INSERT_EVENT_WITH_REPO_SQL = """
            INSERT INTO node_events
            (timestamp, event_type, path, hints %1$s, repo)
            VALUES (?, ?, ?, ? %2$s, ?)
            """;

    private static final String DELETE_TEMP_EVENTS_SQL = """
            DELETE FROM node_events_tmp
            WHERE tmp_event_id IN (#)
            """;

    private static final long MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1);
    private static final long MILLIS_PER_HOUR = TimeUnit.HOURS.toMillis(1);

    /**
     * PostgreSQL-specific DELETE prefix used by the shift-events flow. The predicate matches a tuple
     * of {@code (tmp_event_id, timestamp, partition-key expression)} against a list of literal tuples,
     * one per row being deleted. Including the {@code PARTITION BY} expression
     * ({@code (timestamp % day / hour)}) in the tuple lets PostgreSQL prune to the partitions that
     * actually hold the rows; the legacy DELETE (filter by {@code tmp_event_id} only) caused
     * PostgreSQL to scan all four partitions on every batch, which became catastrophic under heap
     * bloat.
     * <p>
     * The tuple placeholders ({@code ((?, ?, ?), ...)}) are appended at runtime by
     * {@link #deletePostgresBatchUsingTupleIn(List)} based on the batch size.
     */
    @VisibleForTesting
    static final String DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX =
            "DELETE FROM node_events_tmp WHERE (tmp_event_id, timestamp, (timestamp %% (%d) / %d)) IN "
                    .formatted(MILLIS_PER_DAY, MILLIS_PER_HOUR);

    @VisibleForTesting
    public static final String PKEY_INDEX_HINT_ORACLE = "/*+ INDEX(node_events_tmp node_events_tmp_pk) */";

    @VisibleForTesting
    public static final String DELETE_TEMP_EVENTS_USING_PK_HINT_ORACLE_SQL = """
            DELETE %s
            FROM node_events_tmp
            WHERE tmp_event_id IN (#)
            """.formatted(PKEY_INDEX_HINT_ORACLE);


    public static final String WITH_EVENT_ID = ", event_id";
    private static final String SEQ_PG = ", NEXTVAL('node_events_event_id_seq')";
    private static final String SEQ_ORA = ", node_events_event_id_seq.NEXTVAL";
    private static final String MAX_EVENT_ID_QUERY = "SELECT MAX(event_id) FROM node_events";
    private static final String TRUNCATE_PARTITION_SQL = "TRUNCATE %s";
    private static final String CHECK_PARTITION_SQL = "Select 1 from %s where timestamp > 1 limit 1";
    private static final String CHECK_PARTITION_EXISTENCE =
            "Select 1 from information_schema.tables where table_schema = 'public' and table_name = '%s'";

    public static final int TMP_EVENTS_DELETE_BATCH_SIZE = 100;
    public static final int TMP_EVENTS_INSERT_BATCH_SIZE = ConstantValues.shiftEventsInsertBulkSize.getInt();
    public static final int TMP_EVENTS_COPY_BULK_SIZE = ConstantValues.shiftEventsCopyBulkSize.getInt();

    private DbService dbService;
    private DbProperties dbProperties;
    private DbType dbType;
    private String insertEventSql;
    private String insertEventSqlWithRepo;
    private String selectTmpEventsSql;
    private String deleteTmpEventsSql = DELETE_TEMP_EVENTS_SQL;

    private final MeterRegistry meterRegistry;


    @Autowired
    public EventsDao(ArtifactoryJdbcHelper jdbcHelper, MeterRegistry meterRegistry) {
        super(jdbcHelper);
        this.meterRegistry = meterRegistry;
    }

    @Autowired
    public void setDbService(DbService dbService) {
        this.dbService = dbService;
    }

    @Autowired
    public void setDbProperties(DbProperties dbProperties) {
        this.dbProperties = dbProperties;
    }

    @PostConstruct
    public void init() {
        dbType = dbProperties.getDbType();
        this.selectTmpEventsSql = new QueryWriter(this.dbType)
                .select("timestamp, event_type, path, hints, tmp_event_id")
                .from(EVENTS_TEMP_TABLE)
                .limit(ConstantValues.shiftEventsSqlLimit.getLong())
                .orderBy("timestamp, tmp_event_id")
                .build();

        switch (this.dbType) {
            case POSTGRESQL -> {
                this.insertEventSql = String.format(INSERT_EVENT_SQL, WITH_EVENT_ID, SEQ_PG);
                this.insertEventSqlWithRepo = String.format(INSERT_EVENT_WITH_REPO_SQL, WITH_EVENT_ID, SEQ_PG);
            }
            case ORACLE -> {
                this.insertEventSql = String.format(INSERT_EVENT_SQL, WITH_EVENT_ID, SEQ_ORA);
                this.insertEventSqlWithRepo = String.format(INSERT_EVENT_WITH_REPO_SQL, WITH_EVENT_ID, SEQ_ORA);
                if (ConstantValues.shiftEventsOracleDbUsePKeyForDeleteTempEvents.getBoolean()) {
                    log.info("Using Oracle PK index hint for deleting tmp events during shift operation");
                    this.deleteTmpEventsSql = DELETE_TEMP_EVENTS_USING_PK_HINT_ORACLE_SQL;
                }
            }
            case DERBY -> {
                this.insertEventSql = String.format(INSERT_EVENT_SQL, WITH_EVENT_ID, ", ?");
                this.insertEventSqlWithRepo = String.format(INSERT_EVENT_WITH_REPO_SQL, WITH_EVENT_ID, ", ?");
            }
            default -> {
                this.insertEventSql = String.format(INSERT_EVENT_SQL, "", "");
                this.insertEventSqlWithRepo = String.format(INSERT_EVENT_WITH_REPO_SQL, "", "");
            }
        }
    }

    public void create(EventRecord e) throws SQLException {
        jdbcHelper.executeUpdate(INSERT_EVENTS_INTO_NODE_EVENTS_TEMP,
                dbService.nextId(), e.getTimestamp(), e.getType().code(), e.getPath(), e.getHints());
    }

    public void createBulk(@NonNull List<EventRecord> events) throws SQLException {
        for (List<EventRecord> eventsPartition : Lists.partition(events, TMP_EVENTS_INSERT_BATCH_SIZE)) {
            List<Object[]> params = calculateBulkParams(eventsPartition);
            jdbcHelper.executeBulkUpdate(INSERT_EVENTS_INTO_NODE_EVENTS_TEMP, params);
        }
    }

    @NonNull
    private List<Object[]> calculateBulkParams(@NonNull List<EventRecord> eventsPartition) {
        List<Object[]> params = new ArrayList<>();
        for (EventRecord e : eventsPartition) {
            Object[] eventParams =
                    new Object[] {dbService.nextId(), e.getTimestamp(), e.getType().code(), e.getPath(), e.getHints()};
            params.add(eventParams);
        }
        return params;
    }

    @Nonnull
    public List<EventRecord> loadEventsByTimestamp(long timestamp)
            throws SQLException {
        ResultSet rs = null;
        try {
            List<EventRecord> entries = Lists.newArrayList();
            rs = jdbcHelper.executeSelect("SELECT * FROM node_events " +
                    "WHERE timestamp = ? ORDER BY timestamp ASC, event_id ASC", timestamp);
            while (rs.next()) {
                entries.add(eventFromResultSet(rs));
            }
            return entries;
        } finally {
            DbUtils.close(rs);
        }
    }

    @Nonnull
    public List<EventRecord> loadEventsByParams(EventsDaoParams queryParams) throws SQLException {
        try (ResultSet rs = handleGetResultSet(queryParams, false)) {
            List<EventRecord> entries = Lists.newArrayList();
            while (rs.next()) {
                entries.add(eventFromResultSet(rs));
            }
            return entries;
        }
    }

    public List<EventRecord> eventsLeftForCursorToReach(String cursorName, String type, String repoName, long eventMax,
            boolean withRepoKey) throws SQLException {
        String sql = SELECT_ALL_EVENTS_FROM_CURSOR_TO_PROVIDED_EVENT;
        if (withRepoKey) {
            sql += " repo = ?";
        } else {
            sql += " path like ?";
            repoName += "/%";
        }
        List<EventRecord> entries = Lists.newArrayList();
        try (ResultSet rs = jdbcHelper.executeSelect(sql, cursorName, type, eventMax, repoName)) {
            while (rs.next()) {
                entries.add(eventFromResultSet(rs));
            }
        }
        return entries;
    }

    public int countEventsByParams(EventsDaoParams queryParams) throws SQLException {
        if (queryParams.getLimit() > 0) {
            throw new IllegalArgumentException("limit is not allowed");
        }
        try (ResultSet resultSet = getResultSet(queryParams, true)) {
            int count = 0;
            if (resultSet.next()) {
                count = resultSet.getInt(1);
            }
            return count;
        }
    }

    /**
     * Loads all the events newer from the fromTimeStamp and older than the toTimestamp
     *
     * @param fromTimestamp Beginning of the interval non-inclusive timestamp in millis
     * @param toTimestamp   End of the interval non-inclusive timestamp in millis
     * @return Events newer than the given fromTimestamp and older then the toTimestamp ordered by timestamp
     */
    @Nonnull
    public Stream<EventRecord> loadIntervalStream(long fromTimestamp, long toTimestamp, String repoKey, long limit,
            CompletableFuture<SQLException> onException) {
        QueryWriter queryWriter = new ArtifactoryQueryWriter();
        queryWriter.select().from(EVENTS_TABLE)
                .where("timestamp > ? AND timestamp < ? AND path like ?")
                .limit(limit)
                .orderBy("timestamp ASC, event_id ASC");
        try {
            ResultSet rs = jdbcHelper.executeSelect(queryWriter.build(), fromTimestamp, toTimestamp, repoKey + "/%");
            return ResultSetStream.asStream(rs, this::eventFromResultSet, onException);
        } catch (SQLException e) {
            onException.complete(e);
            return Stream.empty();
        }
    }

    /**
     * Return max events by timestamp
     */
    @Nonnull
    public Stream<EventRecord> loadNewerStream(long timestamp, long maxEvents,
            CompletableFuture<SQLException> onException) {
        try {
            QueryWriter queryWriter = new ArtifactoryQueryWriter();
            String sql = queryWriter.select().from(EVENTS_TABLE)
                    .where("timestamp > ?")
                    .orderBy("timestamp ASC, event_id ASC")
                    .limit(maxEvents)
                    .build();
            ResultSet rs = jdbcHelper.executeSelect(sql, timestamp);
            return ResultSetStream.asStream(rs, this::eventFromResultSet, onException);
        } catch (SQLException e) {
            onException.complete(e);
            return Stream.empty();
        }
    }

    public Long getEventIdByTimestamp(long timestamp, String repoKey, boolean withRepoKey)
            throws SQLException {
        QueryWriter queryWriter = new ArtifactoryQueryWriter();
        QueryWriter queryBuilder = queryWriter.select().from(EVENTS_TABLE).orderBy("event_id ASC").limit(1L);
        String whereClause = "timestamp >= ?";

        if (StringUtils.hasLength(repoKey)) {
            return getEventIdByTimestampWithRepo(timestamp, repoKey, whereClause, queryBuilder, withRepoKey);
        }
        return getEventIdByTimestampWithoutRepo(timestamp, whereClause, queryBuilder);
    }

    private Long getEventIdByTimestampWithRepo(long timestamp, String repoKey, String whereClause,
            QueryWriter queryBuilder, boolean withRepoKey)
            throws SQLException {
        if (withRepoKey) {
            whereClause += " and repo=?";
        } else {
            repoKey += "/%";
            whereClause += " and path like ?";
        }
        queryBuilder.where(whereClause);

        String sql = queryBuilder.build();
        log.debug("The provided sql: {}", sql);
        try (ResultSet rs = jdbcHelper.executeSelect(sql, timestamp, repoKey)) {
            if (rs.next()) {
                return rs.getLong("event_id");
            }
        }
        return -1L;
    }

    private Long getEventIdByTimestampWithoutRepo(long timestamp, String whereClause, QueryWriter queryBuilder)
            throws SQLException {
        queryBuilder.where(whereClause);

        String sql = queryBuilder.build();
        log.debug("The provided sql: {}", sql);

        try (ResultSet rs = jdbcHelper.executeSelect(sql, timestamp)) {
            if (rs.next()) {
                return rs.getLong("event_id");
            }
        }
        return -1L;
    }

    public long getEventsCount() throws SQLException {
        return jdbcHelper.executeSelectLong("SELECT COUNT(*) FROM node_events");
    }

    public long getTempEventsCount() throws SQLException {
        return jdbcHelper.executeSelectLong("SELECT COUNT(*) FROM node_events_tmp");
    }

    public long getFirstEventTimestamp() throws SQLException {
        return jdbcHelper.executeSelectLong("SELECT MIN(timestamp) FROM node_events");
    }

    public long getLastEventTimestamp() throws SQLException {
        return jdbcHelper.executeSelectLong("SELECT MAX(timestamp) FROM node_events");
    }

    public long getFirstEventId(String repoKey) throws SQLException {
        return jdbcHelper.executeSelectLong("SELECT MIN(event_id) FROM node_events WHERE path LIKE ?", repoKey + "/%");
    }

    public long getLastEventId() throws SQLException {
        return jdbcHelper.executeSelectLong(MAX_EVENT_ID_QUERY);
    }

    public long getOldestTimestampByEventIdInclusive(long eventId) throws SQLException {
        //This method is used to get the timestamp of the min eventId during events cleanup.
        // We might have different order for event ids and timestamp
        // EventId, Timestamp
        // 100,     200
        // 101,     199
        // Min EventId = 100, The timestamp is 200, we will delete everything until timestamp 200
        // including event 101
        // The bellow query returns the min timestamp for all the events equals and above the specified event id
        return jdbcHelper.executeSelectLong("SELECT MIN(timestamp) FROM node_events WHERE event_id >= ?", eventId);
    }

    public List<EventRecord> loadAll() throws SQLException {
        ResultSet rs = null;
        try {
            List<EventRecord> entries = Lists.newArrayList();
            rs = jdbcHelper.executeSelect("SELECT * FROM node_events ORDER BY event_id ASC");
            while (rs.next()) {
                entries.add(eventFromResultSet(rs));
            }
            return entries;
        } finally {
            DbUtils.close(rs);
        }
    }

    public Stream<EventRecord> loadAllStream(CompletableFuture<SQLException> onException) throws SQLException {
        return ResultSetStream.asStream(
                jdbcHelper.executeSelect("SELECT * FROM node_events ORDER BY timestamp ASC, event_id ASC"),
                this::eventFromResultSet,
                onException);
    }

    public int countRangeEventsByRepoPath(long startInclusive, long endNonInclusive,
            @NonNull String repoKey)
            throws SQLException {
        String sql = new ArtifactoryQueryWriter().from(EVENTS_TABLE)
                .select("count(*)")
                .where("timestamp >= ? AND timestamp < ? and path like ?")
                .build();
        String repoPath = repoKey + "/%";
        try (ResultSet res = jdbcHelper.executeSelect(sql, startInclusive, endNonInclusive, repoPath)) {
            int count = 0;
            if (res.next()) {
                count = res.getInt(1);
            }
            return count;
        }

    }

    /**
     * Check if there is at least one event after the provided timestamp for that repo key
     * @param withRepoColumn handles async migration, where the repoColumn might not have been created yet
     */
    public boolean hasEventsAfterTimestamp(long timestamp, String repoKey, boolean withRepoColumn)
            throws SQLException {
        // use thr queryWriter because we need to limit number of returned results
        var queryWriter = new ArtifactoryQueryWriter();
        var queryBuilder = queryWriter.select("1 AS res").from(EVENTS_TABLE).limit(1L);
        String whereClause = "timestamp > ?";
        if (withRepoColumn) {
            whereClause += " and repo = ?";
        } else {
            repoKey += "/%";
            whereClause += " and path like ?";
        }
        queryBuilder.where(whereClause);
        String sql = queryBuilder.build();

        try (ResultSet rs = jdbcHelper.executeSelect(sql, timestamp, repoKey)) {
            return rs.next();
        }
    }

    /**
     * Counts the range of the events from the given start time to the end time.
     * @param startInclusive - The start time
     * @param endNonInclusive - The end time
     * @return how many events were found
     * @throws SQLException - Upon any issue with the db
     */
    public int countRangeEvents(long startInclusive, long endNonInclusive) throws SQLException {
        String sql = new ArtifactoryQueryWriter().from(EVENTS_TABLE)
                .select("count(*)")
                .where("timestamp >= ? AND timestamp < ?")
                .build();
        try (ResultSet res = jdbcHelper.executeSelect(sql, startInclusive, endNonInclusive)) {
            int count = 0;
            if (res.next()) {
                count = res.getInt(1);
            }
            return count;
        }
    }

    public ResultSet handleGetResultSet(EventsDaoParams queryParams, boolean count) throws SQLException {
        if (Boolean.TRUE.equals(queryParams.getAfterRepoColumnMigration()) &&
                StringUtils.hasLength(queryParams.getRepoKey())) {
            return getResultSetAfterRepoColumnMigrationAndWithRepoKey(queryParams, count);
        } else {
            return getResultSet(queryParams, count);
        }
    }

    ResultSet getResultSetAfterRepoColumnMigrationAndWithRepoKey(EventsDaoParams queryParams, boolean count)
            throws SQLException {
        ParsedQueryParameters getParsedQueryParameters = parseDaoQueryParamsAfterRepoMigration(queryParams, count);
        String whereClause = buildWhereClauseForEventIDAndPath(getParsedQueryParameters) + " AND repo = ?";

        // Prepare the sql
        String sql = getSql(count, getParsedQueryParameters.limitLong(), whereClause);
        return jdbcHelper.executeSelect(sql,
                getSqlParamsAfterMigrationWhenUsePath(getParsedQueryParameters));
    }

    private ResultSet getResultSet(EventsDaoParams queryParams, boolean count) throws SQLException {
        ParsedQueryParameters getParsedQueryParameters = parseDaoQueryParams(queryParams, count);
        String whereClause = buildWhereClauseForEventIDAndPath(getParsedQueryParameters);
        //prepare the sql
        String sql = getSql(count, getParsedQueryParameters.limitLong, whereClause);
        if (getParsedQueryParameters.useLikeOperator()) {
            return jdbcHelper.executeSelect(sql, getSqlParamsWhenUsePath(getParsedQueryParameters));
        } else {
            return jdbcHelper.executeSelect(sql, getParsedQueryParameters.eventId);
        }
    }

    private String buildWhereClauseForEventIDAndPath(ParsedQueryParameters getParsedQueryParameters) {
        return getParsedQueryParameters.fieldNameToCompare() +
                getParsedQueryParameters.comparator() + " ? " +
                (getParsedQueryParameters.useLikeOperator() ? "AND path like ?" : "");
    }

    private ParsedQueryParameters parseDaoQueryParamsAfterRepoMigration(EventsDaoParams queryParams,
            boolean count) {
        return parseDaoQueryParamsCommon(queryParams, count, true);
    }

    private ParsedQueryParameters parseDaoQueryParams(EventsDaoParams queryParams, boolean count) {
        boolean useLikeOperator =
                StringUtils.hasLength(queryParams.getRepoKey()) || StringUtils.hasLength(queryParams.getSuffix());
        return parseDaoQueryParamsCommon(queryParams, count, useLikeOperator);
    }

    private ParsedQueryParameters parseDaoQueryParamsCommon(EventsDaoParams queryParams, boolean count,
            boolean useLikeOperator) {
        boolean inclusive = queryParams.isInclusive();
        long eventId = queryParams.getEventId();
        String suffix = queryParams.getSuffix();
        String repoKey = queryParams.getRepoKey();
        Integer limit = !count && queryParams.getLimit() > 0 ? queryParams.getLimit() : null;
        long limitLong = limit != null ? limit.longValue() : 0L;
        String pathSuffix = null;
        String repoKeyPrefix = null;

        // Prepare where clause
        String comparator = inclusive ? ">=" : ">";
        // Use the timestamp field only if requested by the params.
        String fieldNameToCompare = "event_id ";

        if (useLikeOperator) {
            pathSuffix = suffix == null ? "" : suffix;
            repoKeyPrefix = StringUtils.hasLength(repoKey) ? repoKey + "/" : "";
        }

        return new ParsedQueryParameters(eventId, suffix, repoKey, limitLong, useLikeOperator, comparator,
                fieldNameToCompare,
                repoKeyPrefix, pathSuffix);
    }

    private Object[] getSqlParamsAfterMigrationWhenUsePath(ParsedQueryParameters getParsedQueryParameters) {
        // Include repo column
        // With repo key and suffix
        return new Object[] {getParsedQueryParameters.eventId,
                getParsedQueryParameters.repoKeyPrefix + "%" +
                        getParsedQueryParameters.pathSuffix, getParsedQueryParameters.repoKey};
    }

    private Object[] getSqlParamsWhenUsePath(ParsedQueryParameters getParsedQueryParameters) {
        return new Object[] {getParsedQueryParameters.eventId,
                getParsedQueryParameters.repoKeyPrefix + "%" + getParsedQueryParameters.pathSuffix};
    }

    private record ParsedQueryParameters(long eventId, String suffix, String repoKey, long limitLong,
                                         boolean useLikeOperator,
                                         String comparator, String fieldNameToCompare,
                                         String repoKeyPrefix, String pathSuffix) {
    }

    private record EventInfo(long eventId, long timestamp, EventType eventType, String path, String hints,
                             String repoKey) {
    }

    private String getSql(boolean count, long limit, String whereClause) {
        QueryWriter queryWriter = new ArtifactoryQueryWriter()
                .from(EVENTS_TABLE)
                .select(count ? "count(1)" : "*")
                .where(whereClause)
                .orderBy(count ? null : "event_id ASC");

        if (limit > 0L) {
            queryWriter.limit(limit);
        }
        return queryWriter.build();
    }


    /**
     * Delete all events from the event log. Internal use only!
     */
    public void deleteAll() throws SQLException {
        jdbcHelper.executeUpdate("DELETE FROM node_events_errors");
        jdbcHelper.executeUpdate("DELETE FROM node_events_tmp");
        jdbcHelper.executeUpdate("DELETE FROM node_events");
    }

    /**
     * Delete all events from in range.
     */
    public void deleteRange(long startInclusive, long endExclusive) throws SQLException {
        int deleteRowsCount = jdbcHelper.executeUpdate(DELETE_RANGE_SQL, startInclusive, endExclusive,
                startInclusive, endExclusive);
        log.trace("Deleted {} events from node event", deleteRowsCount);
    }

    public void truncateTmpPartition(String partitionName) throws SQLException {
        if (!nextPartitionHasContent(partitionName)) {
            jdbcHelper.executeUpdate(String.format(TRUNCATE_PARTITION_SQL, partitionName));
            log.debug("Truncated partition: {} of node_events_tmp", partitionName);
        } else {
            log.warn("Cannot truncate partition: {} of node_events_tmp since it's not empty", partitionName);
        }
    }

    public boolean hasPartition(String partitionName) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect(String.format(CHECK_PARTITION_EXISTENCE, partitionName))) {
            return rs.next();
        }
    }

    private boolean nextPartitionHasContent(String partitionName) throws SQLException {
        try (ResultSet rs = jdbcHelper.executeSelect(String.format(CHECK_PARTITION_SQL, partitionName))) {
            return rs.next();
        }
    }

    private EventRecord eventFromResultSet(ResultSet rs) throws SQLException {
        return new EventRecord(rs.getLong("event_id"),
                rs.getLong("timestamp"),
                EventType.fromCode(rs.getShort("event_type")),
                rs.getString("path"),
                rs.getString("hints"));
    }

    /**
     * Shift events from node_events_tmp to node_events And delete the tmp records.
     *
     * @return the affected repos
     */
    @Transactional
    public Set<String> shiftAndDeleteTmpEvents(Boolean withRepoColumn) throws SQLException {

        // Fetch temp events
        Instant fetchStart = Instant.now();
        List<EventInfo> tempEventList = selectTempEventsData();
        Duration fetchDuration = Duration.between(fetchStart, Instant.now());

        // Copy to node_events
        Instant copyStart = Instant.now();
        int copied = copyTempEventsToNodeEventsTable(tempEventList, withRepoColumn);
        Duration copyDuration = Duration.between(copyStart, Instant.now());

        // Delete from node_events_tmp
        Instant deleteStart = Instant.now();
        int deleted = deleteTempEvents(tempEventList);
        Duration deleteDuration = Duration.between(deleteStart, Instant.now());

        // Log and report metrics
        Set<String> repoKeys = tempEventList.stream().map(EventInfo::repoKey).collect(Collectors.toSet());
        logShiftResults(copied, deleted, fetchDuration, copyDuration, deleteDuration, repoKeys);
        reportShiftToMetrics(copied, deleted, fetchDuration.toMillis(), copyDuration.toMillis(),
                deleteDuration.toMillis());

        return repoKeys;
    }

    private List<EventInfo> selectTempEventsData() throws SQLException {
        List<EventInfo> events = new ArrayList<>();

        try (ResultSet rs = jdbcHelper.executeSelect(selectTmpEventsSql)) {
            while (rs.next()) {
                long timestamp = rs.getLong("timestamp");
                int eventType = rs.getInt("event_type");
                String path = rs.getString("path");
                String hints = rs.getString("hints");
                long tmpEventId = rs.getLong("tmp_event_id");

                String repoKey = RepoPathFactory.create(path).getRepoKey();
                events.add(new EventInfo(tmpEventId, timestamp, EventType.fromCode(eventType), path, hints, repoKey));
            }
        }
        return events;
    }

    private int copyTempEventsToNodeEventsTable(List<EventInfo> events, boolean withRepoColumn) throws SQLException {
        if (events.isEmpty()) {
            return 0;
        }

        int totalCopied = 0;

        for (List<EventInfo> partition : Lists.partition(events, TMP_EVENTS_COPY_BULK_SIZE)) {
            if (withRepoColumn) {
                totalCopied += copyEventsBulkWithRepo(partition);
            } else {
                totalCopied += copyEventsBulk(partition);
            }
        }

        return totalCopied;
    }

    private int copyEventsBulk(List<EventInfo> events) throws SQLException {
        List<Object[]> params = new ArrayList<>();
        for (EventInfo event : events) {
            if (DbType.DERBY.equals(this.dbType)) {
                params.add(new Object[]{event.timestamp(), event.eventType().code(), event.path(),
                        event.hints(), dbService.nextId()});
            } else {
                params.add(new Object[]{event.timestamp(), event.eventType().code(), event.path(), event.hints()});
            }
        }
        jdbcHelper.executeBulkUpdate(insertEventSql, params);
        return events.size();
    }

    private int copyEventsBulkWithRepo(List<EventInfo> events) throws SQLException {
        List<Object[]> params = new ArrayList<>();
        for (EventInfo event : events) {
            if (DbType.DERBY.equals(this.dbType)) {
                params.add(new Object[]{event.timestamp(), event.eventType().code(), event.path(),
                        event.hints(), dbService.nextId(), event.repoKey()});
            } else {
                params.add(new Object[]{event.timestamp(), event.eventType().code(), event.path(),
                        event.hints(), event.repoKey()});
            }
        }
        jdbcHelper.executeBulkUpdate(insertEventSqlWithRepo, params);
        return events.size();
    }

    private int deleteTempEvents(List<EventInfo> tempEventList) throws SQLException {
        if (tempEventList.isEmpty()) {
            return 0;
        }
        int deleted = 0;

        for (List<EventInfo> batch : Lists.partition(tempEventList, TMP_EVENTS_DELETE_BATCH_SIZE)) {
            if (DbType.POSTGRESQL.equals(this.dbType)) {
                deleted += deletePostgresBatchUsingTupleIn(batch);
            } else {
                List<Long> ids = batch.stream().map(e -> e.eventId).toList();
                deleted += jdbcHelper.executeUpdate(deleteTmpEventsSql, ids);
            }
        }

        return deleted;
    }

    /**
     * Issue a single DELETE for the whole batch using a tuple {@code IN} predicate of
     * {@code (tmp_event_id, timestamp, partition-key expression)}. Carrying the partition-key
     * expression in each tuple lets the PostgreSQL planner prune to the partitions that hold the
     * rows, while the single statement avoids the per-group round trips of the old approach.
     */
    private int deletePostgresBatchUsingTupleIn(List<EventInfo> batch) throws SQLException {
        String sql = DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX + buildTuplePlaceholders(batch.size());
        Object[] params = new Object[batch.size() * 3];
        int i = 0;
        for (EventInfo e : batch) {
            params[i++] = e.eventId;
            params[i++] = e.timestamp;
            params[i++] = partitionKeyFor(e.timestamp);
        }
        return jdbcHelper.executeUpdate(sql, params);
    }

    private static String buildTuplePlaceholders(int rowCount) {
        return Stream.generate(() -> "(?, ?, ?)")
                .limit(rowCount)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    /**
     * Mirrors the {@code node_events_tmp} {@code PARTITION BY} expression and
     * {@link #DELETE_TEMP_EVENTS_POSTGRES_TUPLE_IN_SQL_PREFIX}. Returns the hour-of-day (0..23).
     */
    @VisibleForTesting
    static int partitionKeyFor(long timestamp) {
        return (int) ((timestamp % MILLIS_PER_DAY) / MILLIS_PER_HOUR);
    }

    private void logShiftResults(int copied, int deleted, Duration fetchDuration, Duration copyDuration,
                                Duration deleteDuration, Set<String> reposKey) {
        Duration threshold = Duration.of(ConstantValues.shiftEventsThreshold.getInt(), ChronoUnit.MILLIS);
        String msg = "Shift tmp events: shifted=%s:(fetch %s ms, copy %s ms), deleted=%s:(%s ms), repos=%s"
                .formatted(copied, fetchDuration.toMillis(), copyDuration.toMillis(), deleted,
                        deleteDuration.toMillis(), reposKey);

        if (passedThreshold(fetchDuration, threshold) ||
                passedThreshold(copyDuration, threshold) ||
                passedThreshold(deleteDuration, threshold)) {
            log.warn(msg);
        } else {
            log.debug(msg);
        }
    }

    private void reportShiftToMetrics(int copied, int deleted, long fetchDurationMillis, long copyDurationMillis,
            long deleteDurationMillis) {
        reportShiftCounterMetric("copy", copied);
        reportShiftCounterMetric("delete", deleted);
        reportShiftDurationMetric("fetch", fetchDurationMillis);
        reportShiftDurationMetric("copy", copyDurationMillis);
        reportShiftDurationMetric("delete", deleteDurationMillis);
    }

    private void reportShiftCounterMetric(String operationType, int count) {
        Counter.builder("pq.shift.events")
                .description("Count of events handled during shift events")
                .tag("type", operationType)
                .register(meterRegistry)
                .increment(count);
    }

    private void reportShiftDurationMetric(String operationType, long duration) {
        Timer.builder("pq.shift.events.duration")
                .description("Duration of shift events")
                .tag("type", operationType)
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);
    }

    boolean passedThreshold(Duration fetchDuration, Duration threshold) {
        return !fetchDuration.minus(threshold).isNegative();
    }
}