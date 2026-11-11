package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.itest.DbTestUtils;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

@Test
public class V50UpgradeNodePropsTest extends UpgradeBaseTest {

    public void testNodePropsIndices() throws SQLException {
        //RTFACT-20776
        if (!dbProperties.getDbType().equals(DbType.MSSQL)) {
            return;
        }
        // added indices
        assertTrue(DbUtils.indexExists(jdbcHelper, "node_props", "node_id",
                "node_props_node_prop_value_idx", dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "node_props", "prop_value",
                "node_props_prop_value_key_idx", dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "node_props", "prop_key",
                "node_props_prop_key_value_idx", dbProperties.getDbType()));

        // removed indices
        assertTrue(DbTestUtils.indexNotExists(jdbcHelper, "node_props", "node_id",
                "node_props_node_id_idx", dbProperties.getDbType()));
        assertTrue(DbTestUtils.indexNotExists(jdbcHelper, "node_props", "prop_key",
                "node_props_prop_key_idx", dbProperties.getDbType()));
        assertTrue(DbTestUtils.indexNotExists(jdbcHelper, "node_props", "prop_value",
                "node_props_prop_value_idx", dbProperties.getDbType()));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v50;
    }
}
