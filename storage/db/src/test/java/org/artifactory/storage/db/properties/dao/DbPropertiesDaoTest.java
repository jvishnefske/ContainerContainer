package org.artifactory.storage.db.properties.dao;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.jfrog.storage.DbType;
import org.jfrog.storage.util.DbUtils;
import org.mockito.MockedStatic;
import org.testng.annotations.Test;

import java.sql.SQLException;

import static org.artifactory.common.config.db.DbVersionUtil.DB_PROPERTIES_TABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;

public class DbPropertiesDaoTest {

    @Test
    void testGetDbServiceVersion() throws SQLException {
        ArtifactoryJdbcHelper helperMock = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(helperMock.executeUpdate(anyString(), any())).thenReturn(0);
        DbVersionInfo dbVersionInfo = new DbVersionInfo(1L, "7.70", 1234, 233L, null);
        DbPropertiesDao dbVersionDao = new DbPropertiesDao(helperMock, artifactoryDbProperties);
        assertFalse(dbVersionDao.deleteVersion(dbVersionInfo));
    }

    @Test
    void testTableExists() throws SQLException {
        ArtifactoryJdbcHelper helperMock = mock(ArtifactoryJdbcHelper.class);
        ArtifactoryDbProperties artifactoryDbProperties = mock(ArtifactoryDbProperties.class);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        try (MockedStatic<DbUtils> dbUtilsMockedStatic = mockStatic(DbUtils.class)) {
            dbUtilsMockedStatic.when(() -> DbUtils.tableExists(helperMock, DbType.POSTGRESQL, DB_PROPERTIES_TABLE))
                    .thenReturn(false);
            DbPropertiesDao dbVersionDao = new DbPropertiesDao(helperMock, artifactoryDbProperties);
            assertFalse(dbVersionDao.isDbPropertiesTableExists());
        }
    }
}
