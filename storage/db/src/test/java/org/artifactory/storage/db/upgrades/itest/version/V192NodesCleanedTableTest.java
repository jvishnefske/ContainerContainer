package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.policy.cleanup.dao.NodeCleanedDao;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author vovam
 */
public class V192NodesCleanedTableTest extends UpgradeBaseTest {

    @Test
    public void testNodesCleanedTableCreated() throws SQLException {
        String tableName = NodeCleanedDao.NODES_CLEANED_TABLE;
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, "policy_key"));
        assertTrue(columnExists(tableName, "policy_run_id"));
        assertTrue(columnExists(tableName, "package_name"));
        assertTrue(columnExists(tableName, "package_version"));
        assertTrue(columnExists(tableName, "lead_artifact"));
        assertTrue(columnExists(tableName, "repo"));
        assertTrue(columnExists(tableName, "node_path"));
        assertTrue(columnExists(tableName, "node_name"));
        assertTrue(columnExists(tableName, "bin_length"));
        assertTrue(columnExists(tableName, "sha1"));

        assertTrue(columnExists(tableName, "status"));
        assertTrue(columnExists(tableName, "message"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v192;
    }
}