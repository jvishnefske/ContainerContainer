/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.upgrades.common;

import ch.qos.logback.classic.util.ContextInitializer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.LockingDBConverterAdapter;
import org.artifactory.storage.db.home.ArtifactoryUniqueFolderHomeBoundTest;
import org.artifactory.storage.db.init.ArtifactoryDataSource;
import org.artifactory.storage.db.init.CommonDataSourceFactory;
import org.artifactory.storage.db.itest.DbTestUtils;
import org.artifactory.storage.db.itest.UniqueJvmForkTestIdentifier;
import org.artifactory.storage.db.upgrades.external.itest.ExternalConversionScriptTest;
import org.artifactory.storage.db.util.ArtifactoryDbVersionUtil;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.artifactory.version.ArtifactoryServiceVersionProvider;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.common.ResourceUtils;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.converter.DbDetails;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.converter.util.DbConverterUtil;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.metric.MetricsDataSource;
import org.jfrog.storage.util.DbStatementUtils;
import org.jfrog.storage.util.DbUtils;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.storage.db.itest.DbTestUtils.RunnableWithException;
import static org.artifactory.storage.db.itest.DbTestUtils.dropExistingObjects;
import static org.artifactory.storage.db.itest.DbTestUtils.initAndCreateUniqueDbNamespaceIfNeeded;
import static org.artifactory.storage.db.itest.DbTestUtils.withRetry;
import static org.jfrog.common.FileUtils.writeFile;
import static org.jfrog.converter.util.DbConverterUtil.calculateDbVersionsForExecution;
import static org.jfrog.storage.DbType.MSSQL;
import static org.jfrog.storage.DbType.ORACLE;
import static org.jfrog.storage.util.DbUtils.doStreamWithConnection;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Date: 8/5/13 9:58 AM
 *
 * @author freds
 */
@Test(groups = "dbtest-upgrade")
@ContextConfiguration(locations = {"classpath:spring/db-upgrade-test-context.xml"})
public abstract class UpgradeBaseTest extends AbstractTestNGSpringContextTests {
    protected static final String RESOURCE_FOR_SUBSTITUTION_NAME = "_v18_ui_session.sql";

    static {
        // use the itest logback config
        URL url = UpgradeBaseTest.class.getClassLoader().getResource("logback-dbtest.xml");
        System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, url.getPath());
    }

    @Autowired
    protected JdbcHelper jdbcHelper;

    @Autowired
    @Qualifier("dbProperties")
    protected ArtifactoryDbProperties dbProperties;

    private ArtifactoryUniqueFolderHomeBoundTest artifactoryHomeBoundTest;

    protected static InputStream getPreviousDbSchemaSql(String previousVersion, DbType dbType) {
        String dbConfigDir = DbUtils.getDbTypeNameForSqlResources(dbType);
        return ResourceUtils.getResource(
                "/upgrades/" + previousVersion + "/ddl/" + dbConfigDir + "/" + dbConfigDir + ".sql");
    }

    private static InputStream getPreviousImportSql(String previousVersion, String dataFileName) {
        return ResourceUtils.getResource(
                "/upgrades/" + previousVersion + "/data/" + dataFileName + ".sql");
    }

    @BeforeClass
    @Override
    protected void springTestContextPrepareTestInstance() throws Exception {
        artifactoryHomeBoundTest = createArtifactoryHomeTest();
        artifactoryHomeBoundTest.bindArtifactoryHome();
        initAndCreateUniqueDbNamespaceIfNeeded();
        super.springTestContextPrepareTestInstance();
        contextCreated();
        rollBackToInitialState();
        assertTrue(runConvertersUpTo(upToVersion()));
        assertFalse(runConvertersUpTo(upToVersion()));
        convertLockingDBIfPresent();
    }

    private boolean runConvertersUpTo(ArtifactoryDBVersion upToVersion) {
        List<DbVersion> dbVersionsToExecute;
        final List<DbVersion> dbVersions =
                ArtifactoryDbVersionUtil.versionsBetween(ArtifactoryDBVersion.v10, upToVersion);
        DbDetails dbDetails = new DbDetails(dbProperties.getDbType(), jdbcHelper);
        String serverId = ArtifactoryHome.get().getServerId();
        try {
            dbVersionsToExecute = calculateDbVersionsForExecution(dbDetails, serverId, new DbVersion[] {},
                    dbVersions.toArray(DbVersion[]::new));
            DbConverterUtil.executeConverters(dbDetails, dbVersionsToExecute, upToVersion.systemVersion(), serverId);
        } catch (DbConverterExecutionException | SQLException e) {
            throw new DbConverterRuntimeException(e.getMessage(), e);
        }
        return !dbVersionsToExecute.isEmpty();
    }

    private void runConvertersUpTo(ArtifactoryServiceVersionProvider skipUntilVersion,
            ArtifactoryDBVersion upToVersion) {
        final List<DbVersion> dbVersions =
                ArtifactoryDbVersionUtil.versionsBetween(ArtifactoryDBVersion.v10, upToVersion);
        try {
            final CompoundVersionDetails compoundVersionDetails = Mockito.mock(CompoundVersionDetails.class);
            Mockito.when(compoundVersionDetails.getVersion()).thenReturn(skipUntilVersion.get());
            DbConverterUtil.calculateAndExecute(new DbDetails(dbProperties.getDbType(), jdbcHelper),
                    ArtifactoryDBVersion.getAlreadyApplied(compoundVersionDetails, ConverterExecutionType.SEQ_SYNC),
                    dbVersions.toArray(DbVersion[]::new), upToVersion.systemVersion(),
                    ArtifactoryHome.get().getServerId());
        } catch (DbConverterExecutionException | SQLException e) {
            throw new DbConverterRuntimeException(e.getMessage(), e);
        }
    }

    protected void contextCreated() throws Exception {
        // NO OP
    }

    /**
     * if LockingDB is present: Set up LockingDBConverterAdapter and convert
     *
     * @throws Exception
     */
    private void convertLockingDBIfPresent() throws Exception {
        if (dbProperties.getDbType().equals(DbType.POSTGRESQL) &&
                dbProperties.getLockingDbSpecificType().isPresent() &&
                dbProperties.getLockingDbSpecificType().get().equals(DbType.POSTGRESQL)) {
            JFrogDataSource lockingDBDataSource =
                    CommonDataSourceFactory.createDifferentDatasourceForDbLock(dbProperties, true);
            JdbcHelper lockingDBJdbcHelper = new JdbcHelper(lockingDBDataSource);
            DbUtils.doWithConnection(lockingDBJdbcHelper,
                    conn -> dropExistingObjects(conn, dbProperties.getLockingDbSpecificType().get()));
            executeSqlStream(getPreviousDbSchemaSql("v550c", dbProperties.getDbType()), lockingDBJdbcHelper);
            LockingDBConverterAdapter lockingDBConverterAdapter =
                    new LockingDBConverterAdapter(ArtifactoryHome.get(),
                            new DbDetails(dbProperties.getLockingDbSpecificType().get(),
                                    lockingDBJdbcHelper));
            lockingDBConverterAdapter.convert(null, null);
        }
    }

    /**
     * Sets up an external conversion script that overrides one (arbitrarily picked) built-in conversion file during
     * conversion, with an externally provided conversion script. This is a bit hacky but required because
     * {@link this#springTestContextPrepareTestInstance} runs before any test can intervene and is invoking the entire
     * conversion chain. This is called only once, and explicitly at
     * {@link ExternalConversionScriptTest}
     *
     * @param emptyScript - indicates whether the script should be empty of content
     */
    protected void prepareTestInstanceForExternalConversion(boolean isExternalConversionScriptsAllowed,
            boolean createScript, boolean emptyScript) throws Exception {
        // We are already ArtifactoryHome bound. Rollback, prepare the external script, and convert again
        try {
            rollBackToInitialState();

            ArtifactoryHome.get().getArtifactoryProperties()
                    .setProperty(ConstantValues.allowExternalConversionScripts.getPropertyName(),
                            Boolean.toString(isExternalConversionScriptsAllowed));

            if (createScript) {
                setupExternalConversionScript(emptyScript);
            }
            assertTrue(runConvertersUpTo(upToVersion()));
            assertFalse(runConvertersUpTo(upToVersion()));
        } finally {
            FileUtils.deleteQuietly(new File(getExternalConversionScriptPath()));
        }
    }

    private String getExternalConversionScriptPath() {
        return ArtifactoryHome.get().getEtcDir() + "/conversion" + "/" +
                DbUtils.getDbTypeNameForSqlResources(dbProperties.getDbType()) + RESOURCE_FOR_SUBSTITUTION_NAME;
    }

    protected void prepareTestInstanceForSkippableConversion(String toSkip) {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ConstantValues.skipSkippableDbConverters.getPropertyName(), toSkip);
    }

    private void setupExternalConversionScript(boolean emptyScript) {
        String externalConversionScript = "";
        File conversionsDir = new File(ArtifactoryHome.get().getEtcDir() + "/conversion");
        String sqlResource = "external_conversion_script.sql";
        String dbType = DbUtils.getDbTypeNameForSqlResources(dbProperties.getDbType());
        if (dbType.equals("oracle") || dbType.equals("mssql")) {
            sqlResource = "oracle_" + sqlResource;
        }
        conversionsDir.mkdir();

        if (!emptyScript) {
            String scriptResourcePath = "/sql/" + sqlResource;
            externalConversionScript = ResourceUtils.getResourceAsString(scriptResourcePath);
            writeFile(getExternalConversionScriptPath(), externalConversionScript.getBytes());
        } else {
            writeFile(getExternalConversionScriptPath(), "".getBytes());
        }
    }

    private ArtifactoryUniqueFolderHomeBoundTest createArtifactoryHomeTest() {
        return new ArtifactoryUniqueFolderHomeBoundTest(UniqueJvmForkTestIdentifier.get());
    }

    @AfterClass
    protected void verifyDbResourcesReleased() throws SQLException {
        ArtifactoryDataSource ds;
        JFrogDataSource jFrogDataSource = jdbcHelper.getDataSource();
        // make sure there are no active connections
        if (jFrogDataSource.isWrapperFor(MetricsDataSource.class)) {
            ds = jFrogDataSource.unwrap(MetricsDataSource.class).unwrap(ArtifactoryDataSource.class);
        } else {
            ds = jFrogDataSource.unwrap(ArtifactoryDataSource.class);
        }
        assertEquals(ds.getActiveConnectionsCount(), 0, "Found " + ds.getActiveConnectionsCount() +
                " active connections after test ended");
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @BeforeMethod
    public void bindArtifactoryHome() {
        artifactoryHomeBoundTest.bindArtifactoryHome();
    }

    @AfterMethod
    public void unbindArtifactoryHome() {
        artifactoryHomeBoundTest.unbindArtifactoryHome();
    }

    @AfterClass
    public void cleanEnv() {
        artifactoryHomeBoundTest.cleanEnv();
    }

    protected void rollBackToInitialState() throws IOException, SQLException {
        // Making DB looks like v10 with data
        dropExistingObjectsWithRetryIfNeeded();
        executeSqlStream(getPreviousDbSchemaSql("v10", dbProperties.getDbType()));
        executeSqlStream(getPreviousImportSql("v10", "builds"));
        executeSqlStream(getPreviousImportSql("v10", "nodes"));
    }

    protected void dropExistingObjectsWithRetryIfNeeded() throws SQLException, IOException {
        RunnableWithException runnable =
                () -> DbUtils.doWithConnection(jdbcHelper, conn -> dropExistingObjects(conn, dbProperties.getDbType()));
        if (dbProperties.getDbType() == ORACLE) {
            withRetry(2, 500, runnable);
        } else {
            runnable.run();
        }
    }

    protected void resetToVersion(ArtifactoryDBVersion resetTo) throws IOException, SQLException {
        rollBackToInitialState();
        assertTrue(runConvertersUpTo(resetTo));
        assertFalse(runConvertersUpTo(resetTo));
    }

    protected void resetToVersion(ArtifactoryServiceVersionProvider skipUntilVersion, ArtifactoryDBVersion resetTo)
            throws IOException, SQLException {
        rollBackToInitialState();
        runConvertersUpTo(skipUntilVersion, resetTo);
    }

    protected void importSql(String resourcePath) {
        try (InputStream resource = ResourceUtils.getResource(resourcePath)) {
            executeSqlStream(resource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean columnExists(String table, String column) throws SQLException {
        return withConnection(jdbcHelper,
                conn -> DbTestUtils.columnExists(conn, dbProperties.getDbType(), table, column));
    }

    protected Boolean primaryKeyExists(String tableName, String primaryKeyName) throws java.sql.SQLException {
        return withConnection(jdbcHelper,
                conn -> DbUtils.primaryKeyExists(conn, dbProperties.getDbType(), tableName, primaryKeyName));
    }

    protected boolean tableEmpty(String table) throws SQLException {
        return withConnection(jdbcHelper, conn -> DbTestUtils.tableEmpty(conn, table));
    }

    protected boolean tableExists(String table) throws SQLException {
        return withConnection(jdbcHelper, conn -> DbTestUtils.tableExists(conn, dbProperties.getDbType(), table));
    }

    protected boolean viewExists(String view) throws SQLException {
        return withConnection(jdbcHelper, conn -> DbTestUtils.viewExists(conn, dbProperties.getDbType(), view));
    }

    protected Boolean indexExist(String tableName, String columnName, String indexName) throws java.sql.SQLException {
        return DbUtils.withConnection(jdbcHelper,
                conn -> DbUtils
                        .indexExists(conn, dbProperties.getDbType(), tableName, columnName,
                                indexName, true));
    }

    /**
     * Checks index existence via {@code pg_indexes} for the active schema. Prefer this over
     * {@link #indexExist} for partitioned PostgreSQL tables: parent indexes are not always visible
     * to JDBC metadata-based checks the same way.
     */
    protected boolean indexExistsByName(String indexName) throws SQLException {
        testOnlyWithPostgresql();
        return withConnection(jdbcHelper, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?")) {
                ps.setString(1, indexName);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    protected Boolean sequenceExists(String sequenceName) throws java.sql.SQLException {
        return DbUtils.withConnection(jdbcHelper,
                conn -> DbUtils
                        .sequenceExists(conn, dbProperties.getDbType(), sequenceName));
    }

    protected int columnSize(String table, String columnName) throws SQLException {
        return withConnection(jdbcHelper,
                conn -> DbTestUtils.getColumnSize(conn, dbProperties.getDbType(), table, columnName));
    }

    protected boolean isColumnNullable(String table, String columnName) throws SQLException {
        return withConnection(jdbcHelper,
                conn -> DbUtils.isColumnNullable(conn.getMetaData(), dbProperties.getDbType(), table, columnName));
    }

    protected boolean isDefaultExist(String table, String columnName) throws SQLException {
        return withConnection(jdbcHelper,
                conn -> DbUtils.isColumnHasDefaultValue(conn.getMetaData(), dbProperties.getDbType(),
                        table, columnName));
    }

    protected void executeSqlStream(InputStream in) throws IOException, SQLException {
        executeSqlStream(in, jdbcHelper);
    }

    protected void executeSqlStream(InputStream in, JdbcHelper jdbcHelper) throws IOException, SQLException {
        try {
            doStreamWithConnection(jdbcHelper, conn -> DbStatementUtils.executeSqlStream(conn, in));
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    protected void testOnlyWithPostgresql() {
        if (!dbProperties.isPostgres()) {
            String message = "This test is for Postgresql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
    }

    protected void testOnlyWithOracle() {
        if (!dbProperties.getDbType().equals(ORACLE)) {
            String message = "This test is for Oracle only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
    }

    protected void testOnlyWithMssql() {
        if (!dbProperties.getDbType().equals(MSSQL)) {
            String message = "This test is for Mssql only, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
    }

    /**
     * Override this method in child classed to set version up to which converters should be executed.
     */

    protected ArtifactoryDBVersion upToVersion() {
        throw new UnsupportedOperationException("Set version up to which converters should be applied.");
    }
}
