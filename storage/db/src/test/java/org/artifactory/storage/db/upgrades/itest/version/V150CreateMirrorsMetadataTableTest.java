package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;

import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class V150CreateMirrorsMetadataTableTest extends UpgradeBaseTest {

    @Test
    public void testMirrorMetadataTableCreated() throws SQLException {
        String tableName = "mirrors_metadata";
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, "metadata_blob"));
        assertTrue(columnExists(tableName, "cache_time"));
        assertTrue(columnExists(tableName, "member_url"));
        assertTrue(columnExists(tableName, "metadata_type"));
    }


    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v150;
    }
}
