package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.fs.dao.QuartzTaskHistoryTable;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author vovam
 */
public class V193ReCreateQuartzTaskHistoryTableTest extends UpgradeBaseTest {

    @Test
    public void testReCreateQuartzTaskHistoryTable() throws SQLException {
        assertTrue(tableExists(QuartzTaskHistoryTable.TABLE_NAME));
        for (QuartzTaskHistoryTable.Columns column : QuartzTaskHistoryTable.Columns.values()) {
            assertTrue(columnExists(QuartzTaskHistoryTable.TABLE_NAME, column.getName()));
        }
    }
    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v193;
    }
}