package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v202;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v203;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V203VersionHistoryAddColumnTest extends UpgradeBaseTest {

    @Test
    public void columnAdded() throws SQLException {
        assertFalse(columnExists("version_history", "active"));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v203)) {
            DbConverterResult res = v203.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertTrue(columnExists("version_history", "active"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v202;
    }
}
