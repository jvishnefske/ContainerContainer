package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v238;
import static org.testng.Assert.assertTrue;

public class V238AddMetadataColumnToNodesCleanedTest extends UpgradeBaseTest {

    private static final String TABLE_NAME = "nodes_cleaned";
    private static final String COLUMN_NAME = "metadata";

    @Test
    public void metadataColumnAdded() throws SQLException {
        assertTrue(tableExists(TABLE_NAME), "Table nodes_cleaned should exist");
        assertTrue(columnExists(TABLE_NAME, COLUMN_NAME),
                "Column metadata should be added to nodes_cleaned table");
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v238;
    }

}

