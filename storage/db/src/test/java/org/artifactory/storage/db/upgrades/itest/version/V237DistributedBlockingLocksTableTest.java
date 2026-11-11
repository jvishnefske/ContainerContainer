package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v237;
import static org.testng.Assert.assertTrue;

public class V237DistributedBlockingLocksTableTest extends UpgradeBaseTest {
    private static final String TABLE_NAME = "distributed_blocking_locks";

    @Test
    public void tableCreated() throws SQLException {
        assertTrue(tableExists(TABLE_NAME));
        assertTrue(columnExists(TABLE_NAME, "lock_key"));

        String primaryKeyName = "distributed_blocking_locks_pk";
        assertTrue(primaryKeyExists(TABLE_NAME, primaryKeyName));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v237;
    }

}