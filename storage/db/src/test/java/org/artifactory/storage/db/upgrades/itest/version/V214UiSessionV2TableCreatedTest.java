package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v214;
import static org.testng.Assert.assertTrue;

public class V214UiSessionV2TableCreatedTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        assertTrue(tableExists("UI_SESSION_V2"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v214;
    }
}
