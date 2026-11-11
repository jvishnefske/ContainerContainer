package org.artifactory.storage.db.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.activity.CurrentActivity;
import org.artifactory.common.ConstantValues;
import org.artifactory.storage.db.monitor.LargeResultSetsMonitor;
import org.artifactory.storage.db.monitor.ResultSetMonitoringProxy;
import org.artifactory.storage.db.monitor.ResultSetSizeMonitor;
import org.artifactory.storage.db.util.limit.LimitedResultSet;
import org.artifactory.test.ArtifactoryHomeStub;
import org.jfrog.storage.DbType;
import org.jfrog.storage.JFrogDataSource;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.QueryControls;
import org.jfrog.storage.wrapper.BlobWrapper;
import org.jfrog.storage.wrapper.JFrogDataSourceWrapper;
import org.jfrog.storage.wrapper.ResultSetProxy;
import org.jfrog.storage.wrapper.ResultSetWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtifactoryJdbcHelperTest {
    @Mock
    JFrogDataSource dataSource;
    @Mock
    Connection connection;
    @Mock
    PreparedStatement statement;
    @Mock
    ResultSet resultSet;

    ArtifactoryJdbcHelper jdbcHelper;
    
    private ArtifactoryHomeStub homeStub;

    private String dummyQuery = "SELECT * FROM test_table";

    @BeforeEach
    void setUp() throws SQLException {
        // Set up Artifactory context
        homeStub = new ArtifactoryHomeStub(getClass().getName());
        homeStub.bind();
        homeStub.setProperty(ConstantValues.limitAllQueriesFetchSize, "1000");
        
        // Mock JFrogDataSourceWrapper for ResultSetProxy using lenient stubbing
        JFrogDataSourceWrapper dataSourceWrapper = mock(JFrogDataSourceWrapper.class);
        lenient().when(dataSourceWrapper.getDbType()).thenReturn(DbType.DERBY);
        lenient().when(dataSource.unwrap(JFrogDataSourceWrapper.class)).thenReturn(dataSourceWrapper);
        
        this.jdbcHelper = new ArtifactoryJdbcHelper(this.dataSource);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up Artifactory context
        if (homeStub != null) {
            ArtifactoryHomeStub.unbind();
        }
    }

    @Test
    void wrapResultSet_shouldReturnMonitoredResultProxy() {
        // Arrange
        ResultSetSizeMonitor monitor = new LargeResultSetsMonitor(mock());
        jdbcHelper.setResultSizeMonitor(monitor);

        // Act
        ResultSet actual = jdbcHelper.wrapResultSet(connection, statement, resultSet, false,
                "test", false, "SELECT * FROM test");

        // Assert
        assertInstanceOf(ResultSetMonitoringProxy.class, actual);
    }

    @Test
    void wrapResultSet_shouldReturnResultSetProxy() {
        // Act
        ResultSet actual = jdbcHelper.wrapResultSet(connection, statement, resultSet, false,
                "test", false, "SELECT * FROM test");

        // Assert
        if (actual instanceof ResultSetProxy proxy) {
            assertEquals("SELECT * FROM test", proxy.getQuery());
            assertFalse(proxy.isStreaming());
            assertEquals("test", proxy.getHint());
        } else {
            // If not wrapped, it should be the original result set
            fail("Expected ResultSetProxy but got: " + actual.getClass().getName());
        }
    }

    @Test
    void wrapResultSet_shouldUseLegacyProxy_whenConfiguredSo() {
        // Arrange
        jdbcHelper.setUseLegacyResultSetWrapper(true);

        // Act
        ResultSet actual = jdbcHelper.wrapResultSet(connection, statement, resultSet, false,
                "test", false, "SELECT * FROM test");

        // Assert
        InvocationHandler handler = Proxy.getInvocationHandler(actual);
        assertInstanceOf(ResultSetWrapper.class, handler);
    }

    @Test
    void executeSelectShouldReturnOriginalResultSetWhenNoLimitConfigured() throws Exception {
        Object[] params = {"param1", "param2"};
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(0);
            
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();

            ResultSet actual = jdbcHelper.executeSelect(dummyQuery, params);

            assertInstanceOf(ResultSetProxy.class, actual);
            assertFalse(actual instanceof LimitedResultSet);
        }
    }

    @Test
    void executeSelectShouldReturnLimitedResultSetWhenLimitConfigured() throws Exception {
        Object[] params = {"param1", "param2"};
        int configuredLimit = 1000;
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(configuredLimit);
            utilityMock.when(UniversalLimitHandlerUtility::isToBlock).thenReturn(false);
            
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();

            ResultSet actual = jdbcHelper.executeSelect(dummyQuery, params);

            assertInstanceOf(LimitedResultSet.class, actual);
            LimitedResultSet limitedResultSet = (LimitedResultSet) actual;
            assertEquals(configuredLimit, limitedResultSet.getLimit());
            assertFalse(limitedResultSet.isToBlock());
        }
    }

    @Test
    void executeSelectShouldReturnLimitedResultSetWithBlockingWhenBlockingEnabled() throws Exception {
        Object[] params = {"param1", "param2"};
        int configuredLimit = 500;
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(configuredLimit);
            utilityMock.when(UniversalLimitHandlerUtility::isToBlock).thenReturn(true);
            
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();

            ResultSet actual = jdbcHelper.executeSelect(dummyQuery, params);

            assertInstanceOf(LimitedResultSet.class, actual);
            LimitedResultSet limitedResultSet = (LimitedResultSet) actual;
            assertEquals(configuredLimit, limitedResultSet.getLimit());
            assertTrue(limitedResultSet.isToBlock());
        }
    }

    @Test
    void executeSelectShouldReturnLimitedResultSetWithoutBlockingWhenBlockingDisabled() throws Exception {
        // Arrange
        Object[] params = {"param1", "param2"};
        int configuredLimit = 2000;
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(configuredLimit);
            utilityMock.when(UniversalLimitHandlerUtility::isToBlock).thenReturn(false);
            
            // Mock the parent class executeSelect method
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();

            // Act
            ResultSet actual = jdbcHelper.executeSelect(dummyQuery, params);

            // Assert
            assertInstanceOf(LimitedResultSet.class, actual);
            LimitedResultSet limitedResultSet = (LimitedResultSet) actual;
            assertEquals(configuredLimit, limitedResultSet.getLimit());
            assertFalse(limitedResultSet.isToBlock());
        }
    }

    @Test
    void executeSelectShouldPropagateSQLExceptionWhenParentMethodThrows() throws Exception {
        // Arrange
        String query = "SELECT * FROM test_table";
        Object[] params = {"param1", "param2"};
        SQLException expectedException = new SQLException("Database connection failed");
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(0);
            
            // Mock the parent class executeSelect method to throw exception
            when(dataSource.getConnection()).thenThrow(expectedException);

            // Act & Assert
            SQLException actualException = assertThrows(SQLException.class, 
                    () -> jdbcHelper.executeSelect(query, params));
            assertSame(expectedException, actualException);
        }
    }

    @Test
    void executeSelectShouldPropagateSQLExceptionWhenParentMethodThrowsWithLimit() throws Exception {
        // Arrange
        Object[] params = {"param1", "param2"};
        SQLException expectedException = new SQLException("Database connection failed");
        int configuredLimit = 1000;
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(configuredLimit);
            utilityMock.when(UniversalLimitHandlerUtility::isToBlock).thenReturn(false);
            
            // Mock the parent class executeSelect method to throw exception
            when(dataSource.getConnection()).thenThrow(expectedException);

            // Act & Assert
            SQLException actualException = assertThrows(SQLException.class, 
                    () -> jdbcHelper.executeSelect(dummyQuery, params));
            assertSame(expectedException, actualException);
        }
    }

    @Test
    void executeSelectStreamedShouldReturnOriginalResultSetWhenNoLimitConfigured() throws Exception {
        // Arrange
        Object[] params = {"param1", "param2"};
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(0);
            
            // Mock the parent class executeSelectStreamed method
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();

            // Act
            ResultSet actual = jdbcHelper.executeSelectStreamed(QueryControls.builder().build(), dummyQuery, params);

            // Assert
            // When no limit is configured, the parent class still wraps the ResultSet in a ResultSetProxy
            assertInstanceOf(ResultSetProxy.class, actual);
            // Verify it's not a LimitedResultSet
            assertFalse(actual instanceof LimitedResultSet);
        }
    }

    @Test
    void executeSelectStreamedShouldNotReturnLimitedResultSetWhenLimitConfigured() throws Exception {
        // Arrange
        Object[] params = {"param1", "param2"};
        int configuredLimit = 1000;
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(configuredLimit);
            utilityMock.when(UniversalLimitHandlerUtility::isToBlock).thenReturn(false);
            
            // Mock the parent class executeSelectStreamed method
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();

            // Act
            ResultSet actual = jdbcHelper.executeSelectStreamed(QueryControls.builder().build(), dummyQuery, params);

            // Assert
            assertInstanceOf(ResultSetProxy.class, actual);
        }
    }

    @Test
    void shouldMonitorSizeShouldReturnFalseWhenResultSetSizeMonitorIsNull() {
        // Act
        boolean result = jdbcHelper.shouldMonitorSize("SELECT * FROM test", true, "hint");

        // Assert
        assertFalse(result);
    }

    @Test
    void shouldMonitorSizeShouldReturnTrueWhenResultSetSizeMonitorIsSet() {
        // Arrange
        ResultSetSizeMonitor monitor = mock(ResultSetSizeMonitor.class);
        when(monitor.shouldMonitor("SELECT * FROM test", true, "hint")).thenReturn(true);
        jdbcHelper.setResultSizeMonitor(monitor);

        // Act
        boolean result = jdbcHelper.shouldMonitorSize("SELECT * FROM test", true, "hint");

        // Assert
        assertTrue(result);
        verify(monitor).shouldMonitor("SELECT * FROM test", true, "hint");
    }

    @Test
    void executeSelectShouldCallExecuteSelectStreamedWhenExecuteQueryAlwaysWithStreamIsTrue() throws Exception {
        Object[] params = {"param1", "param2"};
        QueryControls controls = QueryControls.builder().build();
        
        try (var utilityMock = mockStatic(UniversalLimitHandlerUtility.class)) {
            utilityMock.when(UniversalLimitHandlerUtility::getConfiguredLimit).thenReturn(0);
            homeStub.setProperty(ConstantValues.executeQueryAlwaysWithStream, "TRUE");
            homeStub.setProperty(ConstantValues.limitAllQueriesFetchSize, "1000");
            
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(dummyQuery)).thenReturn(statement);
            doReturn(resultSet).when(statement).executeQuery();
            
            ArtifactoryJdbcHelper spyHelper = org.mockito.Mockito.spy(jdbcHelper);
            spyHelper.executeSelect(controls, dummyQuery, params);

            verify(spyHelper, times(1)).executeSelectStreamed(any(QueryControls.class), eq(dummyQuery), eq(params));
        }
    }

    @Test
    void formatCurrentActivity_shouldReturnEmpty_whenNoActivitySet() {
        MDC.clear();
        assertEquals("", ArtifactoryJdbcHelper.formatCurrentActivity());
    }

    @Test
    void formatCurrentActivity_shouldReturnAllFields_whenAllSet() {
        MDC.clear();
        try {
            CurrentActivity.KIND.put("job");
            CurrentActivity.GROUP.put("indexing");
            CurrentActivity.NAME.put("rpm-indexer");
            CurrentActivity.PACKAGE.put("rpm");
            CurrentActivity.ACTOR.put("system");

            assertEquals("kind=job, group=indexing, name=rpm-indexer, pkg=rpm, actor=system",
                    ArtifactoryJdbcHelper.formatCurrentActivity());
        } finally {
            MDC.clear();
        }
    }

    @Test
    void formatCurrentActivity_shouldSkipNullFields() {
        MDC.clear();
        try {
            CurrentActivity.KIND.put("http");
            CurrentActivity.ACTOR.put("admin");

            assertEquals("kind=http, actor=admin", ArtifactoryJdbcHelper.formatCurrentActivity());
        } finally {
            MDC.clear();
        }
    }

    @Test
    void formatCurrentActivity_shouldSkipEmptyFields() {
        MDC.clear();
        try {
            CurrentActivity.KIND.put("async");
            CurrentActivity.GROUP.put("");
            CurrentActivity.NAME.put("cleanup-task");

            assertEquals("kind=async, name=cleanup-task", ArtifactoryJdbcHelper.formatCurrentActivity());
        } finally {
            MDC.clear();
        }
    }

    @Test
    void logSlowQuery_shouldLogActivityContext_whenActivityPresent() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        MDC.clear();
        try {
            CurrentActivity.KIND.put("job");
            CurrentActivity.GROUP.put("indexing");
            CurrentActivity.ACTOR.put("system");

            jdbcHelper.logSlowQuery(dummyQuery, null, 10, TimeUnit.SECONDS.toNanos(15), 0);

            List<ILoggingEvent> events = appender.list.stream()
                    .filter(e -> e.getFormattedMessage()
                            .startsWith("Slow DB query activity:"))
                    .toList();
            assertEquals(1, events.size());
            assertEquals(
                    "Slow DB query activity: "
                            + "kind=job, group=indexing, actor=system",
                    events.getFirst().getFormattedMessage());
        } finally {
            MDC.clear();
            detachAppender(appender);
        }
    }

    @Test
    void logSlowQuery_shouldNotLogActivityContext_whenNoActivity() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        MDC.clear();
        try {
            jdbcHelper.logSlowQuery(dummyQuery, null, 5, TimeUnit.SECONDS.toNanos(15), 0);

            List<ILoggingEvent> events = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("activity"))
                    .toList();
            assertTrue(events.isEmpty(), "No activity context log expected when MDC is empty");
        } finally {
            detachAppender(appender);
        }
    }

    @Test
    void onSqlBulkFinish_shouldLogActivityContext_forSlowQueries() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        MDC.clear();
        try {
            CurrentActivity.KIND.put("work");
            CurrentActivity.PACKAGE.put("docker");

            long slowDuration = TimeUnit.SECONDS.toNanos(20);
            jdbcHelper.onSqlBulkFinish(dummyQuery, 100, slowDuration, 0);

            List<ILoggingEvent> events = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().startsWith("Slow DB query activity:"))
                    .toList();
            assertEquals(1, events.size());
            assertEquals("Slow DB query activity: kind=work, pkg=docker",
                    events.getFirst().getFormattedMessage());
        } finally {
            MDC.clear();
            detachAppender(appender);
        }
    }

    @Test
    void onSqlBulkFinish_shouldNotLogActivityContext_forFastQueries() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        MDC.clear();
        try {
            CurrentActivity.KIND.put("http");
            CurrentActivity.ACTOR.put("admin");

            long fastDuration = TimeUnit.MILLISECONDS.toNanos(50);
            jdbcHelper.onSqlBulkFinish(dummyQuery, 10, fastDuration, 0);

            List<ILoggingEvent> events = appender.list.stream()
                    .filter(e -> e.getFormattedMessage().contains("activity"))
                    .toList();
            assertTrue(events.isEmpty(), "No activity context log expected for fast queries");
        } finally {
            MDC.clear();
            detachAppender(appender);
        }
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(ArtifactoryJdbcHelper.class);
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(
            ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(ArtifactoryJdbcHelper.class);
        logger.detachAppender(appender);
    }

    // --- Tests for slow-query parameter printing feature ---

    @Test
    void enablePrintingSlowQueriesParamIfNeeded_enablesWhenPropertyTrue() throws Exception {
        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();

        assertTrue(getPrintSlowQueryParameters(jdbcHelper));
        assertNotNull(getSensitiveTablePattern(jdbcHelper));
    }

    @Test
    void enablePrintingSlowQueriesParamIfNeeded_doesNotEnableWhenPropertyFalse()
            throws Exception {
        homeStub.setProperty(ConstantValues.printSlowQueriesParameters, "false");

        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();

        assertFalse(getPrintSlowQueryParameters(jdbcHelper));
        assertNull(getSensitiveTablePattern(jdbcHelper));
    }

    @Test
    void enablePrintingSlowQueriesParamIfNeeded_sensitivePatternMatchesDefaultTables()
            throws Exception {
        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();

        Pattern pattern = getSensitiveTablePattern(jdbcHelper);
        assertNotNull(pattern);
        assertTrue(pattern.matcher("trusted_keys").find());
        assertTrue(pattern.matcher("master_key_status").find());
        assertFalse(pattern.matcher("users").find());
        assertFalse(pattern.matcher("nodes").find());
    }

    @Test
    void enablePrintingSlowQueriesParamIfNeeded_sensitivePatternIncludesExtraTables()
            throws Exception {
        homeStub.setProperty(
                ConstantValues.printSlowQueriesParametersSensitiveTablesExtra, "api_keys,tokens");

        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();

        Pattern pattern = getSensitiveTablePattern(jdbcHelper);
        assertNotNull(pattern);
        assertTrue(pattern.matcher("trusted_keys").find());
        assertTrue(pattern.matcher("master_key_status").find());
        assertTrue(pattern.matcher("api_keys").find());
        assertTrue(pattern.matcher("tokens").find());
    }

    @Test
    void buildSensitiveTables_noExtras_returnsDefaults() throws Exception {
        List<String> result = invokeBuildSensitiveTables();

        assertEquals(
                List.of("trusted_keys", "master_key_status"),
                result);
    }

    @Test
    void buildSensitiveTables_withExtras_returnsMerged() throws Exception {
        homeStub.setProperty(
                ConstantValues.printSlowQueriesParametersSensitiveTablesExtra,
                "custom_secrets,api_keys");

        List<String> result = invokeBuildSensitiveTables();

        assertEquals(4, result.size());
        assertEquals(
                List.of("trusted_keys", "master_key_status",
                        "custom_secrets", "api_keys"),
                result);
    }

    @Test
    void buildSensitiveTables_withOverlappingExtras_deduplicates() throws Exception {
        homeStub.setProperty(
                ConstantValues.printSlowQueriesParametersSensitiveTablesExtra,
                "trusted_keys,custom_table");

        List<String> result = invokeBuildSensitiveTables();

        assertEquals(3, result.size());
        assertEquals(
                List.of("trusted_keys", "master_key_status",
                        "custom_table"),
                result);
    }

    @Test
    void buildSensitiveTables_defaultsAppearBeforeExtras() throws Exception {
        homeStub.setProperty(
                ConstantValues.printSlowQueriesParametersSensitiveTablesExtra,
                "alpha_table");

        List<String> result = invokeBuildSensitiveTables();

        assertEquals("trusted_keys", result.get(0));
        assertEquals("master_key_status", result.get(1));
        assertEquals("alpha_table", result.get(2));
    }

    // --- Tests for slow-query parameter printing in log output ---

    @Test
    void slowQuery_logsResolvedParameters_whenPrintingEnabled()
            throws Exception {
        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();
        setSlowQueryThreshold(jdbcHelper, 1L);

        ListAppender<ILoggingEvent> appender = newLogCapture();
        Logger logger = jdbcHelperLogger();
        Level originalLevel = logger.getLevel();
        try {
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);

            String query =
                    "SELECT * FROM nodes WHERE node_id = ? AND repo = ?";
            Object[] params = {42L, "libs-release"};

            jdbcHelper.onSqlFinish(query, params, 1,
                    100_000_000L, 5_000_000L);

            assertFalse(appender.list.isEmpty(),
                    "Expected a slow-query log event");
            String msg = appender.list.get(0).getFormattedMessage();
            assertTrue(msg.contains("Slow DB query"));
            assertTrue(msg.contains("42"),
                    "Numeric parameter should be resolved in log");
            assertTrue(msg.contains("libs-release"),
                    "String parameter should be resolved in log");
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    @Test
    void slowQuery_handlesNullAndBlobParams_withoutError()
            throws Exception {
        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();
        setSlowQueryThreshold(jdbcHelper, 1L);

        ListAppender<ILoggingEvent> appender = newLogCapture();
        Logger logger = jdbcHelperLogger();
        Level originalLevel = logger.getLevel();
        try {
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);

            String query = "INSERT INTO nodes (a, b, c) VALUES (?, ?, ?)";
            Object[] params = {null, new BlobWrapper("blob-data"), 7};

            jdbcHelper.onSqlFinish(query, params, 0,
                    200_000_000L, 1_000_000L);

            assertFalse(appender.list.isEmpty(),
                    "Expected a slow-query log event");
            String msg = appender.list.get(0).getFormattedMessage();
            assertTrue(msg.contains("NULL"),
                    "Null param should appear as NULL");
            assertTrue(msg.contains("BLOB"),
                    "BlobWrapper param should appear as BLOB(length: ...)");
            assertTrue(msg.contains("7"),
                    "Numeric param should be resolved");
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    @Test
    void slowQuery_doesNotResolveParams_forSensitiveTableQuery()
            throws Exception {
        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();
        setSlowQueryThreshold(jdbcHelper, 1L);

        ListAppender<ILoggingEvent> appender = newLogCapture();
        Logger logger = jdbcHelperLogger();
        Level originalLevel = logger.getLevel();
        try {
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);

            String query =
                    "SELECT * FROM trusted_keys WHERE key_id = ?";
            Object[] params = {"secret-key-123"};

            jdbcHelper.onSqlFinish(query, params, 1,
                    100_000_000L, 5_000_000L);

            assertFalse(appender.list.isEmpty(),
                    "Expected a slow-query log event");
            String msg = appender.list.get(0).getFormattedMessage();
            assertTrue(msg.contains("Slow DB query"));
            assertFalse(msg.contains("secret-key-123"),
                    "Sensitive-table params must not be resolved");
            assertTrue(msg.contains("?"),
                    "Query should retain unresolved placeholders");
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    @Test
    void slowQuery_logsQueryWithoutParams_whenParamsAreEmpty()
            throws Exception {
        jdbcHelper.enablePrintingSlowQueriesParamIfNeeded();
        setSlowQueryThreshold(jdbcHelper, 1L);

        ListAppender<ILoggingEvent> appender = newLogCapture();
        Logger logger = jdbcHelperLogger();
        Level originalLevel = logger.getLevel();
        try {
            logger.addAppender(appender);
            logger.setLevel(Level.INFO);

            String query = "SELECT count(*) FROM nodes";

            jdbcHelper.onSqlFinish(query, null, 1,
                    50_000_000L, 2_000_000L);

            assertFalse(appender.list.isEmpty(),
                    "Expected a slow-query log event");
            String msg = appender.list.get(0).getFormattedMessage();
            assertTrue(msg.contains("Slow DB query"));
            assertTrue(msg.contains("SELECT count(*) FROM nodes"));
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    // --- Log-capture helpers ---

    private static ListAppender<ILoggingEvent> newLogCapture() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        return appender;
    }

    private static Logger jdbcHelperLogger() {
        return (Logger) LoggerFactory.getLogger(JdbcHelper.class);
    }

    private static void setSlowQueryThreshold(
            ArtifactoryJdbcHelper helper, long nanos) throws Exception {
        Field field = JdbcHelper.class.getDeclaredField("slowQueriesThresholdNanos");
        field.setAccessible(true);
        field.setLong(helper, nanos);
    }

    // --- Reflection helpers for accessing parent JdbcHelper state ---

    private static boolean getPrintSlowQueryParameters(
            ArtifactoryJdbcHelper helper) throws Exception {
        Field field = JdbcHelper.class
                .getDeclaredField("printSlowQueryParameters");
        field.setAccessible(true);
        return field.getBoolean(helper);
    }

    private static Pattern getSensitiveTablePattern(
            ArtifactoryJdbcHelper helper) throws Exception {
        Field field = JdbcHelper.class.getDeclaredField("sensitiveTablePattern");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Pattern> ref = (AtomicReference<Pattern>) field.get(helper);
        return ref.get();
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeBuildSensitiveTables() throws Exception {
        Method method = ArtifactoryJdbcHelper.class.getDeclaredMethod(
                "buildSensitiveTablesForSlowQueryParameterLogging");
        method.setAccessible(true);
        return (List<String>) method.invoke(null);
    }
}