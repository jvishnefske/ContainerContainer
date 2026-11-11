package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.event.dao.EventsDao.EVENTS_TEMP_TABLE;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v164;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
public class V164AddConstraintTmpNodeEventsPrimaryKeyTest extends UpgradeBaseTest {
    @Test
    public void testPrimaryKeyExists() throws SQLException {
        assertTrue(primaryKeyExists(EVENTS_TEMP_TABLE, "node_events_tmp_pk"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v164;
    }
}
