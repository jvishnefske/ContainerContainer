package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Mor Merhav
 */
@Test
public class V64RenameReplicationErrorsToNodeEventsErrorsTest extends UpgradeBaseTest {

    private static final String TABLE_NODE_EVENTS_ERRORS = "node_events_errors";

    @Test
    public void testReplicationErrorsChangeToNodeEventsErrors() throws SQLException, IOException {
        resetToVersion(ArtifactoryDBVersion.v64);
        assertTrue(isColumnNullable(TABLE_NODE_EVENTS_ERRORS, "operation_key"));
        assertFalse(isColumnNullable(TABLE_NODE_EVENTS_ERRORS, "event_category"));

        assertFalse(isColumnNullable(TABLE_NODE_EVENTS_ERRORS, "event_time"));
        assertFalse(isColumnNullable(TABLE_NODE_EVENTS_ERRORS, "event_type"));
        assertFalse(isColumnNullable(TABLE_NODE_EVENTS_ERRORS, "path"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v64;
    }
}
