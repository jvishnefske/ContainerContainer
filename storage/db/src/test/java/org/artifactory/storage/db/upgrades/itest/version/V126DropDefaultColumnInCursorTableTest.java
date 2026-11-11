package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.event.dao.NodeEventsCursorDao.EVENTS_CURSOR_TABLE;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v127;
import static org.testng.Assert.assertFalse;

public class V126DropDefaultColumnInCursorTableTest extends UpgradeBaseTest {

    @Test
    public void testNoDefault() throws SQLException {
        assertFalse(isDefaultExist(EVENTS_CURSOR_TABLE, "last_updated"));
        assertFalse(isDefaultExist(EVENTS_CURSOR_TABLE, "status"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v127;
    }
}


