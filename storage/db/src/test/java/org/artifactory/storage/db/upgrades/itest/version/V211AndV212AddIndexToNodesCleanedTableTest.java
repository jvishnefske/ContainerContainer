package org.artifactory.storage.db.upgrades.itest.version;

import lombok.NonNull;
import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v191;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v192;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v209;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v210;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v211;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v212;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v228;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v229;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class V211AndV212AddIndexToNodesCleanedTableTest extends AsyncUpgradeBaseTest {

    private final static String TABLE_NAME = "nodes_cleaned";
    private final static String INDEX_NAME = "nodes_cleaned_policy_key_policy_run_id_idx";

    @Test
    public void testIndexCreationOnDifferentDatabases() throws SQLException {
        if (POSTGRESQL.equals(dbProperties.getDbType()) || MSSQL.equals(dbProperties.getDbType())) {
            String message = "This test is only for database other than Postgresql and Mssql, " +
                    "found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        runUpgrade(v191);
        runUpgrade(v192);
        runUpgrade(v210);
        runUpgrade(v211);
        validateIndexExists();
    }

    @Test
    public void testIndexCreationOnPostgres() throws SQLException {
        testOnlyWithPostgresql();
        runUpgrade(v191);
        runUpgrade(v192);
        runUpgrade(v210);
        runUpgrade(v212);
        validateIndexExists();
    }

    @Test
    public void testIndexCreationOnMssqlNonEnt() throws SQLException {
        testOnlyWithMssql();
        runUpgrade(v191);
        runUpgrade(v192);
        runUpgrade(v210);
        runUpgradeWithSkipped(v229);
    }

    @Test
    public void testIndexCreationOnMssqlEnt() throws SQLException {
        testOnlyWithMssql();
        runUpgrade(v191);
        runUpgrade(v192);
        runUpgrade(v210);
        runUpgrade(v228);
        validateIndexExists();
    }


    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v209;
    }


    private void validateIndexExists() throws SQLException {
        assertTrue(indexExist(TABLE_NAME, "policy_key", INDEX_NAME));
        assertTrue(indexExist(TABLE_NAME, "policy_run_id", INDEX_NAME));
    }

    private void runUpgrade(@NonNull ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            version.converter().convert(context);
        }
    }

    private void runUpgradeWithSkipped(@NonNull ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbProperties.getDbType(), version)) {
            assertEquals(version.converter().convert(context).getStatus(), ConverterApplyStatus.SKIPPED);
        }
    }

}
