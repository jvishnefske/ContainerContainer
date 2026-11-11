package org.artifactory.storage.db.upgrades.itest.version;

import lombok.NonNull;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v233;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v234;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v271;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v83;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v84;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v85;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V271DropOldBuildsIndexTest extends AsyncUpgradeBaseTest {

    private static final String TABLE_NAME = "builds";
    private static final String OLD_INDEX_NAME = "repo_build_name_date_idx";

    @Test
    public void testOldIndexDropped() throws SQLException {
        testOnlyWithPostgresql();
        runUpgrade(v83);
        runUpgrade(v84);
        runUpgrade(v85);
        runUpgrade(v234);
        assertTrue(indexExist(TABLE_NAME, "repo", OLD_INDEX_NAME),
                "Old index should exist before v271");
        assertTrue(indexExist(TABLE_NAME, "build_name", OLD_INDEX_NAME),
                "Old index should exist before v271");
        assertTrue(indexExist(TABLE_NAME, "build_date", OLD_INDEX_NAME),
                "Old index should exist before v271");

        runUpgrade(v271);

        assertFalse(indexExist(TABLE_NAME, "repo", OLD_INDEX_NAME),
                "Old index should not exist after v271");
        assertFalse(indexExist(TABLE_NAME, "build_name", OLD_INDEX_NAME),
                "Old index should not exist after v271");
        assertFalse(indexExist(TABLE_NAME, "build_date", OLD_INDEX_NAME),
                "Old index should not exist after v271");
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v233;
    }

    private void runUpgrade(@NonNull ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }
}
