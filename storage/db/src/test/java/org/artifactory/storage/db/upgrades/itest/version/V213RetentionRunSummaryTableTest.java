package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;


import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v213;
import static org.testng.Assert.assertTrue;

public class V213RetentionRunSummaryTableTest extends UpgradeBaseTest {
    private static final String TABLE_NAME = "retention_run_summary";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));

        assertTrue(columnExists(TABLE_NAME, "id"));
        assertTrue(columnExists(TABLE_NAME, "policy_key"));
        assertTrue(columnExists(TABLE_NAME, "run_id"));
        assertTrue(columnExists(TABLE_NAME, "dry_run"));
        assertTrue(columnExists(TABLE_NAME, "started_date"));
        assertTrue(columnExists(TABLE_NAME, "finished_date"));
        assertTrue(columnExists(TABLE_NAME, "retention_type"));
        assertTrue(columnExists(TABLE_NAME, "status"));
        assertTrue(columnExists(TABLE_NAME, "item_type"));
        assertTrue(columnExists(TABLE_NAME, "processed_items_count"));
        assertTrue(columnExists(TABLE_NAME, "failed_items_count"));
        assertTrue(columnExists(TABLE_NAME, "total_items_count"));
        assertTrue(columnExists(TABLE_NAME, "processed_physical_binary_size"));
        assertTrue(columnExists(TABLE_NAME, "processed_virtual_binary_size"));
        assertTrue(columnExists(TABLE_NAME, "total_binary_size"));
        assertTrue(columnExists(TABLE_NAME, "triggered_by"));
        assertTrue(columnExists(TABLE_NAME, "temp_nodes_cleaned"));

        String rKeyRunIdIndexName = "retention_run_summary_rkey_run_id_idx";
        String processedVirtualBinarySizeIndexName = "retention_run_summary_processed_virtual_binary_size_idx";
        String statusIndexName = "retention_run_summary_status_idx";
        String retentionTypeIndexName = "retention_run_summary_retention_type_idx";

        assertTrue(indexExist(TABLE_NAME, "status", statusIndexName));
        assertTrue(indexExist(TABLE_NAME, "retention_type", retentionTypeIndexName));
        assertTrue(indexExist(TABLE_NAME, "processed_virtual_binary_size", processedVirtualBinarySizeIndexName));
        assertTrue(indexExist(TABLE_NAME, "policy_key", rKeyRunIdIndexName));
        assertTrue(indexExist(TABLE_NAME, "run_id", rKeyRunIdIndexName));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v213;
    }

}
