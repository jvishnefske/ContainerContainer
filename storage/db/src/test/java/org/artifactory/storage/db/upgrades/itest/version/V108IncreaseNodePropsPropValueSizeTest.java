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
public class V108IncreaseNodePropsPropValueSizeTest extends UpgradeBaseTest {

    @Test
    public void testWatchesTableUsernameColumnSize() throws SQLException {
        int columnSize = withConnection(jdbcHelper, conn -> columnSize("node_props", "prop_value"));
        assertEquals(columnSize, 4000);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v108;
    }
}
