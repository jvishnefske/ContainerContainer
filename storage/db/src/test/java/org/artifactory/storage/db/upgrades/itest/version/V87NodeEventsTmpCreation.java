package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V87NodeEventsTmpCreation extends UpgradeBaseTest {
    /**
     * On v182 we use Partitions on node_events_tmp table which cause tableExists(node_events_tmp) return false while
     * the table exists. v87 convertor we check columnNotExists to make sure the table is not exists before creating it
     */
    @Test
    public void testNodeEventsNotExists() throws SQLException, IOException {
        resetToVersion(ArtifactoryDBVersion.v86);
        assertFalse(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "node_events_tmp"));
        assertFalse(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "node_events_tmp", "tmp_event_id"));
    }

    @Test
    public void testCreateNodeEvents() throws SQLException {
        assertTrue(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "node_events_tmp", "tmp_event_id"));
        assertTrue(DbUtils.tableExists(jdbcHelper, dbProperties.getDbType(), "node_events_tmp"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v87;
    }
}
