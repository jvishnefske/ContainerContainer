package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;

/**
 * @author AndreiK.
 */
@Test
public class V51IncreaseBundleVersionSizeTest extends UpgradeBaseTest {

    @Test
    public void testArtifactBundlesVersionSizeChange() throws SQLException {
        int fetchedColumnSize = withConnection(jdbcHelper, conn -> columnSize("artifact_bundles", "version"));
        assertEquals(fetchedColumnSize, 32);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v51;
    }
}
