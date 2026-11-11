package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v220;
import static org.testng.Assert.assertTrue;

public class V220BuildReleaseBundlesTableCreatedTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        assertTrue(tableExists("build_release_bundles"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v220;
    }
}
