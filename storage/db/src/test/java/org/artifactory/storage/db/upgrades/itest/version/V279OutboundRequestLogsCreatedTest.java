package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v279;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Confirms v279 creates {@code outbound_request_logs} with documented columns and indexes on every DB.
 * PostgreSQL additionally gets BRIN and {@code search_vector} (GIN built concurrently in v283).
 */
public class V279OutboundRequestLogsCreatedTest extends UpgradeBaseTest {

    private static final String TABLE_NAME = "outbound_request_logs";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));
    }

    @Test
    public void coreColumnsPresent() throws SQLException {
        String[] columns = {"id", "request_time", "trace_id", "repo_key", "http_method",
                "request_url", "status_code", "duration_ms"};
        for (String c : columns) {
            assertTrue(columnExists(TABLE_NAME, c), "column missing: " + c);
        }
    }

    @Test
    public void userAgentColumnAbsent() throws SQLException {
        assertFalse(columnExists(TABLE_NAME, "user_agent"),
                "outbound_request_logs must not have a user_agent column");
    }

    @Test
    public void searchVectorOnlyOnPostgresql() throws SQLException {
        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            assertTrue(columnExists(TABLE_NAME, "search_vector"),
                    "search_vector must exist on PostgreSQL");
        } else {
            assertFalse(columnExists(TABLE_NAME, "search_vector"),
                    "search_vector must not exist on non-PG DBs");
        }
    }

    @Test
    public void postgresqlBrinAndGinIndexes() throws SQLException {
        testOnlyWithPostgresql();
        assertTrue(indexExistsByName("outbound_rl_request_time_idx"));
    }

    @Test
    public void btreeIndexesCreated() throws SQLException {
        DbType dbType = dbProperties.getDbType();
        if (dbType == DbType.POSTGRESQL) {
            assertTrue(indexExistsByName("outbound_rl_status_time_idx"));
            assertTrue(indexExistsByName("outbound_rl_trace_idx"));
        } else if (dbType == DbType.MYSQL || dbType == DbType.MARIADB) {
            assertTrue(indexExist(TABLE_NAME, "request_time", "outbound_rl_request_time_idx"));
            assertTrue(indexExist(TABLE_NAME, "status_code", "outbound_rl_status_time_idx"));
            assertTrue(indexExist(TABLE_NAME, "request_url", "outbound_rl_search_ft_idx"));
        } else {
            assertTrue(indexExist(TABLE_NAME, "request_time", "outbound_rl_request_time_idx"));
            assertTrue(indexExist(TABLE_NAME, "status_code", "outbound_rl_status_time_idx"));
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v279;
    }
}
