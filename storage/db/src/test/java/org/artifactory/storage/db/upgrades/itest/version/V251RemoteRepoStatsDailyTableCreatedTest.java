package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v251;
import static org.testng.Assert.assertTrue;

/**
 * Tests that the remote_repo_stats_daily table is created.
 *
 * @author Hussein Alshqerat
 */
public class V251RemoteRepoStatsDailyTableCreatedTest extends UpgradeBaseTest {
    private static final String TABLE_NAME = "remote_repo_stats_daily";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));

        assertTrue(columnExists(TABLE_NAME, "id"));
        assertTrue(columnExists(TABLE_NAME, "repository_key"));
        assertTrue(columnExists(TABLE_NAME, "repo_package_type"));
        assertTrue(columnExists(TABLE_NAME, "update_time"));
        assertTrue(columnExists(TABLE_NAME, "total_request_count"));
        assertTrue(columnExists(TABLE_NAME, "total_response_time_ms"));
        assertTrue(columnExists(TABLE_NAME, "max_response_time_ms"));
        assertTrue(columnExists(TABLE_NAME, "ttfb_avg"));
        assertTrue(columnExists(TABLE_NAME, "total_response_size"));
        assertTrue(columnExists(TABLE_NAME, "status_2xx_count"));
        assertTrue(columnExists(TABLE_NAME, "status_3xx_count"));
        assertTrue(columnExists(TABLE_NAME, "status_4xx_count"));
        assertTrue(columnExists(TABLE_NAME, "status_5xx_count"));
        assertTrue(columnExists(TABLE_NAME, "http_method"));
        assertTrue(columnExists(TABLE_NAME, "transfer_rate"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v251;
    }
}
