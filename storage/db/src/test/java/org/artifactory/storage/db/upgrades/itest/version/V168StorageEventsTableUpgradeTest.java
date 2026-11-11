package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author TamirH
 */
public class V168StorageEventsTableUpgradeTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        String tableName = "storage_events";
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, "event_id"));
        assertTrue(columnExists(tableName, "event_type"));
        assertTrue(columnExists(tableName, "sha1"));
        assertTrue(columnExists(tableName, "sha256"));
        assertTrue(columnExists(tableName, "bin_length"));
        assertTrue(indexExist(tableName, "sha1", "storage_events_sha1_type_idx"));
        assertTrue(indexExist(tableName, "event_type", "storage_events_sha1_type_idx"));
        assertTrue(indexExist(tableName, "bin_length", "storage_events_bin_size_idx"));
    }

    @Test
    public void testSequenceExists() throws SQLException {
        switch (dbProperties.getDbType()) {
            case ORACLE, POSTGRESQL -> assertTrue(sequenceExists("storage_events_event_id_seq"));
            default -> {
            }
            // skip test since not using sequence
        }

    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v170;
    }
}
