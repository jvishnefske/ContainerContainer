package org.artifactory.storage.db.fs.itest.service;

import org.apache.commons.io.FileUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.fs.dao.ConfigHistoryDao;
import org.artifactory.storage.db.fs.dao.VersionHistoryDao;
import org.artifactory.storage.db.fs.service.VersionHistoryServiceImpl;
import org.artifactory.storage.db.itest.DbBaseTest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.artifactory.common.home.ArtifactoryHome.LOGBACK_CONFIG_FILE_NAME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * DB integration test: rollback restores {@code logback.xml} from {@code config_history.log_config_data}.
 */
@Test
public class RollbackLogbackRestoreIntegrationTest extends DbBaseTest {

    private static final String CONFIG_DATA = "config_data";
    private static final String REPO_CONFIG_DATA = "repo_config_data";
    private static final String LOG_CONFIG_DATA = "<configuration><!-- rollback-integration-marker --></configuration>";
    private static final String UPGRADED_LOGBACK = "<configuration><!-- upgraded-on-disk --></configuration>";
    private static final String ROLLBACK_VERSION = "1.1.1";
    private static final String UPGRADED_VERSION = "1.1.2";
    private static final String LOGBACK_ORIGINAL_BACKUP_FILE_NAME = "logback.original.xml";

    private ConfigHistoryDao configHistoryDao;
    private VersionHistoryDao versionHistoryDao;

    @AfterMethod
    public void cleanup() throws SQLException, IOException {
        if (versionHistoryDao != null) {
            versionHistoryDao.deleteAll();
        }
        File etcDir = ArtifactoryHome.get().getEtcDir();
        FileUtils.deleteQuietly(new File(etcDir, LOGBACK_CONFIG_FILE_NAME));
        FileUtils.deleteQuietly(new File(etcDir, LOGBACK_ORIGINAL_BACKUP_FILE_NAME));
    }

    @Test
    public void restoreLogbackFromConfigHistoryWritesHistoricalLogbackToDisk() throws Exception {
        initDaos();
        seedConfigHistory();

        ArtifactoryHome home = ArtifactoryHome.get();
        File logbackFile = home.getLogbackConfig();
        FileUtils.writeStringToFile(logbackFile, UPGRADED_LOGBACK, StandardCharsets.UTF_8);

        VersionHistoryServiceImpl versionHistoryService = new VersionHistoryServiceImpl(jdbcHelper, home);
        versionHistoryService.restoreLogbackFromConfigHistory(ROLLBACK_VERSION);

        assertEquals(FileUtils.readFileToString(logbackFile, StandardCharsets.UTF_8), LOG_CONFIG_DATA);
        assertEquals(FileUtils.readFileToString(new File(home.getEtcDir(), LOGBACK_ORIGINAL_BACKUP_FILE_NAME),
                StandardCharsets.UTF_8), UPGRADED_LOGBACK);
        assertTrue(logbackFile.exists());
    }

    private void initDaos() {
        ArtifactoryHome home = ArtifactoryHome.get();
        configHistoryDao = new ConfigHistoryDao(jdbcHelper);
        versionHistoryDao = new VersionHistoryDao(jdbcHelper, home);
    }

    private void seedConfigHistory() throws SQLException {
        configHistoryDao.insert(CONFIG_DATA, REPO_CONFIG_DATA, LOG_CONFIG_DATA);
        versionHistoryDao.insert(ROLLBACK_VERSION, UPGRADED_VERSION);
    }
}
