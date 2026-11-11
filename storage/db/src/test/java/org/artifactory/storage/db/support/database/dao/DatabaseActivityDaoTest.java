package org.artifactory.storage.db.support.database.dao;

import lombok.SneakyThrows;
import org.artifactory.storage.db.support.database.model.DatabaseActivity;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.jfrog.storage.QueryControls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseActivityDaoTest {

    private ArtifactoryJdbcHelper jdbcHelper;

    private DatabaseActivityDao databaseActivityDao;

    private ResultSet rsMock;

    private static final String POSTGRESQL_SLOW_QUERY_STATEMENT =
            " SELECT pid, TO_CHAR(age(clock_timestamp(), query_start), 'HH24:MI:SS') AS age, usename, " +
                    "state, wait_event_type, wait_event, query FROM pg_stat_activity WHERE state != 'idle' AND query " +
                    "NOT LIKE '%pg_stat_activity%' ORDER BY age DESC LIMIT 10";

    @BeforeEach
    void setUp() {
        jdbcHelper = mock(ArtifactoryJdbcHelper.class);
        databaseActivityDao = new DatabaseActivityDao(jdbcHelper);
    }

    @Test
    @SneakyThrows
    void testGetSlowQueriesFromDatabase() {
        rsMock = mock(ResultSet.class);
        when(rsMock.next()).thenReturn(true).thenReturn(false);
        when(rsMock.getInt(1)).thenReturn(116);
        when(rsMock.getString(2)).thenReturn("00:05:00");
        when(rsMock.getString(3)).thenReturn("artifactory");
        when(rsMock.getString(4)).thenReturn("active");
        when(rsMock.getString(5)).thenReturn("Client");
        when(rsMock.getString(6)).thenReturn("ClientRead");
        when(rsMock.getString(7)).thenReturn("select timestamp, event_type, path, hints, temp_event_id " +
                "from node_events_temp order by timestamp, temp_event_id limit  1000 ;");
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString())).thenReturn(rsMock);
        DbType databaseType = DbType.POSTGRESQL;
        List<DatabaseActivity> databaseActivityList =
                databaseActivityDao.getSlowQueriesFromDatabase(
                        POSTGRESQL_SLOW_QUERY_STATEMENT, databaseType);
        assertEquals(116, databaseActivityList.get(0).getPid());
        assertEquals("00:05:00", databaseActivityList.get(0).getAge());
        assertEquals("artifactory", databaseActivityList.get(0).getUserName());
        assertEquals("active", databaseActivityList.get(0).getState());
        assertEquals("Client", databaseActivityList.get(0).getWaitEventType());
        assertEquals("ClientRead", databaseActivityList.get(0).getWaitEvent());
        assertEquals(
                "select timestamp, event_type, path, hints, temp_event_id from node_events_temp order by timestamp, " +
                        "temp_event_id limit  1000 ;",
                databaseActivityList.get(0).getQuery());
    }

    @Test
    @SneakyThrows
    void testGetSlowQueriesFromDatabaseForSQLException() {
        rsMock = mock(ResultSet.class);
        when(rsMock.next()).thenThrow(new SQLException("Testing SQL Exception"));
        when(jdbcHelper.executeSelect(any(QueryControls.class), anyString())).thenReturn(rsMock);
        DbType databaseType = DbType.POSTGRESQL;
        databaseActivityDao.getSlowQueriesFromDatabase(
                POSTGRESQL_SLOW_QUERY_STATEMENT, databaseType);
    }
}