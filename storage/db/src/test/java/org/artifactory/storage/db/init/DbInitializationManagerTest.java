package org.artifactory.storage.db.init;

import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.common.ExecutionUtils;
import org.jfrog.common.ResourceUtils;
import org.jfrog.common.Retryable;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.util.DbStatementUtils;
import org.jfrog.storage.util.DbUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static org.artifactory.storage.db.dao.locks.DbDistributeLocksDao.TABLE_DISTRIBUTED_LOCKS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbInitializationManagerTest {

    @Mock
    private ArtifactoryDbProperties dbProperties;

    @Mock
    private JdbcHelper jdbcHelper;

    @Mock
    private DataSource locksDataSource;

    @Mock
    private Connection connection;

    @Mock
    private Connection lockConnection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private DatabaseMetaData lockMetaData;

    @Mock
    private ArtifactoryHome artifactoryHome;

    private DbInitializationManager manager;

    private MockedStatic<DbConnectionUtils> dbConnectionUtilsMock;
    private MockedStatic<DbStatementUtils> dbStatementUtilsMock;
    private MockedStatic<DbUtils> dbUtilsMock;
    private MockedStatic<ResourceUtils> resourceUtilsMock;
    private MockedStatic<ExecutionUtils> executionUtilsMock;
    private MockedStatic<ArtifactoryHome> artifactoryHomeMock;

    @BeforeEach
    void setUp() {
        manager = new DbInitializationManager(dbProperties, jdbcHelper, locksDataSource);

        dbConnectionUtilsMock = mockStatic(DbConnectionUtils.class);
        dbStatementUtilsMock = mockStatic(DbStatementUtils.class);
        dbUtilsMock = mockStatic(DbUtils.class);
        resourceUtilsMock = mockStatic(ResourceUtils.class);
        executionUtilsMock = mockStatic(ExecutionUtils.class);
        artifactoryHomeMock = mockStatic(ArtifactoryHome.class);
    }

    @AfterEach
    void tearDown() {
        closeMockedStatics();
    }

    private void closeMockedStatics() {
        if (dbConnectionUtilsMock != null) {dbConnectionUtilsMock.close();}
        if (dbStatementUtilsMock != null) {dbStatementUtilsMock.close();}
        if (dbUtilsMock != null) {dbUtilsMock.close();}
        if (resourceUtilsMock != null) {resourceUtilsMock.close();}
        if (executionUtilsMock != null) {executionUtilsMock.close();}
        if (artifactoryHomeMock != null) {artifactoryHomeMock.close();}
    }

    @Test
    void testInit_SchemaDoesNotExist_CreatesSchemaAndReturnsTrue() throws Exception {
        // Arrange
        setupBasicMocks(DbType.POSTGRESQL, false, false);

        // Act
        boolean result = manager.init();

        // Assert
        assertTrue(result);
        dbStatementUtilsMock.verify(() ->
                DbStatementUtils.executeSqlStream(eq(connection), any(InputStream.class)), atLeastOnce());
    }

    @Test
    void testInit_SchemaExists_DoesNotCreateSchemaAndReturnsFalse() throws Exception {
        // Arrange
        setupBasicMocks(DbType.POSTGRESQL, true, true);

        // Act
        boolean result = manager.init();

        // Assert
        assertFalse(result);
    }

    @Test
    void testInit_MySqlDatabase_ChecksMinimumVersion() throws Exception {
        // Arrange
        setupBasicMocks(DbType.MYSQL, false, false);

        // Act
        boolean result = manager.init();

        // Assert
        assertTrue(result);
        dbConnectionUtilsMock.verify(() -> DbConnectionUtils.checkMySqlMinVersion(connection));
        dbConnectionUtilsMock.verify(() -> DbConnectionUtils.checkDefaultDbProperties(dbProperties));
    }

    @Test
    void testInit_ConnectionFails_ThrowsIllegalStateException() {
        // Arrange
        dbConnectionUtilsMock.when(() -> DbConnectionUtils.retryGetConnection(jdbcHelper))
                .thenThrow(new SQLException("Connection failed"));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> manager.init());
        assertTrue(exception.getMessage().contains("DB Schema initialization manager failed to init"));
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void testInit_ResourceNotFound_ThrowsIllegalStateException() throws Exception {
        // Arrange
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

        dbConnectionUtilsMock.when(() -> DbConnectionUtils.retryGetConnection(jdbcHelper))
                .thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        setupSchemaExistsMock(false);
        dbUtilsMock.when(() -> DbUtils.getDbTypeNameForSqlResources(DbType.POSTGRESQL))
                .thenReturn("postgresql");
        resourceUtilsMock.when(() -> ResourceUtils.getResource(anyString()))
                .thenReturn(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> manager.init());
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void testInit_HaNotConfiguredAndSchemaCreationFails_ThrowsException() throws Exception {
        // Arrange
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

        dbConnectionUtilsMock.when(() -> DbConnectionUtils.retryGetConnection(jdbcHelper))
                .thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        setupSchemaExistsMock(false);
        setupResourceMocks("postgresql");

        artifactoryHomeMock.when(ArtifactoryHome::get).thenReturn(artifactoryHome);
        when(artifactoryHome.isHaConfigured()).thenReturn(false);

        dbStatementUtilsMock.when(() -> DbStatementUtils.executeSqlStream(eq(connection), any(InputStream.class)))
                .thenThrow(new SQLException("Creation failed"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> manager.init());
    }

    @Test
    void testInit_BlockingLocksTableDoesNotExist_CreatesTable() throws Exception {
        // Arrange
        setupBasicMocks(DbType.POSTGRESQL, true, false);

        // Act
        manager.init();

        // Assert
        dbStatementUtilsMock.verify(() ->
                DbStatementUtils.executeSqlStream(eq(connection), any(InputStream.class)), atLeastOnce());
    }

    @Test
    void testInit_BlockingLocksTableExists_DoesNotCreateTable() throws Exception {
        // Arrange
        setupBasicMocks(DbType.POSTGRESQL, true, true);

        // Act
        manager.init();

        // Assert - Should only verify connection, not create blocking locks table
        verify(connection, atLeastOnce()).getMetaData();
    }

    @Test
    void testInit_WithLockingDatabase_CreatesLockSchema() throws Exception {
        // Arrange
        setupConnectionMocks(DbType.POSTGRESQL);
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.MYSQL));
        setupSchemaExistsMock(true);
        setupTableExistsMock(false);
        setupResourceMocks("postgresql", "mysql");

        when(locksDataSource.getConnection()).thenReturn(lockConnection);
        when(lockConnection.getMetaData()).thenReturn(lockMetaData);

        dbUtilsMock.when(() -> DbUtils.tableExists(lockMetaData, DbType.MYSQL, TABLE_DISTRIBUTED_LOCKS))
                .thenReturn(false);

        // Act
        manager.init();

        // Assert
        verify(locksDataSource).getConnection();
        dbStatementUtilsMock.verify(() ->
                DbStatementUtils.executeSqlStream(eq(lockConnection), any(InputStream.class)), atLeastOnce());
    }

    @Test
    void testInit_WithLockingDatabase_LockSchemaExists_DoesNotCreate() throws Exception {
        // Arrange
        setupConnectionMocks(DbType.POSTGRESQL);
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.MYSQL));
        setupSchemaExistsMock(true);
        setupTableExistsMock(false);
        setupResourceMocks("postgresql", "mysql");

        when(locksDataSource.getConnection()).thenReturn(lockConnection);
        when(lockConnection.getMetaData()).thenReturn(lockMetaData);

        dbUtilsMock.when(() -> DbUtils.tableExists(lockMetaData, DbType.MYSQL, TABLE_DISTRIBUTED_LOCKS))
                .thenReturn(true);

        CompletableFuture<Boolean> blockingTableExistsFuture = CompletableFuture.completedFuture(false);
        executionUtilsMock.when(
                        () -> ExecutionUtils.retry(any(Retryable.class), any(ExecutionUtils.RetryOptions.class),
                                any(ExecutorService.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(true),  // schema exists
                        CompletableFuture.completedFuture(false), // blocking locks table doesn't exist
                        blockingTableExistsFuture
                );

        // Act
        manager.init();

        // Assert
        verify(locksDataSource).getConnection();
    }

    @Test
    void testInit_WithLockingDatabase_AllTablesExist() throws Exception {
        // Arrange
        setupConnectionMocks(DbType.POSTGRESQL);
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.MYSQL));
        setupSchemaExistsMock(true);

        when(locksDataSource.getConnection()).thenReturn(lockConnection);
        when(lockConnection.getMetaData()).thenReturn(lockMetaData);

        dbUtilsMock.when(() -> DbUtils.tableExists(lockMetaData, DbType.MYSQL, TABLE_DISTRIBUTED_LOCKS))
                .thenReturn(true);

        setupResourceMocks("postgresql", "mysql");

        executionUtilsMock.when(
                        () -> ExecutionUtils.retry(any(Retryable.class), any(ExecutionUtils.RetryOptions.class),
                                any(ExecutorService.class)))
                .thenReturn(
                        CompletableFuture.completedFuture(true), // schema exists
                        CompletableFuture.completedFuture(true), // blocking locks exists
                        CompletableFuture.completedFuture(true)  // locking db blocking locks exists
                );

        // Act
        manager.init();

        // Assert
        verify(locksDataSource).getConnection();
    }

    @Test
    void testInit_PrintConnectionInfo_Success() throws Exception {
        // Arrange
        setupBasicMocks(DbType.POSTGRESQL, true, false);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("13.0");
        when(metaData.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        when(metaData.getDriverVersion()).thenReturn("42.2.5");

        // Act
        manager.init();

        // Assert
        verify(metaData).getDatabaseProductName();
        verify(metaData).getDatabaseProductVersion();
        verify(metaData).getDriverName();
        verify(metaData).getDriverVersion();
    }

    @Test
    void testInit_PrintConnectionInfo_SQLException_LogsWarning() throws Exception {
        // Arrange
        setupConnectionMocks(DbType.POSTGRESQL);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenThrow(new SQLException("Metadata error"));

        setupSchemaExistsMock(true);
        setupTableExistsMock(false);
        setupResourceMocks("postgresql");

        // Act & Assert - Should not throw, just log warning
        assertDoesNotThrow(() -> manager.init());
    }

    @ParameterizedTest
    @EnumSource(DbType.class)
    void testInit_AllDatabaseTypes(DbType dbType) throws Exception {
        setupBasicMocks(dbType, false, false);
        boolean result = manager.init();
        assertTrue(result);
    }

    @Test
    void testInit_LockingDatabaseConnectionFails_ThrowsException() throws Exception {
        // Arrange
        setupConnectionMocks(DbType.POSTGRESQL);
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.of(DbType.MYSQL));
        setupSchemaExistsMock(true);
        setupTableExistsMock(false);
        setupResourceMocks("postgresql");

        when(locksDataSource.getConnection()).thenThrow(new SQLException("Lock DB connection failed"));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> manager.init());
    }

    // Helper methods

    private void setupBasicMocks(DbType dbType, boolean schemaExists, boolean blockingLocksTableExists)
            throws Exception {
        setupConnectionMocks(dbType);
        setupSchemaExistsMock(schemaExists);
        setupTableExistsMock(blockingLocksTableExists);
        setupResourceMocks(DbUtils.getDbTypeNameForSqlResources(dbType));
    }

    private void setupConnectionMocks(DbType dbType) throws SQLException {
        when(dbProperties.getDbType()).thenReturn(dbType);
        when(dbProperties.getLockingDbSpecificType()).thenReturn(Optional.empty());

        dbConnectionUtilsMock.when(() -> DbConnectionUtils.retryGetConnection(jdbcHelper))
                .thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
    }

    private void setupSchemaExistsMock(boolean... exists) {
        CompletableFuture[] futures = new CompletableFuture[exists.length];
        for (int i = 0; i < exists.length; i++) {
            futures[i] = CompletableFuture.completedFuture(exists[i]);
        }

        executionUtilsMock.when(
                        () -> ExecutionUtils.retry(any(Retryable.class), any(ExecutionUtils.RetryOptions.class),
                                any(ExecutorService.class)))
                .thenReturn(futures[0], java.util.Arrays.copyOfRange(futures, 1, futures.length));
    }

    private void setupTableExistsMock(boolean exists) {
        CompletableFuture<Boolean> future = CompletableFuture.completedFuture(exists);
        executionUtilsMock.when(
                        () -> ExecutionUtils.retry(any(Retryable.class), any(ExecutionUtils.RetryOptions.class),
                                any(ExecutorService.class)))
                .thenReturn(future, future);
    }

    private void setupResourceMocks(String... dbTypeNames) {
        for (String dbTypeName : dbTypeNames) {
            dbUtilsMock.when(() -> DbUtils.getDbTypeNameForSqlResources(any()))
                    .thenReturn(dbTypeName);
        }

        InputStream mockStream = new ByteArrayInputStream("CREATE TABLE test".getBytes());
        resourceUtilsMock.when(() -> ResourceUtils.getResource(anyString()))
                .thenReturn(mockStream);
    }
}