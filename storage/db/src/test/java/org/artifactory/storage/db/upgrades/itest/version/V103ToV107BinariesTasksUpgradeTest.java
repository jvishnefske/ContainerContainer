package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.Assert;

import java.sql.SQLException;

public class V103ToV107BinariesTasksUpgradeTest extends UpgradeBaseTest {

    public void testTableExists() throws SQLException {
        String tbName = "binaries_tasks";
        Assert.assertTrue(tableExists(tbName));
        Assert.assertTrue(
                indexExist(tbName, "time", "binaries_tasks_time_idx"));
        Assert.assertTrue(
                indexExist(tbName, "start_time", "binaries_tasks_start_time_idx"));
        Assert.assertTrue(
                indexExist(tbName, "server_id", "binaries_tasks_server_id_idx"));
        Assert.assertTrue(
                indexExist(tbName, "count", "binaries_tasks_count_idx"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v107;
    }

}
