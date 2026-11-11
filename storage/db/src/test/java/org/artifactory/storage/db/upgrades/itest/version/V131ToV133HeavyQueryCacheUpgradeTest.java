package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.hqc.enums.HeavyQueryCacheFields.CACHE_KEY;
import static org.artifactory.storage.db.hqc.enums.HeavyQueryCacheFields.CACHE_TIME;
import static org.artifactory.storage.db.hqc.enums.HeavyQueryCacheFields.DATA;
import static org.artifactory.storage.db.hqc.enums.HeavyQueryCacheFields.EXPIRY_TIME;
import static org.artifactory.storage.db.hqc.enums.HeavyQueryCacheFields.ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Andrey Tashlitsky
 */
public class V131ToV133HeavyQueryCacheUpgradeTest extends UpgradeBaseTest {

    @Test
    public void testTableExists() throws SQLException {
        String tableName = "heavy_query_cache";
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, ID.toString()));
        assertTrue(columnExists(tableName, CACHE_KEY.toString()));
        assertTrue(columnExists(tableName, CACHE_TIME.toString()));
        assertTrue(columnExists(tableName, EXPIRY_TIME.toString()));
        assertTrue(columnExists(tableName, DATA.toString()));
        assertEquals(columnSize(tableName, CACHE_KEY.toString()), 255);
        assertTrue(indexExist(tableName, CACHE_KEY.toString(), "hqc_key_idx"));
        assertTrue(indexExist(tableName, EXPIRY_TIME.toString(), "hqc_expiry_time_idx"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v133;
    }
}
