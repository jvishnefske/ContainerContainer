package org.artifactory.storage.db.event.itest.dao;

import lombok.SneakyThrows;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.event.dao.EventsDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class OracleShiftEventsWithIndexHintTest extends DbBaseTest {

    @Autowired
    private EventsDao eventsDao;

    @BeforeMethod
    public void setup() throws SQLException {
        // Skip test if not running on Oracle database
        if (!isOracleDb()) {
            throw new SkipException("This test is only for Oracle database");
        }

        // Clean up any existing test data
        cleanupTestData();

        setUsePkeyIndexHintProperty(false);
        // Import test data from SQL file but do not shift events yet
        importSql("/sql/events.sql", false);
    }

    @AfterMethod
    public void cleanup() throws SQLException {
        if (isOracleDb()) {
            cleanupTestData();
        }
    }

    @DataProvider(name = "hintEnabledProvider")
    public Object[][] hintEnabledProvider() {
        return new Object[][] {
            {false, "with index hint disabled"}, // Default
            {true, "with index hint enabled"}
        };
    }

    @SneakyThrows
    @Test(dataProvider = "hintEnabledProvider")
    public void testOracleShiftEventsWithIndexHint(boolean hintEnabled, String description) {
        try {
            setUsePkeyIndexHintProperty(hintEnabled);

            // Reinitialize EventsDao to pick up the property value
            eventsDao.init();

            // Verify that EventsDao is configured with the correct SQL
            verifyOracleHintSqlIsSet(hintEnabled);

            // Verify test data was inserted
            int initialTmpEventsCount = countTmpEvents();
            assertTrue(initialTmpEventsCount > 0, "Test data should be inserted in tmp table");

            int initialEventsCount = countEvents();

            // Execute shift and delete operation
            Set<String> affectedRepos = eventsDao.shiftAndDeleteTmpEvents(false);

            // Verify results
            assertEquals(affectedRepos.size(), 3, "Should affect 3 repositories: repo1, repo2, repo-no-create");
            assertTrue(affectedRepos.contains("repo1"), "Should include repo1");
            assertTrue(affectedRepos.contains("repo2"), "Should include repo2");
            assertTrue(affectedRepos.contains("repo-no-create"), "Should include repo-no-create");

            // Verify events were moved from tmp to main table
            int finalTmpEventsCount = countTmpEvents();
            int finalEventsCount = countEvents();

            assertEquals(finalTmpEventsCount, 0, "All tmp events should be deleted " + description);
            assertEquals(finalEventsCount, initialEventsCount + initialTmpEventsCount,
                    "All events should be moved to main events table " + description);

        } finally {
            // Always reset to default state
            setUsePkeyIndexHintProperty(false);
            eventsDao.init();
        }
    }

    private void setUsePkeyIndexHintProperty(boolean hintEnabled) {
        artifactoryHomeBoundTest.setStringSystemProperty(
                ConstantValues.shiftEventsOracleDbUsePKeyForDeleteTempEvents, hintEnabled ? "true" : "false");
    }

    /**
     * Verifies that the EventsDao is configured with the correct SQL statement based on the hint property
     */
    private void verifyOracleHintSqlIsSet(boolean hintEnabled) {
        try {
            String actualDeleteSql = getDeleteTmpEventsSql();

            if (hintEnabled) {
                assertTrue(actualDeleteSql.contains(EventsDao.PKEY_INDEX_HINT_ORACLE),
                        "EventsDao should be configured with Oracle hint SQL when property is enabled");
            } else {
                assertFalse(actualDeleteSql.contains(EventsDao.PKEY_INDEX_HINT_ORACLE),
                        "EventsDao should be configured with regular SQL when property is disabled");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify Oracle hint SQL configuration", e);
        }
    }

    /**
     * Uses reflection to access the private deleteTmpEventsSql field to verify the correct SQL is configured
     */
    private String getDeleteTmpEventsSql() {
        try {
            Field deleteTmpEventsSqlField = EventsDao.class.getDeclaredField("deleteTmpEventsSql");
            deleteTmpEventsSqlField.setAccessible(true);
            return (String) deleteTmpEventsSqlField.get(eventsDao);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access deleteTmpEventsSql field via reflection", e);
        }
    }

    private void cleanupTestData() throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection()) {
            // Clean up test data from both tables using tmp_event_id range from events.sql
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM node_events_tmp")) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM node_events")) {
                ps.executeUpdate();
            }
        }
    }

    private int countTmpEvents() throws SQLException {
        return countEventsInTable(EventsDao.EVENTS_TEMP_TABLE);
    }

    private int countEvents() throws SQLException {
        return countEventsInTable(EventsDao.EVENTS_TABLE);
    }

    private int countEventsInTable(String tableName) throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM %s".formatted(tableName))) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
