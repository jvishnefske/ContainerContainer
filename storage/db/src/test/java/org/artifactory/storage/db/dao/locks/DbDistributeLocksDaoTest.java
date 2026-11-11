package org.artifactory.storage.db.dao.locks;

import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.DbProperties;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.postgresql.core.ServerVersion;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.artifactory.common.ConstantValues.dbLockConditionalInsertEnabled;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Unit test to test the distributed lock dao functionalities. For DB test, see
 * {@link org.artifactory.storage.db.fs.itest.dao.DbDistributeLocksDaoTest}
 *
 * @author Shay Bagants
 */
public class DbDistributeLocksDaoTest extends ArtifactoryHomeBoundTest {

    @Mock
    private DbProperties dbProperties;
    @Mock
    private JFrogDataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private PreparedStatement preparedStatement;
    @Mock
    private ResultSet resultSet;
    private AutoCloseable mocks;

    @BeforeMethod
    private void setup() throws SQLException {
        mocks = MockitoAnnotations.openMocks(this);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "provideVersions")
    public void testIsPostgresVersionSupportConditionalInsert(DbType dbType, String dbVersion, boolean expectedResult)
            throws SQLException {
        prepareMocks(dbType, dbVersion);
        DbDistributeLocksDao locksDao = new DbDistributeLocksDao(dataSource, dbProperties, null);
        boolean conditionalLockSupported = locksDao.isPostgresVersionSupportConditionalInsert();
        assertEquals(expectedResult, conditionalLockSupported);
    }

    @DataProvider
    public Object[][] provideVersions() {
        return new Object[][] {
                // dbType, version, expectedResult
                {DbType.POSTGRESQL, "8.4.0", false},
                {DbType.POSTGRESQL, "9.4.9", false},
                {DbType.POSTGRESQL, "9.5.0", true},
                {DbType.POSTGRESQL, "9.5.1", true},
                {DbType.POSTGRESQL, "9.5.1.1", true},
                {DbType.POSTGRESQL, "9.5.12", true},
                {DbType.POSTGRESQL, "9.5.12.4", true},
                {DbType.POSTGRESQL, "9.6", true},
                {DbType.POSTGRESQL, "9.6.11", true},
                {DbType.POSTGRESQL, "9.6.11-a", true},
                {DbType.POSTGRESQL, "10.12", true},
                {DbType.POSTGRESQL, "10.0", true},
                {DbType.POSTGRESQL, "10.0.0", true},
                {DbType.POSTGRESQL, "10.0.1", true},
                {DbType.POSTGRESQL, "10.1.0", true},
                {DbType.POSTGRESQL, "10.5.0", true},
                {DbType.POSTGRESQL, "11.5.0", true},
                {DbType.POSTGRESQL, "15.0.0", true},
                {DbType.POSTGRESQL, "unknown", false},

                {DbType.DERBY, "9.4.9", true},
                {DbType.ORACLE, "9.4.9", true},
                {DbType.MYSQL, "9.4.9", true},
                {DbType.MSSQL, "9.4.9", true},
                {DbType.MARIADB, "9.4.9", true},
        };
    }

    @Test(dataProvider = "provideVersionsAndProps")
    public void testIsConditionalInsertEnabled(DbType dbType, String dbVersion, boolean conditionalLockSysPropEnabled,
            boolean expectedResult) throws SQLException {
        homeStub.setProperty(dbLockConditionalInsertEnabled, String.valueOf(conditionalLockSysPropEnabled));
        prepareMocks(dbType, dbVersion);
        DbDistributeLocksDao locksDao = new DbDistributeLocksDao(dataSource, dbProperties, null);
        boolean conditionalLockSupported = locksDao.isConditionalInsertEnabled();
        assertEquals(expectedResult, conditionalLockSupported);
    }

    @DataProvider
    public Object[][] provideVersionsAndProps() {
        return new Object[][] {
                // dbType, version, sysPropEnabled, expectedResult
                {DbType.POSTGRESQL, "7.0", true, false},
                {DbType.POSTGRESQL, "7.0", false, false},
                {DbType.POSTGRESQL, "8.9.0", true, false},
                {DbType.POSTGRESQL, "8.9.0", false, false},
                {DbType.POSTGRESQL, "9.5.0", true, true},
                {DbType.POSTGRESQL, "9.5.0", false, false},
                {DbType.POSTGRESQL, "9.5.1", true, true},
                {DbType.POSTGRESQL, "9.5.1", false, false},
                {DbType.POSTGRESQL, "10.0", true, true},
                {DbType.POSTGRESQL, "10.0", false, false},
                {DbType.POSTGRESQL, "10.0.0", true, true},
                {DbType.POSTGRESQL, "10.0.0", false, false},
                {DbType.POSTGRESQL, "10.0", true, true},
                {DbType.POSTGRESQL, "10.0", false, false},
                {DbType.POSTGRESQL, "unknown", true, false },
                {DbType.POSTGRESQL, "unknown", false, false},

                {DbType.ORACLE, "some-version", true, true},
                {DbType.ORACLE, "some-version", false, false},

                // unsupported DB's
                {DbType.DERBY, "9.4.9", true, false},
                {DbType.DERBY, "9.4.9", false, false},
                {DbType.DERBY, "11.0.0", true, false},
                {DbType.DERBY, "11.0.0", false, false},
                {DbType.MSSQL, "some-version", true, false},
                {DbType.MSSQL, "some-version", false, false},
                {DbType.MARIADB, "some-version", true, false},
                {DbType.MARIADB, "some-version", false, false},
                {DbType.MYSQL, "some-version", true, false},
                {DbType.MYSQL, "some-version", false, false}
        };
    }

    private void prepareMocks(DbType dbType, String dbVersion) throws SQLException {
        int dbVersionNumber = ServerVersion.from(dbVersion).getVersionNum();
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(connection.prepareStatement("show server_version_num;")).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(dbVersionNumber);
    }
}