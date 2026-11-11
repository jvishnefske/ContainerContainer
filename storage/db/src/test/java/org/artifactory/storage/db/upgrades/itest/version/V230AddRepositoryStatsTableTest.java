package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v229;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v230;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V230AddRepositoryStatsTableTest extends UpgradeBaseTest {

    @Test
    public void repositoryStatsTableAdded() throws SQLException {
        String tableName = "repository_stats";
        assertFalse(tableExists(tableName));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v230)) {
            DbConverterResult res = v230.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertTrue(tableExists(tableName));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v229;
    }
}
