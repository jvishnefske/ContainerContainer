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

import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventType;
import org.artifactory.storage.event.StorageEventInfo;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.util.BaseDao;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.querybuilder.ArtifactoryQueryWriter;
import org.jfrog.common.ClockUtils;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A data access object for the events table.
 *
 * @author TamirH
 */
@Slf4j
@Repository
public class StorageEventsDao extends BaseDao {
    private final DbProperties dbProperties;
    private DbService dbService;

    private static final String SEQ_PG = ", NEXTVAL('storage_events_event_id_seq')";
    private static final String SEQ_ORA = ", storage_events_event_id_seq.NEXTVAL";
    private String insertStorageEventSql;

    // Used for creating the real insert query
    private static final String INSERT_QUERY_TEMPLATE = """
            INSERT INTO storage_events (timestamp, event_type, sha1, sha256, bin_length, data%1$s)
            VALUES(?, ?, ?, ?, ?, ? %2$s)
            """;
    private static final String SELECT_COUNT = "SELECT COUNT(*) FROM storage_events";
    private static final String DELETE_BY_SHA = "DELETE FROM storage_events WHERE sha1 IN (#)";
    private static final String DELETE_BY_EVENT_ID = "DELETE FROM storage_events WHERE event_id <= ?";
    private static final String DELETE_ALL = "DELETE FROM storage_events";
    // Here we don't have SQL-IN clause limit issue because we use IN with a sub-query clause
    // (see http://www.dbarepublic.com/2015/03/sql-in-clause-limit.html)
    // This query deletes all events except the latest one for a given sha1.
    private String deleteEventsExceptLatestBySha1 = """
            DELETE
                     FROM storage_events
                     WHERE sha1 = ?
                       AND event_id NOT IN (
                         SELECT MAX(event_id)
                         FROM storage_events
                         WHERE sha1 = ?
                         )""";

    private static final String GET_LATEST_EVENTS_BY_SHA1 = """
            SELECT *
            FROM storage_events
            WHERE event_id IN (
                SELECT MAX(event_id)
                FROM storage_events
                WHERE sha1 IN (#)
                GROUP BY sha1
                )
              """;

    @Autowired
    public StorageEventsDao(ArtifactoryJdbcHelper jdbcHelper, DbProperties dbProperties) {
        super(jdbcHelper);

        this.dbProperties = dbProperties;
    }

    @PostConstruct
    public void init() {
        final String EVENT_ID = ", event_id";
        switch (dbProperties.getDbType()) {
            case POSTGRESQL -> this.insertStorageEventSql =
                    String.format(INSERT_QUERY_TEMPLATE, EVENT_ID, SEQ_PG);
            case ORACLE -> this.insertStorageEventSql =
                    String.format(INSERT_QUERY_TEMPLATE, EVENT_ID, SEQ_ORA);
            case DERBY -> this.insertStorageEventSql =
                    String.format(INSERT_QUERY_TEMPLATE, EVENT_ID, ",?");
            case MYSQL, MARIADB -> {
                this.insertStorageEventSql = String.format(INSERT_QUERY_TEMPLATE, "", "");
                deleteEventsExceptLatestBySha1 = """
                        DELETE
                        FROM storage_events
                        WHERE sha1 = ?
                          AND event_id NOT IN (
                            SELECT seM.event_id FROM (
                                SELECT MAX(event_id) AS event_id
                                FROM storage_events
                                WHERE sha1 = ?
                                GROUP BY sha1
                            ) AS seM
                        )""";
            }
            default -> this.insertStorageEventSql = String.format(INSERT_QUERY_TEMPLATE, "", "");
        }
    }

    @Autowired
    public void setDbService(DbService dbService) {
        this.dbService = dbService;
    }

    public void create(StorageEventInfo e) throws SQLException {
        int inserted = jdbcHelper.executeUpdate(insertStorageEventSql, getQueryParameters(e));
        if (inserted == 1) {
            log.trace("Storage event {} of {} bytes were inserted", e.sha1(), e.size());
        } else {
            log.trace("Unexpected result {} of Storage event {} of {} bytes were insertion",
                    inserted, e.sha1(), e.size());
        }
    }

    public void createBulk(@NonNull List<StorageEventInfo> events) throws SQLException {
        List<List<StorageEventInfo>> partitions = Lists.partition(events, ConstantValues.dbOperationsBulkSize.getInt());
        for (List<StorageEventInfo> eventsPartition : partitions) {
            List<Object[]> params = calculateBulkParams(eventsPartition);
            jdbcHelper.executeBulkUpdate(insertStorageEventSql, params);
            // I would get more detailed information about insertion success/failure per event
            // if I had changed the underlying API to return an array of invocation results.
            log.trace("Inserted Storage events {}", params);
        }
    }

    @NonNull
    private List<Object[]> calculateBulkParams(@NonNull List<StorageEventInfo> eventsPartition) {
        List<Object[]> params = new ArrayList<>();
        for (StorageEventInfo e : eventsPartition) {
            params.add(getQueryParameters(e));
        }
        return params;
    }

    private Object[] getQueryParameters(StorageEventInfo e) {
        Object[] eventParams;
        if (DbType.DERBY.equals(dbProperties.getDbType())) {
            eventParams =
                    new Object[] {ClockUtils.epochMillis(), e.type().code(), e.sha1(), e.sha256(), e.size(),
                            StorageEventInfo.Data.toJson(e.data()), dbService.nextId()};
        } else {
            eventParams =
                    new Object[] {ClockUtils.epochMillis(), e.type().code(), e.sha1(), e.sha256(),
                            e.size(), StorageEventInfo.Data.toJson(e.data())};
        }
        return eventParams;
    }

    public long getEventsCount() throws SQLException {
        return jdbcHelper.executeSelectLong(SELECT_COUNT);
    }

    @Nonnull
    public List<StorageEventInfo> loadEventsByParams(long eventId, long limit) throws SQLException {
        try (ResultSet rs = getResultSet(eventId, limit, false)) {
            List<StorageEventInfo> entries = Lists.newArrayList();
            while (rs.next()) {
                entries.add(eventFromResultSet(rs));
            }
            return entries;
        }
    }

    public void deleteEventsBySha1(List<String> shaToDelete) throws SQLException {
        List<List<String>> partitions = Lists.partition(shaToDelete, ConstantValues.dbOperationsBulkSize.getInt());
        for (List<String> eventsPartition : partitions) {
            jdbcHelper.executeUpdate(DELETE_BY_SHA, eventsPartition);
        }
    }

    public void deleteByCursor(long cursorId) throws SQLException {
        jdbcHelper.executeUpdate(DELETE_BY_EVENT_ID, cursorId);
    }

    public int deleteAllPreviousBySHA1(String sha1) throws SQLException {
        return jdbcHelper.executeUpdate(deleteEventsExceptLatestBySha1, sha1, sha1);
    }

    /**
     * Get the latest events for a given list of SHA1s.
     *
     * @param sha1s list of SHA1s
     * @return list of latest events for the given SHA1s or an empty list if no events were found
     * @throws SQLException if an error occurred while executing the query
     */
    public List<StorageEventInfo> getLatestEventsBySHA1(@NonNull List<String> sha1s) throws SQLException {
        if (sha1s.isEmpty()) {
            return List.of();
        }
        List<StorageEventInfo> res = null;
        List<List<String>> partitions = Lists.partition(sha1s, ConstantValues.dbOperationsBulkSize.getInt());
        for (List<String> eventsPartition : partitions) {
            try (ResultSet rs = jdbcHelper.executeSelect(GET_LATEST_EVENTS_BY_SHA1, eventsPartition)) {
                while (rs.next()) {
                    if (res == null) {
                        res = new ArrayList<>((sha1s.size()));
                    }
                    res.add(eventFromResultSet(rs));
                }
            }
        }
        return Objects.requireNonNullElse(res, List.of());
    }

    @NonNull
    private ResultSet getResultSet(long eventId, Long limit, boolean count) throws SQLException {
        //prepare the sql
        String sql = new ArtifactoryQueryWriter().from("storage_events")
                .select(count ? "count(1)" : "*")
                .where("event_id > ?")
                .limit(limit == null ? Long.MAX_VALUE : limit)
                .orderBy(count ? null : "event_id ASC").build();

        return jdbcHelper.executeSelect(sql, eventId);
    }

    private StorageEventInfo eventFromResultSet(ResultSet rs) throws SQLException {
        return new StorageEventInfo(rs.getLong("event_id"),
                rs.getLong("timestamp"),
                EventType.fromCode(rs.getShort("event_type")),
                rs.getString("sha1"),
                rs.getString("sha256"),
                rs.getLong("bin_length"),
                StorageEventInfo.Data.fromJson(rs.getString("data"))
        );
    }

    public int countEventsByParams(long eventId) throws SQLException {
        try (ResultSet resultSet = getResultSet(eventId, null, true)) {
            int count = 0;
            if (resultSet.next()) {
                count = resultSet.getInt(1);
            }
            return count;
        }
    }

    /**
     * Delete all events from the event log. Internal use only!
     */
    public void deleteAll() throws SQLException {
        jdbcHelper.executeUpdate(DELETE_ALL);
    }
}
