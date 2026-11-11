package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.dao.ConfigHistoryDao;
import org.artifactory.storage.db.fs.dao.ConfigsDao;
import org.artifactory.storage.db.fs.dao.VersionConfigDao;
import org.artifactory.storage.db.fs.dao.VersionHistoryDao;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_FILE;
import static org.artifactory.storage.db.fs.dao.ConfigHistoryDao.ARTIFACTORY_REPO_CONFIG_FILE_NAME;
import static org.artifactory.storage.db.fs.dao.VersionConfigDao.CONFIG_DATA_COLUMN;
import static org.artifactory.storage.db.fs.dao.VersionConfigDao.LOG_CONFIG_DATA_COLUMN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

public class VersionConfigDaoTest extends DbBaseTest {

    private VersionConfigDao versionConfigDao;
    private ConfigHistoryDao configHistoryDao;
    private VersionHistoryDao versionHistoryDao;
    @Autowired
    private ConfigsDao configsDao;
    private static final String CONFIG_DATA = "config_data";
    private static final String REPO_CONFIG_DATA = "repo_config_data";
    private static final String LOG_CONFIG_DATA = "log_config_data";
    private static final String VERSION = "1.1.1";
    private static final String TO_VERSION = "1.1.2";

    @BeforeClass
    public void setup() throws SQLException, UnsupportedEncodingException {
        ArtifactoryHome home = new ArtifactoryHome("homeDir");
        home.initDBProperties();
        versionConfigDao = new VersionConfigDao(jdbcHelper);
        configHistoryDao = new ConfigHistoryDao(jdbcHelper);
        versionHistoryDao = new VersionHistoryDao(jdbcHelper,home);
        configsDao.createConfig(ARTIFACTORY_CONFIG_FILE, CONFIG_DATA, System.currentTimeMillis());
        configsDao.createConfig(ARTIFACTORY_REPO_CONFIG_FILE_NAME, REPO_CONFIG_DATA, System.currentTimeMillis());
    }

    @AfterMethod
    public void clean() throws SQLException {
        versionHistoryDao.deleteAll();
    }

    @Test
    void getConfigDataByVersion() throws SQLException, IOException {
        configHistoryDao.insert(CONFIG_DATA, REPO_CONFIG_DATA, LOG_CONFIG_DATA);
        versionHistoryDao.insert(VERSION, TO_VERSION);
        assertEquals(versionConfigDao.getConfigDataByVersion(VERSION, CONFIG_DATA_COLUMN), CONFIG_DATA);
        assertEquals(versionConfigDao.getConfigDataByVersion(VERSION, LOG_CONFIG_DATA_COLUMN), LOG_CONFIG_DATA);
    }

    @Test
    void getConfigDataByVersionEmpty() {
        assertThrows(SQLException.class,
                () -> versionConfigDao.getConfigDataByVersion(VERSION, CONFIG_DATA_COLUMN));
    }

    @Test
    void getConfigDataByVersionInactiveRow() throws SQLException {
        configHistoryDao.insert(CONFIG_DATA, REPO_CONFIG_DATA, LOG_CONFIG_DATA);
        versionHistoryDao.insert(VERSION, TO_VERSION);
        versionHistoryDao.update(VERSION);
        assertThrows(SQLException.class,
                () -> versionConfigDao.getConfigDataByVersion(VERSION, CONFIG_DATA_COLUMN));
    }

    @Test
    void delete() throws SQLException {
        configHistoryDao.insert(CONFIG_DATA, REPO_CONFIG_DATA, LOG_CONFIG_DATA);
        versionHistoryDao.insert(VERSION, TO_VERSION);
        versionConfigDao.deleteHistoryByVersion(VERSION);
        // Check deleted, no history records in db.
        assertNull(versionHistoryDao.getLatestActive());
    }
}
