package org.artifactory.storage.db.fs.itest.dao;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.dao.ConfigHistoryDao;
import org.artifactory.storage.db.fs.dao.ConfigsDao;
import org.artifactory.storage.db.fs.entity.ConfigHistoryRecord;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.List;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_CONFIG_FILE;
import static org.artifactory.storage.db.fs.dao.ConfigHistoryDao.ARTIFACTORY_REPO_CONFIG_FILE_NAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

@Test
public class ConfigHistoryDaoTest extends DbBaseTest {

    private ConfigHistoryDao configHistoryDao;
    @Autowired
    private ConfigsDao configsDao;

    private static final String CONFIG_DATA = "config_data";
    private static final String REPO_CONFIG_DATA = "repo_config_data";
    private static final String LOG_CONFIG_DATA = "log_config_data";

    @BeforeClass
    public void setup() throws SQLException, UnsupportedEncodingException {
        ArtifactoryHome home = new ArtifactoryHome("homeDir");
        home.initDBProperties();
        configHistoryDao = new ConfigHistoryDao(jdbcHelper);
        configsDao.createConfig(ARTIFACTORY_CONFIG_FILE, CONFIG_DATA, System.currentTimeMillis());
        configsDao.createConfig(ARTIFACTORY_REPO_CONFIG_FILE_NAME, REPO_CONFIG_DATA, System.currentTimeMillis());
    }

    @AfterClass
    public void clean() throws SQLException, UnsupportedEncodingException {
        configsDao.deleteConfig(ARTIFACTORY_CONFIG_FILE);
        configsDao.deleteConfig(ARTIFACTORY_REPO_CONFIG_FILE_NAME);
    }

    @Test
    void loadConfig() throws SQLException {
        var result = configHistoryDao.loadConfig(
                List.of(ARTIFACTORY_CONFIG_FILE, ARTIFACTORY_REPO_CONFIG_FILE_NAME));
        ConfigHistoryDao.Config configData = result.stream()
                .filter(r -> r.name().equalsIgnoreCase(ARTIFACTORY_CONFIG_FILE))
                .findFirst().orElse(null);
        ConfigHistoryDao.Config repoConfigData = result.stream()
                .filter(r -> r.name().equalsIgnoreCase(ARTIFACTORY_REPO_CONFIG_FILE_NAME))
                .findFirst().orElse(null);
        assertEquals(result.size(), 2);
        assertEquals(configData.data(), CONFIG_DATA);
        assertEquals(ARTIFACTORY_CONFIG_FILE, configData.name());
        assertEquals(ARTIFACTORY_REPO_CONFIG_FILE_NAME, repoConfigData.name());
        assertEquals(repoConfigData.data(), REPO_CONFIG_DATA);
    }

    @Test
    void insert() throws SQLException, IOException {
        boolean saved = configHistoryDao.insert(CONFIG_DATA, REPO_CONFIG_DATA, LOG_CONFIG_DATA);
        int id = configHistoryDao.getLatestId();
        ConfigHistoryRecord configHistory = configHistoryDao.get(id);

        assertEquals(configHistory.configData(), CONFIG_DATA);
        assertEquals(configHistory.repoConfigData(), REPO_CONFIG_DATA);
        assertEquals(configHistory.logConfigData(), LOG_CONFIG_DATA);
        assertTrue(saved);
    }

    @Test
    void insertMissingRepoConfig() throws SQLException, IOException {
        assertTrue(configHistoryDao.insert(CONFIG_DATA, null, LOG_CONFIG_DATA));
        ConfigHistoryRecord configHistory = configHistoryDao.get(configHistoryDao.getLatestId());
        assertEquals(configHistory.configData(), CONFIG_DATA);
        assertEquals(configHistory.repoConfigData(), ConfigHistoryDao.MISSING_REPO_CONFIG_PLACEHOLDER);
        assertEquals(configHistory.logConfigData(), LOG_CONFIG_DATA);
    }

    @Test
    void insertMissingLogConfig() throws SQLException, IOException {
        assertFalse(configHistoryDao.insert(CONFIG_DATA, REPO_CONFIG_DATA, null));
    }

    @Test
    public void getLastIdEmpty() {
        assertThrows(SQLException.class, () -> configHistoryDao.getLatestId());
    }

    @Test
    public void getEmpty() {
        assertThrows(SQLException.class, () -> configHistoryDao.get(1));
    }
}
