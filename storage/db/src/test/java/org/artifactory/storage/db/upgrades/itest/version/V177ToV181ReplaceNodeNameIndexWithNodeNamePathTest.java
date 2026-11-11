package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.V177ToV181Commons;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v177;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v178;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v179;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v180;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V177ToV181ReplaceNodeNameIndexWithNodeNamePathTest extends V177ToV181Commons {
    @Test
    public void testUpgradeFlow() throws SQLException {
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), NEW_INDEX));
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(1), NEW_INDEX));
        assertTrue(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), OLD_INDEX));
        runUpgrade(v177, getExpectedStatus(v177));
        runUpgrade(v178, getExpectedStatus(v178));
        runUpgrade(v179, getExpectedStatus(v179));
        runUpgrade(v180, getExpectedStatus(v180));
        assertTrue(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), NEW_INDEX));
        assertTrue(indexExist(TABLE_NAME, COLUMN_NAMES.get(1), NEW_INDEX));
        assertFalse(indexExist(TABLE_NAME, COLUMN_NAMES.get(0), OLD_INDEX));
    }
}
