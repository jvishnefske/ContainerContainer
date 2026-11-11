package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v240;
import static org.testng.Assert.assertTrue;

public class V240StorageCacheTableCreatedTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        assertTrue(tableExists("storage_cache"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v240;
    }
}
