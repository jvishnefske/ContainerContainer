package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v37;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v38;
import static org.testng.Assert.assertEquals;

/**
 * @author Inbar Tal
 */
@Test
public class V38UpgradeSetStoringRepoTest extends UpgradeBaseTest {
    public void testStoringRepoSetToDefaultOnlyIfTypeIsTarget() throws SQLException {
        importSql("/sql/bundles_non_default_storing_repo.sql");
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v38)) {
            v38.converter().convert(context);
        }
        try (ResultSet res = jdbcHelper.executeSelect("SELECT * FROM artifact_bundles")) {
            while (res.next()) {
                String type = res.getString("type");
                String storingRepo = res.getString("storing_repo");
                if ("SOURCE".equals(type)) {
                    assertEquals(storingRepo, "my-repo");
                } else {
                    assertEquals(type, "TARGET");
                    assertEquals(storingRepo, "release-bundles");
                }
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v37;
    }
}

