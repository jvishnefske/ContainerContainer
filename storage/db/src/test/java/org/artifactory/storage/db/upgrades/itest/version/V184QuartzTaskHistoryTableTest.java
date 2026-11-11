package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v184;
import static org.testng.Assert.assertTrue;

/**
 * @author Tomer Slutsky
 */
public class V184QuartzTaskHistoryTableTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        assertTrue(tableExists("quartz_task_history"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v184;
    }
}
