package org.artifactory.storage.db;

import ch.qos.logback.classic.Level;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.storage.db.properties.DbVersionInfo;
import org.artifactory.storage.db.init.ArtifactoryTomcatDataSource;
import org.artifactory.storage.db.init.DbInitializationManager;
import org.artifactory.storage.db.properties.service.ArtifactoryDbPropertiesService;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.util.IdGenerator;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.artifactory.test.TestUtils;
import org.awaitility.Awaitility;
import org.jfrog.storage.DataSourceHealthException;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;


/**
 * @author Roman Gurevitch.
 */
@Slf4j
@Test
public class DbServiceImplTest extends ArtifactoryHomeBoundTest {

    private DbServiceImpl dbService;

    @Mock
    private ArtifactoryJdbcHelper jdbcHelper;
    @Mock
    private JFrogDataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData dbMetadata;
    @Mock
    private ArtifactoryTomcatDataSource lockDatasource;
    @Mock
    private Connection lockDbConnection;
    @Mock
    private DatabaseMetaData lockConnectionMetadata;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    @Mock
    private ArtifactoryDbPropertiesService dbPropertiesService;
    @Mock
    private IdGenerator idGenerator;

    private Level logLevel;
    private AutoCloseable mocks;

    @BeforeClass
    public void beforeClass() {
        Logger logger = LoggerFactory.getILoggerFactory().getLogger(DbServiceImplTest.class.getName());
        logLevel = ((ch.qos.logback.classic.Logger) logger).getEffectiveLevel();
        TestUtils.setLoggingLevel(DbServiceImplTest.class, Level.INFO);
    }

    @AfterClass
    public void afterClass() {
        TestUtils.setLoggingLevel(DbServiceImplTest.class, logLevel);
    }

    private final Set<JFrogDataSource> allDataSources = Sets.newHashSet();

    // the ddl are here to ensure that if the file we are using is changed (conversion ddl), we will not accidentally
    //miss it
    private final List<String> ORACLE_DISTRIBUTED_LOCK_INDEXES = ImmutableList.of();
    private final String ORACLE_DISTRIBUTED_LOCK_SQL = "CREATE TABLE distributed_locks (\n" +
            "  category       VARCHAR(64)    NOT NULL,\n" +
            "  lock_key       VARCHAR(255)   NOT NULL,\n" +
            "  owner          VARCHAR(64)    NOT NULL,\n" +
            "  owner_thread   NUMBER(19, 0)  NOT NULL,\n" +
            "  owner_thread_name     VARCHAR(64)   NOT NULL,\n" +
            "  acquire_time   NUMBER(19, 0)  NOT NULL,\n" +
            "  CONSTRAINT locks_pk PRIMARY KEY (category,lock_key)\n" +
            ")";

    private final List<String> MSSQL_DISTRIBUTED_LOCK_INDEXES = ImmutableList.of();
    private final String MSSQL_DISTRIBUTED_LOCK_SQL = "CREATE TABLE distributed_locks (\n" +
            "  category       VARCHAR(64)   NOT NULL,\n" +
            "  lock_key       VARCHAR(255)  NOT NULL,\n" +
            "  owner          VARCHAR(64)   NOT NULL,\n" +
            "  owner_thread   BIGINT        NOT NULL,\n" +
            "  owner_thread_name     VARCHAR(64)   NOT NULL,\n" +
            "  acquire_time   BIGINT        NOT NULL,\n" +
            "  CONSTRAINT locks_pk PRIMARY KEY (category,lock_key)\n" +
            ")";

    private final List<String> MYSQL_DISTRIBUTED_LOCK_INDEXES = ImmutableList.of();
    private final String MYSQL_DISTRIBUTED_LOCK_SQL = "CREATE TABLE distributed_locks (\n" +
            "  category       VARCHAR(64)   NOT NULL,\n" +
            "  lock_key       VARCHAR(255)      NOT NULL,\n" +
            "  owner          VARCHAR(64)   NOT NULL,\n" +
            "  owner_thread   BIGINT     NOT NULL,\n" +
            "  owner_thread_name     VARCHAR(64)   NOT NULL,\n" +
            "  acquire_time   BIGINT     NOT NULL,\n" +
            "  CONSTRAINT locks_pk PRIMARY KEY (category,lock_key)\n" +
            ")";

    private final List<String> PSQL_DISTRIBUTED_LOCK_INDEXES = ImmutableList
            .of("CREATE INDEX distributed_locks_owner ON distributed_locks (owner)");
    private final String PSQL_DISTRIBUTED_LOCK_SQL = "CREATE UNLOGGED TABLE distributed_locks (\n" +
            "  category       VARCHAR(64)   NOT NULL,\n" +
            "  lock_key       VARCHAR(255)      NOT NULL,\n" +
            "  owner          VARCHAR(64)   NOT NULL,\n" +
            "  owner_thread   BIGINT     NOT NULL,\n" +
            "  owner_thread_name     VARCHAR(64)   NOT NULL,\n" +
            "  acquire_time   BIGINT     NOT NULL,\n" +
            "  CONSTRAINT locks_pk PRIMARY KEY (category,lock_key)\n" +
            ")";

    @BeforeMethod
    private void beforeMethod() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);
        dbService = spy(new DbServiceImpl());
        doReturn(true).when(dbService).isNewInstallation();
        doNothing().when(dbService).broadcastNewDbInstallation();
        doNothing().when(dbService).updateDbPropertiesTable();

        ArtifactoryHomeStub artifactoryHomeStub = new ArtifactoryHomeStub("testhome");
        artifactoryHomeStub.getSkipVerifyPrivilegesMarkerFile().createNewFile();
        ArtifactoryHome.bind(artifactoryHomeStub);

        doAnswer(invocation -> dataSource).when(jdbcHelper).getDataSource();

        dbService.waitBetweenInitialConnectionRetry = 1;
        ReflectionTestUtils.setField(dbService, "jdbcHelper", jdbcHelper);
        ReflectionTestUtils.setField(dbService, "dbProperties", dbProperties);
        ReflectionTestUtils.setField(dbService, "dbPropertiesService", dbPropertiesService);
        ReflectionTestUtils.setField(dbService, "idGenerator", idGenerator);
        ReflectionTestUtils.setField(dbService, "allDataSources", allDataSources);

        allDataSources.clear();

        ResultSet rsMock = mock(ResultSet.class);
        try {
            when(rsMock.next()).thenReturn(true);
            when(rsMock.getBoolean(1)).thenReturn(false);
            when(rsMock.getString(1)).thenReturn("Sensitive");
            when(jdbcHelper.executeSelect(anyString())).thenReturn(rsMock);
        } catch (SQLException e) {
            fail();
        }
    }

    @AfterMethod
    public void tearDown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    public void testRetryConnection() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("Testing retry connection"));
        when(jdbcHelper.getDataSource()).thenReturn(dataSource);

        DbInitializationManager dbInitializingInstallationManager = spy(new DbInitializationManager(
                dbProperties, jdbcHelper, lockDatasource));
        expectThrows(IllegalStateException.class, dbInitializingInstallationManager::init);

        verify(dataSource, times(3)).getConnection();
    }

    @Test
    public void testInitFailsWhenDBTypeIsNotPostgresAndFlagIsFalse() throws Exception {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(dbProperties.allowNonPgFlag()).thenReturn(Optional.empty());
        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        mockMetadata(dbMetadata, connection, false);

        DbInitializationManager dbInitializingInstallationManager = spy(new DbInitializationManager(
                dbProperties, jdbcHelper, lockDatasource));
        expectThrows(IllegalStateException.class, dbInitializingInstallationManager::init);
    }

    @Test
    public void testInitSucceedsWhenDBTypeIsNotPostgresAndFlagIsTrue() throws Exception {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        mockMetadata(dbMetadata, connection, false);
        when(dbProperties.allowNonPgFlag()).thenReturn(Optional.of(false));

        DbInitializationManager dbInitializingInstallationManager = spy(new DbInitializationManager(
                dbProperties, jdbcHelper, lockDatasource));
        try {
            dbInitializingInstallationManager.init();
        } catch (Exception e) {
            assertEquals(e.getCause().getMessage(),
                    "Cannot start the application with a database other than PostgreSQL. For more information, see " +
                            "JFrog documentation");
            assertEquals(e.getMessage(), "DB Schema initialization manager failed to init.");
        }
    }

    @Test(dataProvider = "provideDbTypeWithSql")
    public void testDifferentDbForLockingWhenTableNotExists(DbType dbType, String expectedCreateTableSql,
            List<String> expectedIndexes) throws Exception {
        testDifferentDbForLock(false, dbType);
        Statement stmtMock = mock(Statement.class);
        when(lockDbConnection.createStatement()).thenReturn(stmtMock);
        DbInitializationManager dbInitializingInstallationManager = spy(new DbInitializationManager(
                dbProperties, jdbcHelper, lockDatasource));
        dbInitializingInstallationManager.init();

        dbService.initDatabaseService();
        verify(lockConnectionMetadata, times(1)).getTables(any(), any(), matches("distributed_locks"), any());
        verify(lockDatasource, times(1)).getConnection();
        verify(lockDbConnection, times(2)).createStatement();
        verify(stmtMock, times(1)).executeUpdate(expectedCreateTableSql);
        expectedIndexes.forEach(index -> {
            try {
                verify(stmtMock, times(1)).executeUpdate(index);
            } catch (SQLException e) {
                fail("Failed validating index creation.", e);
            }
        });
    }

    /**
     * Verify that dbService is healthy
     * and also verify the cache allows only a single health check per data source based on
     * {@link org.artifactory.common.ConstantValues#dbDatasourceHealthCheckCacheInvalidationIntervalMillis
     */
    @Test
    public void testHealthy() {
        for (int i = 0; i < 10; i++) {
            dbService.health();
        }
        allDataSources.forEach(ds -> verify(ds, times(1)).health());
    }

    /**
     * Verify the cache allows more than a single health check per data source after
     * {@link org.artifactory.common.ConstantValues#dbDatasourceHealthCheckCacheInvalidationIntervalMillis expires
     */
    @Test
    public void testHealthCheckMoreThanOneCheckAfterCacheInterval() {
        try {
            getBound().setProperty(ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis, "0");
            for (int i = 0; i < 5; i++) {
                dbService.health();
            }
            allDataSources.forEach(ds -> verify(ds, times(5)).health());
        } finally {
            getBound().setProperty(ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis,
                    ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis.getDefValue());
        }
    }

    /**
     * Verify the health check locking mechanism - Only a single thread out of a burst is allowed to acquire
     * the lock and send the health checks.
     */
    @Test
    public void testHealthCacheLockMechanism() throws InterruptedException {
        Runnable health = () -> dbService.health();
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            executorService.submit(health);
        }
        executorService.shutdown();
        boolean termination = executorService.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(termination);
        allDataSources.forEach(ds -> verify(ds, times(1)).health());
    }

    /**
     * Verify that DB service is unhealthy, should throw DataSourceHealthException
     */
    @Test(expectedExceptions = DataSourceHealthException.class)
    public void testUnHealthyException() {
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        doThrow(DataSourceHealthException.class).when(dataSource).health();
        allDataSources.add(dataSource);
        dbService.health();
    }

    @Test(expectedExceptions = DataSourceHealthException.class, expectedExceptionsMessageRegExp = "Datasource is not " +
            "healthy: Interrupted by test")
    public void testThreadInterruptedThrowException() {
        JFrogDataSource dataSource = mock(JFrogDataSource.class);
        doAnswer((Answer<Void>) invocationOnMock -> {
            throw new InterruptedException("Interrupted by test");
        }).when(dataSource).health();
        allDataSources.add(dataSource);
        dbService.health();
    }

    /**
     * When a thread is stuck during health check, other threads from subsequent checks should fail
     */
    @Test
    public void testHealthCheckThreadStuckSubsequentThreadsShouldFail() throws InterruptedException {
        getBound().setProperty(ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis, "0");
        getBound().setProperty(ConstantValues.dbDataSourceHealthCheckLockRetrievalTimeoutMillis, "5");

        CountDownLatch lockOwnerWaitLatch = new CountDownLatch(1);
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        AtomicBoolean lockOwnerReleased = new AtomicBoolean();

        // Thread calls DbService#health, acquires lock, calls datasource#health, sends signal via lockAcquiredLatch,
        // and waits for signal to finish
        Thread lockOwnerThread = new Thread(() -> dbService.health());
        JFrogDataSource stuckDataSource = mock(JFrogDataSource.class);
        doAnswer((Answer<Void>) invocationOnMock -> {
            lockAcquiredLatch.countDown();
            long threadId = Thread.currentThread().getId();
            log.info("Thread: {} caught the lock. Waiting...", threadId);
            boolean releasedBeforeTimeout = lockOwnerWaitLatch.await(10, TimeUnit.SECONDS);
            log.info("Thread: {} finished waiting. releaseBeforeTimeout: {}", threadId, releasedBeforeTimeout);
            lockOwnerReleased.set(releasedBeforeTimeout);
            return null;
        }).when(stuckDataSource).health();
        allDataSources.add(stuckDataSource);

        try {
            lockOwnerThread.start();
            assertTrue(lockAcquiredLatch.await(2, TimeUnit.SECONDS));

            // While lock owner thread holds the lock, a call to DbService#health fails to acquire lock, and throws
            // exception
            assertThrows("Datasource is not healthy. Failed to acquire lock for 0 seconds",
                    DataSourceHealthException.class, () -> dbService.health());
            lockOwnerWaitLatch.countDown();
            log.info("Released lock owner thread");
            Awaitility.await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(lockOwnerReleased.get(),
                    "Expected lock owner to wait until told to release but it timed out"));
        } finally {
            getBound().setProperty(ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis,
                    ConstantValues.dbDatasourceHealthCheckCacheInvalidationIntervalMillis.getDefValue());
            getBound().setProperty(ConstantValues.dbDataSourceHealthCheckLockRetrievalTimeoutMillis,
                    ConstantValues.dbDataSourceHealthCheckLockRetrievalTimeoutMillis.getDefValue());
        }
    }

    private void testDifferentDbForLock(boolean mockTableExists, DbType dbType) throws Exception {
        when(jdbcHelper.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(dbProperties.getDbType()).thenReturn(dbType);
        mockMetadata(dbMetadata, connection, true);
        when(dbPropertiesService.getDbVersionInfo()).thenReturn(
                new DbVersionInfo(111, "Z", 222, 333, "Q"));

        // prepare external db lock props
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(dbType));
        when(lockDatasource.getConnection()).thenReturn(lockDbConnection);
        mockMetadata(lockConnectionMetadata, lockDbConnection, mockTableExists);
    }

    private void mockMetadata(DatabaseMetaData dbMetadata, Connection connection, boolean mockTableExists)
            throws SQLException {
        when(connection.getMetaData()).thenReturn(dbMetadata);
        when(dbMetadata.getDatabaseProductName()).thenReturn("test");
        when(dbMetadata.getDatabaseProductVersion()).thenReturn("test");
        when(dbMetadata.getDriverName()).thenReturn("test");
        when(dbMetadata.getDriverVersion()).thenReturn("test");
        when(dbMetadata.getURL()).thenReturn("url");
        when(dbMetadata.getUserName()).thenReturn("username");
        when(dbMetadata.getConnection()).thenReturn(connection);
        ResultSet resultSet = mock(ResultSet.class);
        when(dbMetadata.getTables(any(), any(), any(), any())).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(mockTableExists);
        if (mockTableExists) {
            when(resultSet.getString(any())).thenReturn("some table");
        }
    }

    @DataProvider
    private Object[][] provideDbTypeWithSql() {
        return new Object[][] {
                {DbType.ORACLE, ORACLE_DISTRIBUTED_LOCK_SQL, ORACLE_DISTRIBUTED_LOCK_INDEXES},
                {DbType.POSTGRESQL, PSQL_DISTRIBUTED_LOCK_SQL, PSQL_DISTRIBUTED_LOCK_INDEXES},
                {DbType.MSSQL, MSSQL_DISTRIBUTED_LOCK_SQL, MSSQL_DISTRIBUTED_LOCK_INDEXES},
                {DbType.MYSQL, MYSQL_DISTRIBUTED_LOCK_SQL, MYSQL_DISTRIBUTED_LOCK_INDEXES}
        };
    }
}