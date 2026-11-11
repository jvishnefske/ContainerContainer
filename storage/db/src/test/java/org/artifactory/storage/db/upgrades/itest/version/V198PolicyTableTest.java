package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author vovam
 */
public class V198PolicyTableTest extends UpgradeBaseTest {

    @Test
    public void testNodesCleanedTableCreated() throws SQLException {
        String tableName = "policy";
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, "policy_key"));
        assertTrue(columnExists(tableName, "policy_type"));
        assertTrue(columnExists(tableName, "item_type"));
        assertTrue(columnExists(tableName, "enabled"));
        assertTrue(columnExists(tableName, "created"));
        assertTrue(columnExists(tableName, "created_by"));
        assertTrue(columnExists(tableName, "config"));
        assertTrue(columnExists(tableName, "modified"));
        assertTrue(columnExists(tableName, "modified_by"));
        assertTrue(columnExists(tableName, "cronExp"));
        assertTrue(columnExists(tableName, "description"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v198;
    }
}