package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

public class V100UpgradeAddBuildModuleTypeTest extends UpgradeBaseTest {

    @Test
    public void testTypeColumnExists() throws SQLException {
        assertTrue(columnExists("build_modules", "type"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v100;
    }

}
