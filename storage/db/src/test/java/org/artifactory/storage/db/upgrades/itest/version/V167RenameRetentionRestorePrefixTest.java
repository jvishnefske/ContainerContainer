package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Oleksandr Legenkiy
 */
public class V167RenameRetentionRestorePrefixTest extends UpgradeBaseTest {

    @Test
    public void testPrefixRenamedSuccessfully() throws SQLException {
        setup();
        try (final ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), ArtifactoryDBVersion.v167)) {
            ArtifactoryDBVersion.v167.converter().convert(context);
        }

        assertFalse(checkIfMigrationStatusExistsById("retention_restore_some_test_id"));
        assertTrue(checkIfMigrationStatusExistsById("retention_restore_artifact_some_test_id"));

        assertFalse(checkIfMigrationStatusExistsById("retention_restore_some_test_id2"));
        assertTrue(checkIfMigrationStatusExistsById("retention_restore_artifact_some_test_id2"));

        assertTrue(checkIfMigrationStatusExistsById("retention_restore_package_some_test_id3"));
    }

    private void setup() throws SQLException {
        if (dbProperties.getDbType() == DbType.DERBY) {
            importSql("/sql/derby_migration_status_remove_not_null_blob_constraint.sql");
        }
        importSql("/sql/insert_migration_status_records.sql");
        assertTrue(checkIfMigrationStatusExistsById("retention_restore_some_test_id"));
        assertTrue(checkIfMigrationStatusExistsById("retention_restore_some_test_id2"));
        assertTrue(checkIfMigrationStatusExistsById("retention_restore_package_some_test_id3"));
    }

    private boolean checkIfMigrationStatusExistsById(String id) throws SQLException {
        try (ResultSet resultSet = jdbcHelper.executeSelect(
                "SELECT * FROM migration_status WHERE identifier = '" + id + "'")) {
            return resultSet.next();
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v166;
    }
}
