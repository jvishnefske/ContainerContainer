package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbConverterResult;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v231;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v232;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class V232AddRepositoryStatsTableTest extends UpgradeBaseTest {

    @Test
    public void repositoryStatsTableAdded() throws SQLException {
        String tableName = "repository_stats";
        assertFalse(tableExists(tableName));
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), v232)) {
            DbConverterResult res = v232.converter().convert(context);
            assertEquals(res.getStatus(), ConverterApplyStatus.COMPLETED);
        }
        assertTrue(tableExists(tableName));
        assertTrue(columnExists(tableName, "last_statistics_updated"));
        assertTrue(columnExists(tableName, "last_downloaded"));
        assertTrue(columnExists(tableName, "last_uploaded"));
        assertTrue(indexExist(tableName,
                "last_statistics_updated",
                "idx_repository_stats_last_statistics_updated"));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v231;
    }
}
