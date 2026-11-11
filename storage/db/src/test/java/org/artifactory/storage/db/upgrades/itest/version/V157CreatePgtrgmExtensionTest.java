package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v156;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v157;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.jfrog.storage.util.DbUtils.extensionExists;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
public class V157CreatePgtrgmExtensionTest extends AsyncUpgradeBaseTest {

    @Test(enabled = false)
    public void testExtensionExists() throws SQLException {
        testOnlyWithPostgresql();
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                POSTGRESQL, v157)) {
            v157.converter().convert(context);
        }
        assertTrue(extensionExists(jdbcHelper, DbType.POSTGRESQL, "pg_trgm"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v156;
    }
}
