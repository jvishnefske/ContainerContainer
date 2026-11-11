package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
public class V101IncreaseWatchesUsernameSizeTest extends UpgradeBaseTest {

    @Test
    public void testWatchesTableUsernameColumnSize() throws SQLException {
        int columnSize = withConnection(jdbcHelper, conn -> columnSize("watches", "username"));
        assertEquals(columnSize, 255);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v101;
    }
}
