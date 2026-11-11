package org.artifactory.common.config.db;

import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.jfrog.storage.JdbcHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.common.config.db.DbVersionUtil.ARTIFACTORY_RELEASE_COLUMN;
import static org.artifactory.common.config.db.DbVersionUtil.ARTIFACTORY_REVISION_COLUMN;
import static org.artifactory.common.config.db.DbVersionUtil.ARTIFACTORY_SERVICE_VERSION_COLUMN;
import static org.artifactory.common.config.db.DbVersionUtil.INSTALLATION_DATE_COLUMN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionDaoTest {

    @Test
    void testGetDbServiceVersion() throws SQLException {
        ResultSet resultSetMock = mock(ResultSet.class);
        when(resultSetMock.getLong(INSTALLATION_DATE_COLUMN)).thenReturn(1L);
        when(resultSetMock.getString(ARTIFACTORY_SERVICE_VERSION_COLUMN)).thenReturn("7.70");
        when(resultSetMock.getInt(ARTIFACTORY_REVISION_COLUMN)).thenReturn(1234);
        when(resultSetMock.getLong(ARTIFACTORY_RELEASE_COLUMN)).thenReturn(233L);
        when(resultSetMock.next()).thenReturn(true, false);
        JdbcHelper helperMock = mock(JdbcHelper.class);
        when(helperMock.executeSelect(anyString(), any(Object[].class))).thenReturn(resultSetMock);
        DbVersionInfo dbVersionInfo = new DbVersionInfo(1L, "7.70", 1234, 233L, null);
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        DbVersionInfo dbVersionInfo1 = dbVersionDao.getDbServiceVersion();
        assertEquals(dbVersionInfo.getArtifactoryVersion(), dbVersionInfo1.getArtifactoryVersion());
        assertEquals(dbVersionInfo.getInstallationDate(), dbVersionInfo1.getInstallationDate());
        assertEquals(dbVersionInfo.getArtifactoryRevision(),
                dbVersionInfo1.getArtifactoryRevision());
        assertEquals(dbVersionInfo.getArtifactoryRelease(), dbVersionInfo1.getArtifactoryRelease());
    }

    @Test
    void testGetVersionException() throws SQLException {
        JdbcHelper helperMock = mock(JdbcHelper.class);
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        when(helperMock.executeSelect(anyString(), any())).thenThrow(new SQLException("heyy"));
        assertThrows(IllegalStateException.class, dbVersionDao::getDbServiceVersion);
    }

    @Test
    void testGetVersionWhenEmpty() throws SQLException {
        JdbcHelper helperMock = mock(JdbcHelper.class);
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        ResultSet resultSetMock = mock(ResultSet.class);
        when(helperMock.executeSelect(anyString(), any(Object[].class))).thenReturn(resultSetMock);
        assertNull(dbVersionDao.getDbServiceVersion());
    }

    @ParameterizedTest
    @CsvSource({
            "1, 'false'",
            "0, 'true'",
    })
    void testIsEmpty(long size, boolean expectedResult) throws SQLException {
        JdbcHelper helperMock = mock(JdbcHelper.class);
        when(helperMock.executeSelectLong(anyString(), any(Object[].class))).thenReturn(size);
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        assertEquals(dbVersionDao.isDbPropertiesEmpty(), expectedResult);
    }

    @Test
    void testIsEmptyException() throws SQLException {
        JdbcHelper helperMock = mock(JdbcHelper.class);
        when(helperMock.executeSelectLong(anyString(), any(Object[].class))).thenThrow(new SQLException("hey"));
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        assertThrows(IllegalStateException.class, dbVersionDao::isDbPropertiesEmpty);
    }

    @ParameterizedTest
    @CsvSource({
            "1, 'true'",
            "0, 'false'",
    })
    void testInsertVersion(int size, boolean expectedResult) throws SQLException {
        JdbcHelper helperMock = mock(JdbcHelper.class);
        when(helperMock.executeUpdate(anyString(), any(Object[].class))).thenReturn(size);
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        DbVersionInfo dbVersionInfo = new DbVersionInfo(1L, "7.70", 1234, 233L, null);
        assertEquals(dbVersionDao.createVersion(dbVersionInfo), expectedResult);
    }

    @Test
    void testInsertVersionException() throws SQLException {
        JdbcHelper helperMock = mock(JdbcHelper.class);
        when(helperMock.executeUpdate(anyString(), any(Object[].class))).thenThrow(new SQLException("hey"));
        DbVersionDao dbVersionDao = new DbVersionDao(helperMock);
        DbVersionInfo dbVersionInfo = new DbVersionInfo(1L, "7.70", 1234, 233L, null);
        assertThrows(IllegalStateException.class, () -> dbVersionDao.createVersion(dbVersionInfo));
    }
}