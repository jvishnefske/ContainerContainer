package org.artifactory.storage.db.upgrades.itest.version;

import org.artifactory.storage.db.upgrades.common.UpgradeBaseTest;
import org.artifactory.storage.db.version.ArtifactoryDBVersion;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.CONFIG_BLOB_COLUMN;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.CREATED_COLUMN;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.MODIFIED_COLUMN;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.PACKAGE_TYPE_COLUMN;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.REPO_CONFIG_TABLE;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.REPO_KEY_COLUMN;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.REVISION_COLUMN;
import static org.artifactory.storage.db.repo.dao.RepositoryConfigDao.TYPE_COLUMN;
import static org.testng.Assert.assertTrue;

/**
 * @author Tamar Rosen
 */
@Test
public class V142CreateRepositoryConfigTableTest extends UpgradeBaseTest {
    public void testRepositoryConfigTableExists() throws SQLException {
        assertTrue(tableExists(REPO_CONFIG_TABLE), "Missing table " + REPO_CONFIG_TABLE);
        assertTrue(columnExists(REPO_CONFIG_TABLE, REPO_KEY_COLUMN));
        assertTrue(columnExists(REPO_CONFIG_TABLE, TYPE_COLUMN));
        assertTrue(columnExists(REPO_CONFIG_TABLE, PACKAGE_TYPE_COLUMN));
        assertTrue(columnExists(REPO_CONFIG_TABLE, REVISION_COLUMN));
        assertTrue(columnExists(REPO_CONFIG_TABLE, CREATED_COLUMN));
        assertTrue(columnExists(REPO_CONFIG_TABLE, MODIFIED_COLUMN));
        assertTrue(columnExists(REPO_CONFIG_TABLE, CONFIG_BLOB_COLUMN));
    }

    @Override
    protected ArtifactoryDBVersion upToVersion() {
        return ArtifactoryDBVersion.v142;
    }
}
