package org.artifactory.storage.db.fs.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.logging.version.LoggingVersion;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.dao.ConfigHistoryDao;
import org.artifactory.storage.db.fs.dao.VersionConfigDao;
import org.artifactory.storage.db.fs.dao.VersionHistoryDao;
import org.artifactory.storage.db.fs.entity.VersionHistoryRecord;
import org.artifactory.storage.fs.service.VersionHistoryService;
import org.jfrog.storage.JdbcHelper;

import java.io.IOException;
import java.sql.SQLException;

@Slf4j
public class VersionHistoryServiceImpl implements VersionHistoryService {

    public final ArtifactoryHome home;
    private final VersionHistoryDao versionHistoryDao;
    private final ConfigHistoryDao configHistoryDao;

    public VersionHistoryServiceImpl(JdbcHelper jdbcHelper, ArtifactoryHome home) {
        this.home = home;
        this.versionHistoryDao = new VersionHistoryDao(jdbcHelper, home);
        this.configHistoryDao = new ConfigHistoryDao(jdbcHelper);
    }

    @Override
    public boolean saveConfig() {
        try {
            return configHistoryDao.saveConfigHistory(home);
        } catch (SQLException | IOException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void saveVersionHistory(String from, String to) {
        try {
            versionHistoryDao.insert(from, to);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public void saveBackwardCompatible(String from, String to) {
        try {
            versionHistoryDao.updateBackwardCompatible(from, to);
        } catch (SQLException e) {
            throw new StorageException(e);
        }
    }

    @Override
    public boolean isRollbackAllowed(String version) {
        // Check if config_history table exists and has version
        try {
            log.info("Checking rollback possibility for version {}", version);
            // Get latest version from history table, as we're allowing only rollback to previous version
            // Compare target version with the one from db.
            VersionHistoryRecord versionHistory = versionHistoryDao.getLatestActive();
            if (versionHistory == null || !versionHistory.version().equalsIgnoreCase(version)) {
                return false;
            }
            log.info("Got version from history {} with backward compatible {}",
                    versionHistory.version(), versionHistory.backwardCompatible());
            return versionHistory.backwardCompatible();
        } catch (Exception e) {
            log.error("Can't get version from history {}", version, e);
            return false;
        }
    }

    @Override
    public void restoreLogbackFromConfigHistory(String version) {
        log.debug("Restoring logback from config history for version {}", version);
        try {
            String logConfig = versionHistoryDao.getConfigDataByVersion(version,
                    VersionConfigDao.LOG_CONFIG_DATA_COLUMN);
            if (StringUtils.isBlank(logConfig)) {
                throw new StorageException("log_config_data is empty for version " + version);
            }
            LoggingVersion.backupAndSaveLogback(logConfig, home.getEtcDir());
            log.info("Restored logback.xml from config history for version {}", version);
        } catch (SQLException | IOException e) {
            throw new StorageException("Failed to restore logback from config history for version " + version, e);
        }
    }
}
