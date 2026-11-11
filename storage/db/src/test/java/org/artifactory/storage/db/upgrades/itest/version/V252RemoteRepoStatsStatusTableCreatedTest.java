package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v252;
import static org.testng.Assert.assertTrue;
/**
 * Tests that the remote_repo_stats_status table is created.
 *
 * @author Hussein Alshqerat
 */
public class V252RemoteRepoStatsStatusTableCreatedTest extends UpgradeBaseTest {
    private static final String TABLE_NAME = "remote_repo_stats_status";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));

        assertTrue(columnExists(TABLE_NAME, "id"));
        assertTrue(columnExists(TABLE_NAME, "repository_key"));
        assertTrue(columnExists(TABLE_NAME, "repo_package_type"));
        assertTrue(columnExists(TABLE_NAME, "update_time"));
        assertTrue(columnExists(TABLE_NAME, "aggregation_type"));
        assertTrue(columnExists(TABLE_NAME, "status_code"));
        assertTrue(columnExists(TABLE_NAME, "request_count"));
        assertTrue(columnExists(TABLE_NAME, "http_method"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v252;
    }
}
