package org.artifactory.storage.db.init;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.DbType;
import org.jfrog.storage.HikariJDBCDataSource;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.metric.MetricsDataSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Properties;

import static org.artifactory.common.config.db.ArtifactoryDbProperties.DEFAULT_POOL_TYPE;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test
public class CommonDataSourceFactoryTest extends ArtifactoryHomeBoundTest {

    @Mock
    ArtifactoryDbProperties artifactoryDbProperties;
    private AutoCloseable mocks;

    @BeforeMethod
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(artifactoryDbProperties.getProperty(eq("connectivityCheckTimeoutMs"), anyString()))
                .thenReturn("5000");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    public void artifactoryTestInitTomcatDataSourceParameters() throws SQLException {
        prepareDefaultTomcatDataSource();

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDataSource(artifactoryDbProperties, true).unwrap(ArtifactoryTomcatDataSource.class);

        // When temporaryDbChannel channel set to true, it overwrites the default values.
        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 1);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 1);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 0);
    }

    @Test
    public void artifactoryTestInitArtifactoryDataSourceParameters() throws SQLException {
        prepareDefaultTomcatDataSource();

        ArtifactoryDataSource artifactoryDataSource = CommonDataSourceFactory
                .createDataSource(artifactoryDbProperties, true).unwrap(ArtifactoryDataSource.class);

        // When temporaryDbChannel channel set to true, it overwrites the default values.
        assertEquals(artifactoryDataSource.getMaxActive(), 1);
        assertEquals(artifactoryDataSource.getMaxIdle(), 1);
        assertEquals(artifactoryDataSource.getMinIdle(), 0);
    }

    @Test
    public void artifactoryTestTomcatDataSourceParameters() throws SQLException {
        prepareDefaultTomcatDataSource();

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDataSource(artifactoryDbProperties, false).unwrap(ArtifactoryTomcatDataSource.class);

        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 5);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 5);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 5);
    }

    @Test
    public void artifactoryTestInitHikariDataSourceParameters() throws SQLException {
        prepareDefaultHikariDataSource();

        ArtifactoryHikariDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createDataSource(artifactoryDbProperties, true).unwrap(ArtifactoryHikariDataSource.class);
        // When temporaryDbChannel channel set to true, it overwrites the default values.
        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 1);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 0);
    }

    @Test
    public void artifactoryTestHikariDataSourceParameters() throws SQLException {
        prepareDefaultHikariDataSource();
        when(artifactoryDbProperties.getMinIdleConnections()).thenReturn(1);

        ArtifactoryHikariDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createDataSource(artifactoryDbProperties, false).unwrap(ArtifactoryHikariDataSource.class);

        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 5);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 1);
    }

    @Test
    public void artifactoryTestTomcatCreateUniqueDataSourceByType() throws SQLException {
        prepareDefaultTomcatDataSource();

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createUniqueDataSourceByType(artifactoryDbProperties).unwrap(ArtifactoryTomcatDataSource.class);

        // The createUniqueDataSourceByType overwrites the default values.
        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 1);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 1);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 0);
        assertEquals(artifactoryTomcatDataSource.getInitialSize(), 0);
    }

    @Test
    public void artifactoryTestHikariCreateUniqueDataSourceByType() throws SQLException {
        prepareDefaultHikariDataSource();
        when(artifactoryDbProperties.getIntProperty("minIdle", 0)).thenReturn(1);
        when(artifactoryDbProperties.getLongProperty("leakDetectionThreshold", 60_000)).thenReturn(60_000L);

        HikariJDBCDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createUniqueDataSourceByType(artifactoryDbProperties).unwrap(HikariJDBCDataSource.class);

        // The createUniqueDataSourceByType overwrites the default values.
        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 1);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 1);
        assertEquals(artifactoryHikariDataSource.getLeakDetectionThreshold(), 60_000);
    }

    @Test
    public void artifactoryTestTomcatWithDbLock() throws SQLException {
        prepareDefaultTomcatDataSource();

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, true)
                .unwrap(ArtifactoryTomcatDataSource.class);

        // When temporaryDbChannel channel set to true, it overwrites the default values.
        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 1);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 1);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 0);
        verify(artifactoryDbProperties, times(0)).getLockingDbDriverClass();

        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.DERBY));
        artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, true)
                .unwrap(ArtifactoryTomcatDataSource.class);

        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 1);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 1);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 0);
        verify(artifactoryDbProperties, times(1)).getLockingDbDriverClass();
    }

    @Test
    public void artifactoryTestTomcatWithDbLockMaxActiveCapped() throws SQLException {
        prepareDefaultTomcatDataSource();
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());
        when(artifactoryDbProperties.getIntProperty(eq(ArtifactoryDbProperties.LOCKS_MAX_ACTIVE_CONNECTIONS), anyInt()))
                .thenReturn(3);

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, false)
                .unwrap(ArtifactoryTomcatDataSource.class);

        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 3);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 3);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 3);
    }

    @Test
    public void artifactoryTestTomcatWithDbLockSkipsCapOnTemporaryChannel() throws SQLException {
        prepareDefaultTomcatDataSource();
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());
        when(artifactoryDbProperties.getIntProperty(eq(ArtifactoryDbProperties.LOCKS_MAX_ACTIVE_CONNECTIONS), anyInt()))
                .thenReturn(3);

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, true)
                .unwrap(ArtifactoryTomcatDataSource.class);

        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 1);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 1);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 0);
    }

    @Test
    public void artifactoryTestTomcatWithDbLockMaxActiveDefaultsToGeneralPool() throws SQLException {
        prepareDefaultTomcatDataSource();
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());

        ArtifactoryTomcatDataSource artifactoryTomcatDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, false)
                .unwrap(ArtifactoryTomcatDataSource.class);

        assertEquals(artifactoryTomcatDataSource.getMaxActive(), 5);
        assertEquals(artifactoryTomcatDataSource.getMaxIdle(), 5);
        assertEquals(artifactoryTomcatDataSource.getMinIdle(), 5);
    }

    @Test
    public void artifactoryTestHikariWithDbLock() throws SQLException {
        prepareDefaultHikariDataSource();
        when(artifactoryDbProperties.getLongProperty("leakDetectionThreshold", 60_000)).thenReturn(60_000L);
        when(artifactoryDbProperties.getLockingDbDriverClass()).thenReturn("org.apache.derby.jdbc.EmbeddedDriver");

        HikariJDBCDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, true).unwrap(HikariJDBCDataSource.class);
        // When temporaryDbChannel channel set to true, it overwrites the default values.
        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 1);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 0);
        verify(artifactoryDbProperties, times(0)).getLockingDbDriverClass();

        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.DERBY));
        artifactoryHikariDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, true).unwrap(HikariJDBCDataSource.class);
        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 1);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 0);
        verify(artifactoryDbProperties, times(1)).getLockingDbDriverClass();
    }

    @Test
    public void artifactoryTestHikariWithDbLockMaxActiveCapped() throws SQLException {
        prepareDefaultHikariDataSource();
        when(artifactoryDbProperties.getMinIdleConnections()).thenReturn(4);
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());
        when(artifactoryDbProperties.getIntProperty(eq(ArtifactoryDbProperties.LOCKS_MAX_ACTIVE_CONNECTIONS), anyInt()))
                .thenReturn(3);

        HikariJDBCDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, false)
                .unwrap(HikariJDBCDataSource.class);

        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 3);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 3);
    }

    @Test
    public void artifactoryTestHikariWithDbLockSkipsCapOnTemporaryChannel() throws SQLException {
        prepareDefaultHikariDataSource();
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());
        when(artifactoryDbProperties.getIntProperty(eq(ArtifactoryDbProperties.LOCKS_MAX_ACTIVE_CONNECTIONS), anyInt()))
                .thenReturn(3);

        HikariJDBCDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, true)
                .unwrap(HikariJDBCDataSource.class);

        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 1);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 0);
    }

    @Test
    public void artifactoryTestHikariWithDbLockMaxActiveDefaultsToGeneralPool() throws SQLException {
        prepareDefaultHikariDataSource();
        when(artifactoryDbProperties.getMinIdleConnections()).thenReturn(4);
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());

        HikariJDBCDataSource artifactoryHikariDataSource = CommonDataSourceFactory
                .createDifferentDatasourceForDbLock(artifactoryDbProperties, false)
                .unwrap(HikariJDBCDataSource.class);

        assertEquals(artifactoryHikariDataSource.getMaximumPoolSize(), 5);
        assertEquals(artifactoryHikariDataSource.getMinimumIdle(), 4);
    }

    @Test
    public void createDifferentDatasourceForDbLockThrowsWhenMaxActiveConnectionsIsZero() {
        prepareDefaultTomcatDataSource();
        when(artifactoryDbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());
        when(artifactoryDbProperties.getMaxActiveConnections()).thenReturn(0);

        try {
            CommonDataSourceFactory.createDifferentDatasourceForDbLock(artifactoryDbProperties, false);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("maxActiveConnections must be > 0"));
        }
    }

    @Test
    public void unwrapPoolSucceedsViaMetricsDataSourceWhenDirectWrapperCheckIsFalse() throws Exception {
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        MetricsDataSource metricsDataSource = mock(MetricsDataSource.class);
        ArtifactoryTomcatDataSource expectedPool = mock(ArtifactoryTomcatDataSource.class);
        when(dataSource.isWrapperFor(ArtifactoryTomcatDataSource.class)).thenReturn(false);
        when(dataSource.isWrapperFor(MetricsDataSource.class)).thenReturn(true);
        when(dataSource.unwrap(MetricsDataSource.class)).thenReturn(metricsDataSource);
        when(metricsDataSource.unwrap(ArtifactoryTomcatDataSource.class)).thenReturn(expectedPool);

        ArtifactoryTomcatDataSource result = invokeUnwrapPool(dataSource, ArtifactoryTomcatDataSource.class);

        assertEquals(result, expectedPool);
    }

    @Test
    public void unwrapPoolFallsBackToMetricsWhenDirectUnwrapThrows() throws Exception {
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        MetricsDataSource metricsDataSource = mock(MetricsDataSource.class);
        ArtifactoryTomcatDataSource expectedPool = mock(ArtifactoryTomcatDataSource.class);
        when(dataSource.isWrapperFor(ArtifactoryTomcatDataSource.class)).thenReturn(true);
        when(dataSource.unwrap(ArtifactoryTomcatDataSource.class)).thenThrow(new SQLException("direct failed"));
        when(dataSource.unwrap(MetricsDataSource.class)).thenReturn(metricsDataSource);
        when(metricsDataSource.unwrap(ArtifactoryTomcatDataSource.class)).thenReturn(expectedPool);

        ArtifactoryTomcatDataSource result = invokeUnwrapPool(dataSource, ArtifactoryTomcatDataSource.class);

        assertEquals(result, expectedPool);
    }

    @Test
    public void unwrapPoolThrowsIllegalStateWhenBothDirectAndMetricsFallbackFail() throws Exception {
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        MetricsDataSource metricsDataSource = mock(MetricsDataSource.class);
        SQLException directFailed = new SQLException("direct failed");
        SQLException metricsFailed = new SQLException("metrics fallback failed");
        when(dataSource.isWrapperFor(ArtifactoryTomcatDataSource.class)).thenReturn(true);
        when(dataSource.unwrap(ArtifactoryTomcatDataSource.class)).thenThrow(directFailed);
        when(dataSource.unwrap(MetricsDataSource.class)).thenReturn(metricsDataSource);
        when(metricsDataSource.unwrap(ArtifactoryTomcatDataSource.class)).thenThrow(metricsFailed);

        try {
            invokeUnwrapPool(dataSource, ArtifactoryTomcatDataSource.class);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertEquals(e.getCause(), metricsFailed);
            assertEquals(e.getCause().getSuppressed()[0], directFailed);
        }
    }

    @Test
    public void unwrapPoolSkipsMetricsRetryWhenMetricsWrapperWasCheckedAsFalse() throws Exception {
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        SQLException directUnwrapFailed = new SQLException("direct unwrap failed");
        when(dataSource.isWrapperFor(ArtifactoryTomcatDataSource.class)).thenReturn(false);
        when(dataSource.isWrapperFor(MetricsDataSource.class)).thenReturn(false);
        when(dataSource.unwrap(ArtifactoryTomcatDataSource.class)).thenThrow(directUnwrapFailed);

        try {
            invokeUnwrapPool(dataSource, ArtifactoryTomcatDataSource.class);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getCause() instanceof SQLException);
            SQLException thrownDirectUnwrapFailed = (SQLException) e.getCause();
            assertEquals(thrownDirectUnwrapFailed, directUnwrapFailed);
            verify(dataSource, times(0)).unwrap(MetricsDataSource.class);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeUnwrapPool(JFrogDataSource dataSource, Class<T> poolType) throws Exception {
        Method unwrapPool = CommonDataSourceFactory.class.getDeclaredMethod(
                "unwrapPool", JFrogDataSource.class, Class.class);
        unwrapPool.setAccessible(true);
        try {
            return (T) unwrapPool.invoke(null, dataSource, poolType);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    // Prepares default params for tomcat data source.
    // getDriverProperties() must be stubbed non-null because JFrogDataSourceWrapper's
    // constructor reads it via hasDriverSocketTimeout() (jfrog-commons 13.4.0+, CMNS-643).
    // Tracked upstream as CMNS-647 for a library-side null guard.
    private void prepareDefaultTomcatDataSource() {
        when(artifactoryDbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE))
                .thenReturn("tomcat-jdbc");
        when(artifactoryDbProperties.getMaxIdleConnections()).thenReturn(5);
        when(artifactoryDbProperties.getIntProperty("minIdle", 1)).thenReturn(5);
        when(artifactoryDbProperties.getMaxActiveConnections()).thenReturn(5);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(artifactoryDbProperties.getDriverProperties()).thenReturn(new Properties());
    }

    // Prepares default params for hikari data source.
    // Params such as validationQueryTimeout or org.apache.derby.jdbc.EmbeddedDriver
    // are required to init the data source
    private void prepareDefaultHikariDataSource() {
        when(artifactoryDbProperties.getProperty(ArtifactoryDbProperties.Key.POOL_TYPE, DEFAULT_POOL_TYPE))
                .thenReturn("hikari");
        when(artifactoryDbProperties.getMaxActiveConnections()).thenReturn(5);
        when(artifactoryDbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(artifactoryDbProperties.getDriverClass()).thenReturn("org.apache.derby.jdbc.EmbeddedDriver");
        when(artifactoryDbProperties.getIntProperty("validationQueryTimeout", 5000)).thenReturn(5000);
        when(artifactoryDbProperties.getDriverProperties()).thenReturn(new Properties());
    }
}