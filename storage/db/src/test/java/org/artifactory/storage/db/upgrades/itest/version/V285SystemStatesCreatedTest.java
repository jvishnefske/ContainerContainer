package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v285;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Confirms v285 creates {@code system_states} with the documented columns, nullability
 * constraints, and supporting index on every DB dialect.
 *
 * <p>Note: the composite PK ({@code system_states_pk}) is intentionally not asserted by name.
 * MySQL always names its primary key constraint {@code PRIMARY} regardless of the DDL
 * {@code CONSTRAINT} clause, and Oracle uppercases identifiers, so a name-based check would
 * fail on both. The fact that {@link #tableCreated()} passes already proves the PK DDL
 * was accepted by the database — a failed {@code CREATE TABLE} with a PK constraint would
 * have thrown before any test ran.
 */
public class V285SystemStatesCreatedTest extends UpgradeBaseTest {

    private static final String TABLE_NAME = "system_states";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));
    }

    @Test
    public void coreColumnsPresent() throws SQLException {
        String[] columns = {"use_case_name", "id", "timestamp", "node_id", "actor", "data"};
        for (String c : columns) {
            assertTrue(columnExists(TABLE_NAME, c), "column missing: " + c);
        }
    }

    @Test
    public void requiredColumnsAreNotNullable() throws SQLException {
        assertFalse(isColumnNullable(TABLE_NAME, "use_case_name"), "use_case_name must be NOT NULL");
        assertFalse(isColumnNullable(TABLE_NAME, "id"),            "id must be NOT NULL");
        assertFalse(isColumnNullable(TABLE_NAME, "timestamp"),     "timestamp must be NOT NULL");
        assertFalse(isColumnNullable(TABLE_NAME, "node_id"),       "node_id must be NOT NULL");
    }

    @Test
    public void optionalColumnsAreNullable() throws SQLException {
        assertTrue(isColumnNullable(TABLE_NAME, "actor"), "actor must be nullable");
        assertTrue(isColumnNullable(TABLE_NAME, "data"),  "data must be nullable");
    }

    /**
     * Verifies that the {@code (use_case_name, timestamp)} supporting index was created.
     * PostgreSQL requires {@link #indexExistsByName} (backed by {@code pg_indexes});
     * all other dialects use the JDBC-metadata path via {@link #indexExist}.
     */
    @Test
    public void supportingIndexExists() throws SQLException {
        if (dbProperties.getDbType() == DbType.POSTGRESQL) {
            assertTrue(indexExistsByName("system_states_uc_ts_idx"),
                    "index system_states_uc_ts_idx must exist on PostgreSQL");
        } else {
            assertTrue(indexExist(TABLE_NAME, "use_case_name", "system_states_uc_ts_idx"),
                    "index system_states_uc_ts_idx must exist");
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v285;
    }
}
