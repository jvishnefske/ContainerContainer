package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v233;
import static org.testng.Assert.assertTrue;

public class V233TrafficSummaryTableTest extends UpgradeBaseTest {
    private static final String TABLE_NAME = "traffic_summary";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));

        assertTrue(columnExists(TABLE_NAME, "id"));
        assertTrue(columnExists(TABLE_NAME, "time"));
        assertTrue(columnExists(TABLE_NAME, "total_request_count"));
        assertTrue(columnExists(TABLE_NAME, "upload_request_count"));
        assertTrue(columnExists(TABLE_NAME, "download_request_count"));
        assertTrue(columnExists(TABLE_NAME, "total_bytes"));
        assertTrue(columnExists(TABLE_NAME, "upload_bytes"));
        assertTrue(columnExists(TABLE_NAME, "download_bytes"));

        String timeIndexName = "traffic_summary_time_idx";
        assertTrue(indexExist(TABLE_NAME, "time", timeIndexName));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v233;
    }

}
