package org.artifactory.storage.db.properties.service;

import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.properties.dao.DbPropertiesDao;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

public class ArtifactoryDbPropertiesServiceImplTest {

    private ArtifactoryDbPropertiesServiceImpl artifactoryDbPropertiesService;

    private DbPropertiesDao dbPropertiesDao;

    @BeforeMethod
    public void setup() {
        dbPropertiesDao = Mockito.mock(DbPropertiesDao.class);
        artifactoryDbPropertiesService = new ArtifactoryDbPropertiesServiceImpl(dbPropertiesDao);
    }

    @Test
    void deleteDbPropertyException() throws SQLException {
        when(dbPropertiesDao.deleteVersion(any())).thenThrow(new SQLException());
        try {
            artifactoryDbPropertiesService.deleteDbProperty(any());
        } catch (StorageException e) {
            assertEquals(e.getMessage(),
                    "Failed to delete Artifactory version from db_properties table in the database");
        }
    }

    @Test
    public void getLatestDbVersionInfo() throws SQLException {
        String latestVersion = "3.1.1";
        int latestRevision = 311;
        String latestProductVersion = "3.1.2";
        DbVersionInfo first = versionInfo(1L, "1.0.0", 100, "1.0.0");
        DbVersionInfo second = versionInfo(5L, "2.0.1", 201, "2.0.2");
        DbVersionInfo last = versionInfo(10L, latestVersion, latestRevision, latestProductVersion);
        List<DbVersionInfo> versions = Stream.of(first, second, last).collect(Collectors.toList());
        when(dbPropertiesDao.getProperties()).thenReturn(versions);
        DbVersionInfo actual = artifactoryDbPropertiesService.getDbVersionInfo();

        assertEquals(latestVersion, actual.getArtifactoryVersion());
        assertEquals(latestRevision, actual.getArtifactoryRevision());
        assertEquals(latestProductVersion, actual.getArtifactoryProductVersion());
    }

    @Test
    public void getLatestDbVersionInfoError() throws SQLException {
        when(dbPropertiesDao.getProperties()).thenThrow(new RuntimeException());
        assertThrows(StorageException.class, () -> artifactoryDbPropertiesService.getDbVersionInfo());
    }

    @Test
    public void getLatestDbVersionInfoEmpty() throws SQLException {
        when(dbPropertiesDao.getProperties()).thenReturn(Collections.EMPTY_LIST);
        DbVersionInfo actual = artifactoryDbPropertiesService.getDbVersionInfo();
        assertNull(actual);
    }

    private DbVersionInfo versionInfo(Long installDate, String version, int revision, String productVersion) {
        return new DbVersionInfo(installDate, version,
                revision, 2, productVersion);
    }
}
