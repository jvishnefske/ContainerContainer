package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

public class V191CleanupRunSummaryTableTest extends UpgradeBaseTest {

    private static final String TABLE_NAME = "cleanup_run_summary";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));

        assertTrue(columnExists(TABLE_NAME, "policy_key"));
        assertTrue(columnExists(TABLE_NAME, "policy_run_id"));
        assertTrue(columnExists(TABLE_NAME, "dry_run"));
        assertTrue(columnExists(TABLE_NAME, "started_date"));
        assertTrue(columnExists(TABLE_NAME, "finished_date"));
        assertTrue(columnExists(TABLE_NAME, "status"));
        assertTrue(columnExists(TABLE_NAME, "type"));
        assertTrue(columnExists(TABLE_NAME, "temp_nodes_cleaned"));
        assertTrue(columnExists(TABLE_NAME, "estimated_packages_count"));
        assertTrue(columnExists(TABLE_NAME, "deleted_packages_count"));
        assertTrue(columnExists(TABLE_NAME, "failed_packages_count"));
        assertTrue(columnExists(TABLE_NAME, "deleted_artifacts_count"));
        assertTrue(columnExists(TABLE_NAME, "failed_artifacts_count"));
        assertTrue(columnExists(TABLE_NAME, "original_binary_size"));
        assertTrue(columnExists(TABLE_NAME, "estimated_deleted_binary_size"));
        assertTrue(columnExists(TABLE_NAME, "triggered_by"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v191;
    }
}
