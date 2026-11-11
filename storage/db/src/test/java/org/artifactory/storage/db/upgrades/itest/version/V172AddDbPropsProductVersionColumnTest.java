package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.common.config.db.DbVersionUtil.DB_PROPERTIES_TABLE;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v171;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v172;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamar Rosen
 */
public class V172AddDbPropsProductVersionColumnTest extends UpgradeBaseTest {

    @Test
    public void testUpgrade() throws SQLException {
        assertFalse(columnExists(DB_PROPERTIES_TABLE, "artifactory_product_version"));

        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v172)) {
            v172.converter().convert(context);
        }

        assertTrue(columnExists(DB_PROPERTIES_TABLE, "artifactory_product_version"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v171;
    }
}
