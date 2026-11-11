package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.init.migration.dao.MigrationStatusDao;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.util.ArtifactoryDbVersionUtil.versionsBetween;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v34;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Uriah Levy
 */
@Test
public class V37RemoveMetadataMigrationEntryTest extends UpgradeBaseTest {
    @BeforeMethod
    public void setup() throws IOException, SQLException {
        jdbcHelper.executeUpdate("INSERT INTO migration_status VALUES\n" +
                "(?, ?, ?, ?)", "metadata-service-migration", 0, 0, new byte[] {});
        assertTrue(hasEntries());
        final List<DbVersion> dbVersions = versionsBetween(v34, ArtifactoryDBVersion.v37);
        for (DbVersion dbVersion : dbVersions) {
            try (final ChangeLogContext context = new ChangeLogContext(
                    jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);

                if (!context.getConnection().getAutoCommit()) {
                    context.getConnection().commit();
                }
            }
        }

        for (DbVersion dbVersion : dbVersions) {
            try (final ChangeLogContext context = new ChangeLogContext(
                    jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }
    }

    public void testMetadataMigrationEntryRemoved() throws SQLException {
        assertFalse(hasEntries());
    }

    private boolean hasEntries() throws SQLException {
        try (ResultSet resultSet = jdbcHelper
                .executeSelect("SELECT * from " + MigrationStatusDao.MIGRATION_STATUS_TABLE + " where 1 = 1")) {
            return resultSet.next();
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v34;
    }
}
