
package org.artifactory.storage.db.event.itest.dao;

import org.artifactory.storage.db.event.dao.eventTableRepoKeyMigration.EventTableRepoKeyMigrationDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.jfrog.converter.util.PreDefinedConditions.indexExists;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;


/**
 * Units tests for {@link EventTableRepoKeyMigrationDao}.
 *
 * @author Daniel siman tov
 */
@Test
public class EventTableRepoKeyMigrationDaoTest extends DbBaseTest {

    public static final String EVENTS_TABLE = "node_events";
    public static final String EVENTS_INDEX_NAME = "node_events_repo_event_id";
    public static final String REPO_COLUMN_NAME = "repo";
    public static final String EVENT_ID_COLUMN_NAME = "event_id";
    private DbType dbType;

    @BeforeMethod
    public void cleanEnvBeforeClass() {
        dbType = dbProperties.getDbType();
        cleanDao();
    }

    @AfterClass
    public void cleanEnvAfterClass() {
        cleanDao();
    }

    @Autowired
    private EventTableRepoKeyMigrationDao eventTableRepoKeyMigrationDao;

    @BeforeClass
    public void setup() throws Exception {
        importSql("/sql/events-with-hints.sql");
    }

    @Test
    public void testFinishedMigration() throws SQLException {
        // add migration record in the DB
        jdbcHelper.executeUpdate(
                "insert into migration_status (identifier, started, finished, migration_info_blob)\n" +
                        "values ('event-table-repo-key-migration',3123,4234,null)");
        // execute finished migration function
        eventTableRepoKeyMigrationDao.finishedMigration();

        // test that the finished column has been updated by query the field with finished
        int ifMigrationFinished = jdbcHelper.executeSelectCount(
                "select COUNT(finished) from migration_status where identifier = 'event-table-repo-key-migration' " +
                        "and " +
                        "finished > 0");
        assertEquals(ifMigrationFinished, 1);
    }

    @Test
    public void testUpdateRepoInNodeEvent() throws SQLException {
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        // update repo column
        eventTableRepoKeyMigrationDao.updateRepoInNodeEvent();
        // test that there are rows
        int result = jdbcHelper.executeSelectCount(
                "select count(*) from node_events where repo is not null");
        assertNotEquals(result, 0);

        // test that all the rows has been updated
        result = jdbcHelper.executeSelectCount(
                "select count(*) from node_events where repo is null");
        assertEquals(result, 0);
    }

    @Test
    public void testAddIndexOnRepoAndEventIdPositive() throws SQLException {
        // We skip the test because our testing env doesn't include the enterprise edition
        // see https://jfrog-int.atlassian.net/browse/RTDEV-33360
        if (DbType.ORACLE.equals(dbProperties.getDbType())) {
            String message = "This test is not for Oracle. Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        // config env - add repo column
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        // should add index on repo column
        eventTableRepoKeyMigrationDao.addIndexOnRepoAndEventId();
        // test if index exist
        Connection connection = jdbcHelper.getDataSource()
                .getConnection();
        boolean resultIfIndexExist =
                indexExists(EVENTS_TABLE, EVENTS_INDEX_NAME, List.of(REPO_COLUMN_NAME, EVENT_ID_COLUMN_NAME)).test(
                        connection, dbType);
        DbUtils.close(connection);
        assertTrue(resultIfIndexExist);
    }

    @Test
    public void testAddIndexOnRepoAndEventIdNegative() throws SQLException {
        // We skip the test because our testing env doesn't include the enterprise edition
        // see https://jfrog-int.atlassian.net/browse/RTDEV-33360
        if (DbType.ORACLE.equals(dbProperties.getDbType())) {
            String message = "This test is not for Oracle. Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        cleanDao();
        try {
            jdbcHelper.executeUpdate(
                    "drop index node_events_repo_event_id");
        } catch (Exception e) {
            // drop index
        }
        // config env - add repo column
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        // should add index on repo column
        eventTableRepoKeyMigrationDao.addIndexOnRepoAndEventId();
        // should not throw exception if index already exist
        eventTableRepoKeyMigrationDao.addIndexOnRepoAndEventId();
        // test if index exist
        Connection connection = jdbcHelper.getDataSource()
                .getConnection();
        boolean resultIfIndexExist =
                indexExists(EVENTS_TABLE, EVENTS_INDEX_NAME, List.of(REPO_COLUMN_NAME, EVENT_ID_COLUMN_NAME)).test(
                        connection, dbType);
        DbUtils.close(connection);
        assertTrue(resultIfIndexExist);
    }

    @Test
    public void testAddRepoColumnPositive() throws SQLException {
        // config env - add repo column
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        // test if column exist
        assertTrue(DbUtils.columnExists(jdbcHelper, dbType, EVENTS_TABLE, REPO_COLUMN_NAME));
    }

    @Test
    public void testAddRepoColumnNegative() throws SQLException {
        // config env - add repo column
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        // should not throw exception when the repo column already exist
        eventTableRepoKeyMigrationDao.handleAddRepoColumn();
        // test if column exist
        assertTrue(DbUtils.columnExists(jdbcHelper, dbType, EVENTS_TABLE, REPO_COLUMN_NAME));
    }

    @Test(description = "should return true when there is already record that finished successfully")
    public void testCheckIfMigrationFinishedPositive() throws SQLException {
        // add record with finished time zero (not finished probably because an error)
        jdbcHelper.executeUpdate(
                "insert into migration_status (identifier, started, finished, migration_info_blob)\n" +
                        "values ('event-table-repo-key-migration',3123,434,null)");

        // config env - should not fail when execute checkIfMigrationFinished with empty server
        boolean ifMigrationFinished = eventTableRepoKeyMigrationDao.checkIfMigrationFinished("server1");
        assertTrue(ifMigrationFinished);
    }

    @Test(description = "should return false when there is no record in the DB and in the memory")
    public void testCheckIfMigrationFinishedNegative1() throws SQLException {
        // config env - execute checkIfMigrationFinished
        boolean ifMigrationFinished = eventTableRepoKeyMigrationDao.checkIfMigrationFinished("server1");
        assertFalse(ifMigrationFinished);
        // check row exist in MIGRATION_STATUS table as excepted
        try (ResultSet rs = jdbcHelper.executeSelect(
                "select * from migration_status where identifier = 'event-table-repo-key-migration'")) {
            if (rs.next()) {
                assertEquals(rs.getString("identifier"), "event-table-repo-key-migration");
                assertNotEquals(rs.getLong("started"), 0);
                assertEquals(rs.getLong("finished"), 0);
            }
        }

    }

    @Test(description = "should return false when there is no record in the DB and in the memory")
    public void testCheckIfMigrationFinishedNegative2() throws SQLException {
        // config env - should not fail when execute checkIfMigrationFinished with empty server
        boolean ifMigrationFinished = eventTableRepoKeyMigrationDao.checkIfMigrationFinished("");
        assertFalse(ifMigrationFinished);

        // check row exist in MIGRATION_STATUS table as excepted
        try (ResultSet rs = jdbcHelper.executeSelect(
                "select * from migration_status where identifier = 'event-table-repo-key-migration'")) {
            if (rs.next()) {
                assertEquals(rs.getString("identifier"), "event-table-repo-key-migration");
                assertNotEquals(rs.getLong("started"), 0);
                assertEquals(rs.getLong("finished"), 0);
            }
        }
    }

    @Test(description = "should return false when there is already record that not finished")
    public void testCheckIfMigrationFinishedNegative3() throws SQLException {
        // add record with finished time zero (not finished probably because an error)
        jdbcHelper.executeUpdate(
                "insert into migration_status (identifier, started, finished, migration_info_blob)\n" +
                        "values ('event-table-repo-key-migration',3123,0,null)");

        // config env - should not fail when execute checkIfMigrationFinished with empty server
        boolean ifMigrationFinished = eventTableRepoKeyMigrationDao.checkIfMigrationFinished("server1");
        assertFalse(ifMigrationFinished);
    }

    @Test(description = "should return false if it already enabled in memory and should not check in the DB")
    public void testCheckIfMigrationFinishedNegative4() throws SQLException {
        eventTableRepoKeyMigrationDao.finishedMigration();
        // delete the record from the DB to verify it not been created after running checkIfMigrationFinished
        try {
            jdbcHelper.executeUpdate(
                    "delete from migration_status where identifier = 'event-table-repo-key-migration'");
        } catch (Exception e) {
            //
        }
        // execute checkIfMigrationFinished
        boolean ifMigrationFinished = eventTableRepoKeyMigrationDao.checkIfMigrationFinished("server1");
        assertTrue(ifMigrationFinished);

        // test that there are no rows in migration status been created and only test the variable in memory
        int result = jdbcHelper.executeSelectCount(
                "select COUNT(finished) from migration_status where identifier = 'event-table-repo-key-migration' ");
        assertEquals(result, 0);
    }

    private void cleanDao() {
        try {
            jdbcHelper.executeUpdate(
                    "Alter table node_events DROP COLUMN repo");
        } catch (Exception e) {
            //
        }
        try {
            jdbcHelper.executeUpdate(
                    "drop index node_events_repo_event_id ON node_events");
        } catch (Exception e) {
            //
        }
        if (dbType.name().equals("DERBY")) {
            try {
                jdbcHelper.executeUpdate(
                        "drop index node_events_repo_event_id");
            } catch (Exception e) {
                // drop index for derby
            }
        }
        try {
            jdbcHelper.executeUpdate(
                    "delete from migration_status where identifier = 'event-table-repo-key-migration'");
        } catch (Exception e) {
            //
        }
    }

}