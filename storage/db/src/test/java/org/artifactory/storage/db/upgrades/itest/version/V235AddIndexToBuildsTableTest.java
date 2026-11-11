package org.artifactory.storage.db.upgrades.itest.version;

import lombok.NonNull;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v234;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v235;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v83;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v84;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v85;
import static org.jfrog.storage.DbType.DERBY;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertTrue;

public class V235AddIndexToBuildsTableTest extends AsyncUpgradeBaseTest {

    @Test
    public void testIndexExists() throws SQLException {
        if (POSTGRESQL.equals(dbProperties.getDbType()) || DERBY.equals(dbProperties.getDbType())) {
            String message = "This test is only for database other than Postgresql and Derby. " +
                    "found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        String indexName = "repo_build_name_date_idx";
        runUpgrade(v83);
        runUpgrade(v84);
        runUpgrade(v85);
        runUpgrade(v235);
        assertTrue(indexExist("builds", "repo", indexName));
        assertTrue(indexExist("builds", "build_name", indexName));
        assertTrue(indexExist("builds", "build_date", indexName));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v234;
    }

    private void runUpgrade(@NonNull ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }
}
