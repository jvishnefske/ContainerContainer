package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.AsyncUpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.storage.DbType;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v205;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v206;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v207;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v226;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v227;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.POSTGRESQL;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Kunal Mazumdar
 */
public class V206And207NodeRepoCreatedNewIndexTest extends AsyncUpgradeBaseTest {

    @Test
    public void testIndexCreationOnDifferentDatabases() throws SQLException {
        if (POSTGRESQL.equals(dbProperties.getDbType()) || MSSQL.equals(dbProperties.getDbType())) {
            String message = "This test is only for database other than Postgresql and Mssql, " +
                    "found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
        validateConverterStatus(dbProperties.getDbType(), v206);
        validateIndexExists();
    }

    @Test
    public void testIndexCreationOnPostgres() throws SQLException {
        testOnlyWithPostgresql();
        validateConverterStatus(POSTGRESQL, v207);
        validateIndexExists();
    }

    @Test
    public void testIndexCreationOnMssqlEnt() throws SQLException {
        testOnlyWithMssql();
        validateConverterStatus(MSSQL, v226);
        validateIndexExists();
    }

    @Test
    public void testIndexCreationOnMssqlNonEnt() throws SQLException {
        testOnlyWithMssql();
        validateConverterStatusSkipped(MSSQL, v227);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v205;
    }

    private void validateConverterStatus(DbType dbType, ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbType, version)) {
            assertEquals(version.converter().convert(context).getStatus(), ConverterApplyStatus.COMPLETED);
        }
    }

    private void validateConverterStatusSkipped(DbType dbType, ArtifactoryDBVersion version) throws SQLException {
        try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                dbType, version)) {
            assertEquals(version.converter().convert(context).getStatus(), ConverterApplyStatus.SKIPPED);
        }
    }

    private void validateIndexExists() throws SQLException {
        assertTrue(indexExist("nodes", "repo", "nodes_repo_created_idx"));
        assertTrue(indexExist("nodes", "created", "nodes_repo_created_idx"));
    }
}