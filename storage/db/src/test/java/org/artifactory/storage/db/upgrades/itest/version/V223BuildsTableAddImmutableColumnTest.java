package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v223;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;


public class V223BuildsTableAddImmutableColumnTest extends UpgradeBaseTest {

    @Test
    public void testBuildsTableAddImmutableColumn() throws SQLException {
        assertTrue(columnExists("builds", "immutable"));
        assertTrue(isColumnNullable("builds", "immutable"));
        assertEquals(columnSize("builds", "immutable"), 5);
    }


    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v223;
    }
}