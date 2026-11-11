package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.itest.DbTestUtils;
import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.util.ArtifactoryDbVersionUtil;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.storage.util.DbUtils;
import org.testng.annotations.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v82;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v83;
import static org.artifactory.storage.db.version.ArtifactoryDBVersion.v85;
import static org.jfrog.storage.util.DbUtils.withConnection;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * @author Barakh
 */
@Test
public class V85AddRepoColumnToBuildsTest extends UpgradeBaseTest {

    public void testAddRepoColumnToBuilds() throws SQLException {
        assertFalse(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "builds", "repo"));
        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "build_name", "builds_name_number_date_idx",
                dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "build_number", "builds_name_number_date_idx",
                dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "build_date", "builds_name_number_date_idx",
                dbProperties.getDbType()));

        addBuild(1234, "my_build", "1", 0);

        for (DbVersion dbVersion : ArtifactoryDbVersionUtil.versionsBetween(v83, v85)) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper.getDataSource().getConnection(),
                    dbProperties.getDbType(), dbVersion)) {
                dbVersion.converter().convert(context);
            }
        }

        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "build_name", "builds_name_num_date_repo_idx",
                dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "build_number", "builds_name_num_date_repo_idx",
                dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "build_date", "builds_name_num_date_repo_idx",
                dbProperties.getDbType()));
        assertTrue(DbUtils.indexExists(jdbcHelper, "builds", "repo", "builds_name_num_date_repo_idx",
                dbProperties.getDbType()));

        assertTrue(DbTestUtils.indexNotExists(jdbcHelper, "builds", "build_name", "builds_name_number_date_idx",
                dbProperties.getDbType()));
        assertTrue(DbTestUtils.indexNotExists(jdbcHelper, "builds", "build_number", "builds_name_number_date_idx",
                dbProperties.getDbType()));
        assertTrue(DbTestUtils.indexNotExists(jdbcHelper, "builds", "build_date", "builds_name_number_date_idx",
                dbProperties.getDbType()));

        assertTrue(DbUtils.columnExists(jdbcHelper, dbProperties.getDbType(), "builds", "repo"));
        assertFalse(withConnection(jdbcHelper,
                conn -> DbUtils.isColumnNullable(conn.getMetaData(), dbProperties.getDbType(), "builds", "repo")));

        ResultSet resultSet = jdbcHelper.executeSelect("SELECT * FROM builds WHERE build_id = 1234");
        assertTrue(resultSet.next());
        assertEquals(resultSet.getString("repo"), "artifactory-build-info");
        resultSet.close();

        addBuildWithRepoKey(1235, "my_build", "1", 1, "my_repo");

        resultSet = jdbcHelper.executeSelect("SELECT * FROM builds WHERE build_id = 1235");
        assertTrue(resultSet.next());
        assertEquals(resultSet.getString("repo"), "my_repo");
        resultSet.close();
    }

    private void addBuild(long id, String buildName, String buildNumber, long buildDate) throws SQLException {
        int addedBuilds = jdbcHelper
                .executeUpdate("INSERT INTO builds " +
                        "(build_id, build_name, build_number, build_date, created, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?)", id, buildName, buildNumber, buildDate, 0, "me");

        assertEquals(addedBuilds, 1);
    }

    private void addBuildWithRepoKey(long id, String buildName, String buildNumber, long buildDate, String repoKey)
            throws SQLException {
        int addedBuilds = jdbcHelper
                .executeUpdate("INSERT INTO builds " +
                        "(build_id, build_name, build_number, build_date, created, created_by, repo) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)", id, buildName, buildNumber, buildDate, 0, "me", repoKey);

        assertEquals(addedBuilds, 1);
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return v82;
    }
}
