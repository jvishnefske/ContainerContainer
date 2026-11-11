package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author Yoaz Menda
 */
@Test
public class V35UpgradeJobTableTest extends UpgradeBaseTest {
    public void testJobsExists() throws SQLException {
        String tableName = "jobs";
        assertTrue(tableExists(tableName), "Missing table jobs");
        assertTrue(columnExists(tableName, "job_id"));
        assertTrue(columnExists(tableName, "job_type"));
        assertTrue(columnExists(tableName, "job_status"));
        assertTrue(columnExists(tableName, "started"));
        assertTrue(columnExists(tableName, "finished"));
        assertTrue(columnExists(tableName, "additional_details"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v35;
    }
}
