package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao.ARTIFACT_BUNDLES_TABLE;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v21;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v22;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;


@Test
public class V22AddTypeColumnWithDefaultValueToArtifactBundlesTest extends UpgradeBaseTest {

    public void testTypeColumnDefaultValueOnArtifactBundlesTable() throws SQLException {
        assertFalse(columnExists(ARTIFACT_BUNDLES_TABLE, "type"));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v22)) {
            v22.converter().convert(context);
        }
        importSql("/sql/bundles_with_null_type.sql");
        try (ResultSet res = jdbcHelper.executeSelect("SELECT * FROM artifact_bundles")) {
            while (res.next()) {
                String type = res.getString("type");
                assertEquals(type, "TARGET");
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v21;
    }

}
