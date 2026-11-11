package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

public class V197AlterCleanupRunSummaryTableTest extends UpgradeBaseTest {

    private static final String TABLE_NAME = "cleanup_run_summary";

    @Test
    public void testEstimatedPackagesCountDoesNotExist() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));
        assertFalse(columnExists(TABLE_NAME, "estimated_packages_count"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v197;
    }
}
