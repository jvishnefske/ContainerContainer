package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.testng.Assert.assertTrue;

/**
 * @author Alexei Vainshtein
 */
public class V81AsyncUpgradeBundleFilesNodeIdIndexTest extends AsyncUpgradeBaseTest {

    private static final String BUNDLE_FILES_TABLE = "bundle_files";
    private static final String NODE_ID_COLUMN = "node_id";
    private static final String BUNDLE_FILES_NODE_ID_INDEX_NAME = "bundle_files_node_id_idx";

    @Test
    public void testAddBundleFilesIndex() throws SQLException {
        assertTrue(DbUtils.indexExists(jdbcHelper, BUNDLE_FILES_TABLE, NODE_ID_COLUMN, BUNDLE_FILES_NODE_ID_INDEX_NAME,
                dbProperties.getDbType()));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v81;
    }
}
