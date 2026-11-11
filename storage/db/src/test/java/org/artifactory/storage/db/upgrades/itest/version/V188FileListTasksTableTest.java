package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v188;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
public class V188FileListTasksTableTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        assertTrue(tableExists("file_list_tasks"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v188;
    }
}
