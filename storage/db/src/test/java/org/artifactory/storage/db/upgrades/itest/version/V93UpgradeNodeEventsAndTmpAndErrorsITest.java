package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.conversion.version.v227.CreateDbSpecificSequence;
import org.artifactory.storage.db.itest.DbTestUtils;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.util.IdGenerator;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.artifactory.storage.db.util.ArtifactoryDbVersionUtil.versionsBetween;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v83;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v84;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v95;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

/**
 * Test SQLs of @{@link CreateDbSpecificSequence}
 *
 * @author shaharz
 */
@SuppressWarnings("SameParameterValue")
@Test
public class V93UpgradeNodeEventsAndTmpAndErrorsITest extends UpgradeBaseTest {

    @Autowired
    private IdGenerator idGenerator;

    /**
     * Verify Upgrade
     */
    public void testUpgrade() throws Exception {
        idGenerator.initializeIdGenerator();
        for (int i = 0; i < 2100; i++) {
            idGenerator.nextId();
        }

        final List<DbVersion> dbVersions = versionsBetween(v84, v95);
        for (DbVersion dbVersion : dbVersions) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }

        // Verify upgrade
        verify(true);
    }

    private void verify(boolean value) throws SQLException {
        assertEquals(tableExists("node_events_tmp"), value);
        assertEquals(columnExists("node_events_tmp", "tmp_event_id"), value);
        assertEquals(columnExists("node_events_tmp", "timestamp"), value);
        assertEquals(columnExists("node_events_tmp", "event_type"), value);
        assertEquals(columnExists("node_events_tmp", "path"), value);
        assertEquals(columnExists("node_events_tmp", "hints"), value);
        if (!dbProperties.getDbType().equals(DbType.DERBY)) {
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_tmp", "timestamp",
                    "node_events_tmp_idx", dbProperties.getDbType()), value);
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_tmp", "tmp_event_id",
                    "node_events_tmp_idx", dbProperties.getDbType()), value);
        }

        assertEquals(columnExists("node_events", "hints"), value);
        try {
            assertEquals(isColumnNullable("node_events", "hints"), value);
        } catch (Exception e) {
            assertFalse(value, e.getMessage());
        }
        assertEquals(columnExists("node_events_errors", "event_id"), value);
        try {
            assertEquals(isColumnNullable("node_events_errors", "event_id"), value);
        } catch (Exception e) {
            assertFalse(value, e.getMessage());
        }
        assertEquals(DbUtils.foreignKeyExists(jdbcHelper, dbProperties.getDbType(), "node_events_errors",
                "node_events_errors_event_id_fk"), value);

        if (!dbProperties.getDbType().equals(DbType.DERBY)) {
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_errors", "operation_key",
                    "node_events_errors_op_key_idx", dbProperties.getDbType()), !value);
        }

        if (!dbProperties.getDbType().equals(DbType.DERBY) || value) {
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_errors", "operation_key",
                    "node_events_errors_idx", dbProperties.getDbType()), value);
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_errors", "event_category",
                    "node_events_errors_idx", dbProperties.getDbType()), value);
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_errors", "event_id",
                    "node_events_errors_idx", dbProperties.getDbType()), value);
            assertEquals(DbUtils.indexExists(jdbcHelper, "node_events_errors", "event_time",
                    "node_events_errors_idx", dbProperties.getDbType()), value);
        }

        assertEquals(isSequenceExistOrAutoIncrement(), value);

        if (value) {
            verifySequenceInitialValue();
        }
    }

    private boolean isSequenceExistOrAutoIncrement() throws SQLException {
        if (dbProperties.getDbType().equals(DbType.MYSQL) || dbProperties.getDbType().equals(DbType.MARIADB)) {
            try (ResultSet resultSet = jdbcHelper.executeSelect("SELECT event_id FROM node_events")) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                return metadata.isAutoIncrement(1);
            }
        } else {
            return DbUtils.withConnection(jdbcHelper, conn ->
                    DbTestUtils.sequenceExists(conn, dbProperties.getDbType(), "node_events_event_id_seq"));

        }
    }

    private void verifySequenceInitialValue() throws SQLException {
        if (dbProperties.getDbType().equals(DbType.MYSQL) ||
                dbProperties.getDbType().equals(DbType.MARIADB)) {

            DbUtils.withConnection(jdbcHelper, con -> {
                try (PreparedStatement stmt = con.prepareStatement(
                        "INSERT INTO node_events (timestamp, event_type, path, hints) VALUES (?, ?, ?, null)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setLong(1, System.currentTimeMillis());
                    stmt.setInt(2, 1);
                    stmt.setString(3, "path");
                    if (stmt.executeUpdate() == 1) {
                        try (ResultSet rs = stmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                long currentId = jdbcHelper.executeSelectLong(
                                        CreateDbSpecificSequence.CURRENT_ID_QUERY) +
                                        ConstantValues.nodeEventsEventIdMigrationMargin.getLong();
                                long generatedKey = rs.getLong(1);
                                if (generatedKey < currentId) {
                                    fail("New event record generated key (" + generatedKey + ") is invalid");
                                }
                            } else {
                                fail("New event record has no generated key!");
                            }
                        }
                    } else {
                        fail("New event record was not inserted!");
                    }
                }
                return null;
            });
        } else {
            String selectSeqValue;
            switch (dbProperties.getDbType()) {
                case POSTGRESQL:
                    selectSeqValue = "SELECT NEXTVAL('node_events_event_id_seq')";
                    break;
                case ORACLE:
                    selectSeqValue = "SELECT node_events_event_id_seq.NEXTVAL from DUAL";
                    break;
                case DERBY:
                    selectSeqValue = "SELECT NEXT VALUE FOR node_events_event_id_seq FROM SYS.SYSSEQUENCES";
                    break;
                case MSSQL:
                    selectSeqValue = "SELECT NEXT VALUE FOR node_events_event_id_seq";
                    break;
                default:
                    throw new RuntimeException("Invalid DB type: " + dbProperties.getDbType());
            }

            long seqValue = jdbcHelper.executeSelectLong(selectSeqValue);
            long currentId =
                    jdbcHelper.executeSelectLong(CreateDbSpecificSequence.CURRENT_ID_QUERY) +
                            ConstantValues.nodeEventsEventIdMigrationMargin.getLong();
            if (seqValue < currentId) {
                fail("Invalid sequence initial value: " + seqValue);
            }
        }
    }
    
    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v83;
    }
}
