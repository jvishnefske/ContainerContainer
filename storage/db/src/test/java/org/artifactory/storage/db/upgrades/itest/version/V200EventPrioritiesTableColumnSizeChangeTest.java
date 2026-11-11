package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.event.dao.NodeEventPrioritiesDao.EVENT_PRIORITIES_TABLE;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v200;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;


public class V200EventPrioritiesTableColumnSizeChangeTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        int columnSize = withConnection(jdbcHelper, conn -> columnSize(EVENT_PRIORITIES_TABLE, "path"));
        assertEquals(columnSize, 1344);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v200;
    }
}
