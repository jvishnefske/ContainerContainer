package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v269;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Absence assertions that lock documented design choices for request-log tables.
 */
public class RequestLogsAbsenceAssertionsTest extends UpgradeBaseTest {

    @Test
    public void noTenantIdColumnOnRequestLogsTable() throws SQLException {
        assertFalse(columnExists("request_logs", "tenant_id"));
    }

    @Test
    public void noCreatedAtUpdatedAtColumns() throws SQLException {
        assertFalse(columnExists("request_logs", "created_at"));
        assertFalse(columnExists("request_logs", "updated_at"));
    }

    @Test
    public void noFkOnRequestLogsOnPostgresql() throws SQLException {
        testOnlyWithPostgresql();
        assertNoForeignKey("request_logs");
    }

    @Test
    public void outboundRequestLogsTableNotExistBeforeV279() throws SQLException {
        assertFalse(tableExists("outbound_request_logs"),
                "outbound_request_logs must not exist before v279");
    }

    @Test
    public void requestTimeHasNoDbLevelDefaultOnPostgresql() throws SQLException {
        testOnlyWithPostgresql();
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT column_default FROM information_schema.columns "
                                + "WHERE table_name = ? AND column_name = 'request_time'")) {
            stmt.setString(1, "request_logs");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    assertEquals(rs.getString(1), null,
                            "request_time must not have a DB-level default on request_logs");
                }
            }
        }
    }

    private void assertNoForeignKey(String table) throws SQLException {
        try (Connection conn = jdbcHelper.getDataSource().getConnection();
                PreparedStatement stmt = conn.prepareStatement(
                        "SELECT count(*) FROM information_schema.table_constraints "
                                + "WHERE table_name = ? AND constraint_type = 'FOREIGN KEY'")) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    assertEquals(rs.getInt(1), 0,
                            "expected no FOREIGN KEY constraints on " + table);
                }
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v269;
    }
}
