package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v243;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v244;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v245;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v246;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v247;
import static org.testng.Assert.assertEquals;

/**
 * Test for v244-v247 converters that replace node_events_full_idx with optimized index
 * node_events_timestamp_event_id_type_idx (without path column).
 *
 * @author Paz Nahmias
 */
public class V244ToV247ReplaceNodeEventsIndexTest extends AsyncUpgradeBaseTest {

    private static final String TABLE_NAME = "node_events";
    private static final String OLD_INDEX_NAME = "node_events_full_idx";
    private static final String NEW_INDEX_NAME = "node_events_timestamp_event_id_type_idx";

    @Test
    public void testOldIndexDroppedAndNewIndexCreated() throws SQLException {
        assertIndexExists(false);
        runUpgrade(v244);
        runUpgrade(v245);
        runUpgrade(v246);
        runUpgrade(v247);
        assertIndexExists(true);
    }

    private void runUpgrade(ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }

    private void assertIndexExists(boolean shouldExist) throws SQLException {
        assertEquals(indexExist(TABLE_NAME, "timestamp", OLD_INDEX_NAME), !shouldExist);
        assertEquals(indexExist(TABLE_NAME, "event_id", OLD_INDEX_NAME), !shouldExist);
        assertEquals(indexExist(TABLE_NAME, "event_type", OLD_INDEX_NAME), !shouldExist);
        assertEquals(indexExist(TABLE_NAME, "path", OLD_INDEX_NAME), !shouldExist);

        assertEquals(indexExist(TABLE_NAME, "timestamp", NEW_INDEX_NAME), shouldExist);
        assertEquals(indexExist(TABLE_NAME, "event_id", NEW_INDEX_NAME), shouldExist);
        assertEquals(indexExist(TABLE_NAME, "event_type", NEW_INDEX_NAME), shouldExist);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v243;
    }
}
