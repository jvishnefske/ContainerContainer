package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.config.db.TemporaryDBChannel;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v116;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v117;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.artifactory.storage.db.bundle.dao.ArtifactBundlesDao.ARTIFACT_BUNDLES_TABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Osaid Wattad
 */
@Test
public class v117AddTypeColumnDefaultValueOnArtifactBundlesTest extends UpgradeBaseTest {

    private JdbcTemplate jdbc;

    @BeforeMethod
    public void beforeMethod() throws SQLException, IOException {
        TemporaryDBChannel temporaryDBChannel = new TemporaryDBChannel(ArtifactoryHome.get().getDBProperties());
        jdbc = temporaryDBChannel.asJdbcTemplate();
    }

    public void testAlterTypeColumnDefaultValueOnArtifactBundles() throws SQLException {
        if ((dbProperties.getDbType() != DbType.ORACLE)) {
            dropColumnTypeDefaultValue();
            assertFalse(withConnection(jdbcHelper, conn -> DbUtils
                    .isColumnHasDefaultValue(conn.getMetaData(), dbProperties.getDbType(), ARTIFACT_BUNDLES_TABLE,
                            "type")));
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), v117)) {
                v117.converter().convert(context);
                assertTrue(withConnection(jdbcHelper, conn -> DbUtils
                        .isColumnHasDefaultValue(conn.getMetaData(), dbProperties.getDbType(), ARTIFACT_BUNDLES_TABLE,
                                "type")));
            }

            importSql("/sql/bundles_with_null_type.sql");
            SqlRowSet rowSet = jdbc.queryForRowSet("select type from " + ARTIFACT_BUNDLES_TABLE);
            String type = "";
            while (rowSet.next()) {
                type = rowSet.getString("type");
                assertEquals("TARGET", type);
            }
        }
        else {
            String message = "This test is not for oracle, found " + dbProperties.getDbType() + ". Skipping test.";
            logger.info(message);
            throw new SkipException(message);
        }
    }

    private void dropColumnTypeDefaultValue() {
        if (dbProperties.getDbType() == DbType.MSSQL) {
            dropDefaultOnMssqlDB();
        } else if ((dbProperties.getDbType() == DbType.POSTGRESQL)) {
            jdbc.execute("ALTER TABLE artifact_bundles ALTER COLUMN type DROP DEFAULT");
        } else if (dbProperties.getDbType() == DbType.MYSQL || dbProperties.getDbType() == DbType.MARIADB) {
            jdbc.execute("ALTER TABLE artifact_bundles ALTER type DROP DEFAULT");
        } else if (dbProperties.getDbType() == DbType.DERBY) {
            jdbc.execute("ALTER TABLE artifact_bundles ALTER type DROP DEFAULT");
        }
    }

    private void dropDefaultOnMssqlDB() {
        String constraintName = "";
        //fetch constraint on table artifact_bundles column type
        SqlRowSet rowSet = jdbc.queryForRowSet(
                "SELECT \n" +
                        "    Table_Name = t.Name,\n" +
                        "    Column_Name = c.Name,\n" +
                        "    Constraint_Name = dc.Name\n" +
                        "FROM sys.tables t\n" +
                        "INNER JOIN sys.default_constraints dc ON \n" +
                        "         t.object_id = dc.parent_object_id\n" +
                        "INNER JOIN sys.columns c ON \n" +
                        "         dc.parent_object_id = c.object_id AND \n" +
                        "         c.column_id = dc.parent_column_id AND\n" +
                        "         c.name = 'type'\n" +
                        "WHERE\n" +
                        "     t.NAME = 'artifact_bundles'");
        while (rowSet.next()) {
            constraintName = rowSet.getString("Constraint_Name");
        }
        jdbc.execute("ALTER TABLE artifact_bundles DROP constraint " + constraintName);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v116;
    }

}
