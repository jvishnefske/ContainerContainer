package org.artifactory.storage.db.fs.itest.service;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.fs.dao.ConfigHistoryDao;
import org.artifactory.storage.db.fs.dao.VersionHistoryDao;
import org.artifactory.storage.db.fs.entity.VersionHistoryRecord;
import org.artifactory.storage.db.fs.service.VersionHistoryServiceImpl;
import org.jfrog.storage.JdbcHelper;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class VersionHistoryServiceImplTest {

    private VersionHistoryServiceImpl versionHistoryService;

    private VersionHistoryDao versionHistoryDao;

    private ConfigHistoryDao configHistoryDao;

    private final static String FIRST_VERSION = "1.1.1";

    private final static String PREV_VERSION = "1.1.2";

    @BeforeMethod
    public void setup() throws SQLException {
        ArtifactoryHome home = new ArtifactoryHome("homeDir");
        home.initDBProperties();
        JdbcHelper jdbcHelper1 = Mockito.mock(JdbcHelper.class);
        versionHistoryService = new VersionHistoryServiceImpl(jdbcHelper1, home);
        versionHistoryDao = Mockito.mock(VersionHistoryDao.class);
        configHistoryDao = Mockito.mock(ConfigHistoryDao.class);
        ReflectionTestUtils.setField(versionHistoryService, "configHistoryDao", configHistoryDao);
        ReflectionTestUtils.setField(versionHistoryService, "versionHistoryDao", versionHistoryDao);
    }

    @AfterMethod
    public void clear() {
        Mockito.clearInvocations();
    }

    @Test
    void isRollbackAllowed() throws SQLException {
        VersionHistoryRecord record = new VersionHistoryRecord(1, PREV_VERSION, 1,
                true, true);
        Mockito.when(versionHistoryDao.getLatestActive()).thenReturn(record);
        assertTrue(versionHistoryService.isRollbackAllowed(PREV_VERSION));
    }

    @Test
    void isRollbackAllowedNegative() throws SQLException {
        VersionHistoryRecord record = new VersionHistoryRecord(1, PREV_VERSION, 1,
                true, true);
        Mockito.when(versionHistoryDao.getLatestActive()).thenReturn(record);
        assertFalse(versionHistoryService.isRollbackAllowed(FIRST_VERSION));
    }

    @Test
    void isRollbackAllowedError() throws SQLException {
        Mockito.when(versionHistoryDao.getLatestActive()).thenThrow(new StorageException("Failed"));
        assertFalse(versionHistoryService.isRollbackAllowed(FIRST_VERSION));
    }

    @Test
    void saveConfig() throws SQLException, IOException {
        versionHistoryService.saveConfig();
        verify(configHistoryDao, times(1)).saveConfigHistory(any());
    }

    @Test
    void saveConfigException() throws SQLException, IOException {
        when(configHistoryDao.saveConfigHistory(any())).thenThrow(new SQLException());
        assertThrows(StorageException.class, () -> versionHistoryService.saveConfig());
    }

    @Test
    void saveVersionHistory() throws SQLException {
        String from = "1.1.1";
        String to = "1.1.2";
        versionHistoryService.saveVersionHistory(from, to);
        verify(versionHistoryDao, times(1)).insert(from, to);
    }

    @Test
    void saveVersionHistoryException() throws SQLException {
        when(versionHistoryDao.insert(any(), any())).thenThrow(new SQLException());
        assertThrows(StorageException.class, () -> versionHistoryService.saveVersionHistory(any(), any()));
    }

    @Test
    void saveBackwardCompatible() throws SQLException {
        String from = "1.1.1";
        String to = "1.1.2";
        versionHistoryService.saveBackwardCompatible(from, to);
        verify(versionHistoryDao, times(1)).updateBackwardCompatible(from, to);
    }

    @Test
    void saveBackwardCompatibleException() throws SQLException {
        when(versionHistoryDao.updateBackwardCompatible(any(), any())).thenThrow(new SQLException());
        assertThrows(StorageException.class, () -> versionHistoryService.saveBackwardCompatible(any(), any()));
    }
}
