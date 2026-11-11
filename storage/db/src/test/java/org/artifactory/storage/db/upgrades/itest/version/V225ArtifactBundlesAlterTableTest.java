package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v225;
import static org.testng.Assert.assertEquals;

public class V225ArtifactBundlesAlterTableTest extends UpgradeBaseTest {
    @Test
    public void testDependencyScopesColumnDataTypeAltered() throws SQLException {
        assertEquals(columnSize("artifact_bundles", "version"), 255);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v225;
    }
}

