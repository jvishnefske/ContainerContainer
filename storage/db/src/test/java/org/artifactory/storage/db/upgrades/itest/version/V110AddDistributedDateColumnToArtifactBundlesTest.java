package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v109;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v110;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class V110AddDistributedDateColumnToArtifactBundlesTest extends UpgradeBaseTest {

    public void testAddDistributedDateColumnToArtifactBundles() throws SQLException, IOException {

        assertFalse(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "artifact_bundles", "date_distributed"));

        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v110)) {
            v110.converter().convert(context);
            assertTrue(
                    DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "artifact_bundles", "date_distributed"));
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v109;
    }

}
