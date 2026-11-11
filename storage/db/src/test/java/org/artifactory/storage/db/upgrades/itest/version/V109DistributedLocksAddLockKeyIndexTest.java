package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.dao.locks.DbDistributeLocksDao.TABLE_DISTRIBUTED_LOCKS;
import static org.testng.Assert.assertTrue;

@Test
public class V109DistributedLocksAddLockKeyIndexTest extends UpgradeBaseTest {

    public void testLockKeyIndexAdded() throws SQLException {
        // added index
        assertTrue(DbUtils.indexExists(jdbcHelper, TABLE_DISTRIBUTED_LOCKS, "lock_key",
                "lock_key_idx", dbProperties.getDbType()), "Expected lock_key_idx to exists but was not found");
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v109;
    }
}
