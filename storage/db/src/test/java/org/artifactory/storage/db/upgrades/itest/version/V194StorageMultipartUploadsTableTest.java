package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;
import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

public class V194StorageMultipartUploadsTableTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        String tableName = "storage_multipart_uploads";
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, "upload_id"));
        assertTrue(columnExists(tableName, "created_date"));
        assertTrue(columnExists(tableName, "temp_path"));
        assertTrue(columnExists(tableName, "status"));
        assertTrue(columnExists(tableName, "modified_date"));
        assertTrue(columnExists(tableName, "data"));
        assertTrue(indexExist(tableName, "created_date", "storage_mpu_created_date_idx"));
        assertTrue(indexExist(tableName, "status", "storage_mpu_status_idx"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v194;
    }
}
