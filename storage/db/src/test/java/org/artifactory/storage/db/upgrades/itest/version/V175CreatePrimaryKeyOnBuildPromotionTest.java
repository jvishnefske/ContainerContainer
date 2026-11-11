package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v174;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v175;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v176;
import static org.jfrog.storage.DbType.MARIADB;
import static org.jfrog.storage.DbType.ORACLE;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Hussein Alshqerat
 */
public class V175CreatePrimaryKeyOnBuildPromotionTest extends UpgradeBaseTest {

    @Test
    public void testPrimaryKeyExists() throws SQLException {
        checkMariadbVersion();
        assertFalse(primaryKeyExists("build_promotions", "build_promotions_builds_pk"));
        try (final ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), v175)) {
            v175.converter().convert(context);
        }
        try (final ChangeLogContext context = new ChangeLogContext(
                jdbcHelper.getDataSource().getConnection(), dbProperties.getDbType(), v176)) {
            v176.converter().convert(context);
        }
        assertTrue(primaryKeyExists("build_promotions", "build_promotions_builds_pk"));

        if (!ORACLE.equals(dbProperties.getDbType())) {
            assertFalse(indexExist("build_promotions", "build_id", "build_promotions_created_idx"));
            assertFalse(indexExist("build_promotions", "created", "build_promotions_created_idx"));
        }
    }

    private void checkMariadbVersion() throws SQLException {
        if (MARIADB.equals(dbProperties.getDbType())) {
            try (ResultSet version = jdbcHelper.executeSelect("SELECT @@version;")) {
                if (version.next()) {
                    if (version.getString(1).contains("10.4")) {
                        String message = "This test is not for mariadb 10.4, Skipping test.";
                        logger.info(message);
                        throw new SkipException(message);
                    }
                }
            }
        }
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v174;
    }
}