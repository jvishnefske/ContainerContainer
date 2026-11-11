package org.artifactory.support.core.collectors.database;

import jakarta.annotation.Nonnull;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.storage.db.DbService;
import org.artifactory.storage.db.support.database.dao.DatabaseActivityDao;
import org.artifactory.storage.db.support.database.model.DatabaseActivity;
import org.artifactory.support.constant.SupportBundleConstants;
import org.jfrog.common.JsonUtils;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author vigneshc
 */

class ArtifactoryDatabaseSlowQueriesCollectorTest {

    private static final String DATABASE_SLOW_QUERIES = "[ {\n" +
            "  \"pid\" : 116,\n" +
            "  \"age\" : \"\"00:05:00\"\",\n" +
            "  \"user_name\" : \"artifactory\",\n" +
            "  \"state\" : \"active\",\n" +
            "  \"waitEventType\" : \"Client\",\n" +
            "  \"waitEvent\" : \"ClientRead\",\n" +
            "  \"query\" : \"select timestamp, event_type, path, hints, temp_event_id from node_events_temp order" +
            " by timestamp, temp_event_id limit  1000 ;\"\n" +
            "} ]";
    private JsonUtils jsonUtils;
    private DbService dbService;
    private DatabaseActivityDao databaseActivityDao;
    private ArtifactoryDatabaseSlowQueriesCollector artifactoryDatabaseSlowQueriesCollector;

    @BeforeEach
    void setUp() {
        dbService = mock(DbService.class);
        jsonUtils = mock(JsonUtils.class);
        databaseActivityDao = mock(DatabaseActivityDao.class);
        artifactoryDatabaseSlowQueriesCollector = new ArtifactoryDatabaseSlowQueriesCollector(
                dbService, jsonUtils, databaseActivityDao);
    }

    @Test
    void collectSlowQueriesFromDatabaseForDerby() {
        DbType databaseType = DbType.DERBY;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(SupportBundleConstants.UN_SUPPORTED_DERBY_DATABASE_RESPONSE, content);
    }

    @Test
    void collectSlowQueriesFromDatabaseForOracle() {
        DbType databaseType = DbType.ORACLE;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(SupportBundleConstants.UN_SUPPORTED_ORACLE_DATABASE_RESPONSE, content);
    }

    @Test
    void collectSlowQueriesFromDatabaseEmptyResponse() {
        List list = new ArrayList();
        when(databaseActivityDao.getSlowQueriesFromDatabase(any(), any())).thenReturn(list);
        DbType databaseType = DbType.POSTGRESQL;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(SupportBundleConstants.EMPTY_RESPONSE_FROM_DATABASE, content);
    }

    @Test
    void collectSlowQueriesFromDatabaseForPostgresql() {
        List<DatabaseActivity> databaseActivities = getDatabaseActivities();
        when(databaseActivityDao.getSlowQueriesFromDatabase(any(), any())).thenReturn(databaseActivities);
        DbType databaseType = DbType.POSTGRESQL;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Mockito.when(jsonUtils.valueToString(anyList(), anyBoolean())).thenReturn(DATABASE_SLOW_QUERIES);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(DATABASE_SLOW_QUERIES, content);
    }

    @Test
    void collectSlowQueriesFromDatabaseForMysql() {
        List<DatabaseActivity> databaseActivities = getDatabaseActivities();
        when(databaseActivityDao.getSlowQueriesFromDatabase(any(), any())).thenReturn(databaseActivities);
        DbType databaseType = DbType.MYSQL;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Mockito.when(jsonUtils.valueToString(anyList(), anyBoolean())).thenReturn(DATABASE_SLOW_QUERIES);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(DATABASE_SLOW_QUERIES, content);
    }

    @Test
    void collectSlowQueriesFromDatabaseForMariaDB() {
        List<DatabaseActivity> databaseActivities = getDatabaseActivities();
        when(databaseActivityDao.getSlowQueriesFromDatabase(any(), any())).thenReturn(databaseActivities);
        DbType databaseType = DbType.MARIADB;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Mockito.when(jsonUtils.valueToString(anyList(), anyBoolean())).thenReturn(DATABASE_SLOW_QUERIES);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(DATABASE_SLOW_QUERIES, content);
    }

    @Test
    void collectSlowQueriesFromDatabaseForMssql() {
        List<DatabaseActivity> databaseActivities = getDatabaseActivities();
        when(databaseActivityDao.getSlowQueriesFromDatabase(any(), any())).thenReturn(databaseActivities);
        DbType databaseType = DbType.MSSQL;
        when(dbService.getDatabaseType()).thenReturn(databaseType);
        Mockito.when(jsonUtils.valueToString(anyList(), anyBoolean())).thenReturn(DATABASE_SLOW_QUERIES);
        Object content = ReflectionTestUtils.invokeMethod(artifactoryDatabaseSlowQueriesCollector,
                "collectSlowQueriesFromDatabase");
        assertEquals(DATABASE_SLOW_QUERIES, content);
    }

    @Nonnull
    private static List<DatabaseActivity> getDatabaseActivities() {
        List<DatabaseActivity> databaseActivityList = Lists.newArrayList();
        DatabaseActivity databaseActivity =
                new DatabaseActivity(116, "00:05:00", "artifactory", "active", "Client", "ClientRead",
                        "select timestamp, event_type, path, hints, temp_event_id from node_events_temp order by " +
                                "timestamp, temp_event_id limit  1000 ;");
        databaseActivityList.add(databaseActivity);
        return databaseActivityList;
    }
}