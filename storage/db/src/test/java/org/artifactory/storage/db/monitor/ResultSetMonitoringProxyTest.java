package org.artifactory.storage.db.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.common.ConstantValues;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultSetMonitoringProxyTest extends ArtifactoryHomeBoundJUnitTest {
    @AfterEach
    void resetSystemProperties() {
        homeStub.initArtifactorySystemProperties();
    }

    @Mock
    ResultSetSizeMonitor monitor;
    @Mock
    ResultSet resultSet;
    @Mock
    DataSource dataSource;
    @Mock
    Connection connection;
    @Mock
    Statement statement;

    @Test
    void next_shouldIncrementRowsCount() throws Exception {
        // Arrange
        DbQueryContext context = new DbQueryContext("SELECT * FROM test", false, "testHint");
        when(resultSet.next()).thenReturn(true).thenReturn(false); // Simulate one row returned

        // Act (and assert)
        ResultSetMonitoringProxy proxy = new ResultSetMonitoringProxy(
                monitor, context, resultSet, dataSource, connection, statement, false
        );

        assertEquals(0, proxy.getRowsCount());
        boolean actual = proxy.next(); // This one supposed to return true, we have one row
        assertTrue(actual);
        assertEquals(1, proxy.getRowsCount());

        actual = proxy.next(); // This one supposed to return false, no more rows
        assertFalse(actual);
        assertEquals(1, proxy.getRowsCount()); // Still one row, as we didn't call close yet
    }

    @Test
    void close_shouldTriggerTheMonitor() throws Exception {
        DbQueryContext context = new DbQueryContext("SELECT * FROM test", false, "testHint");
        when(resultSet.next()).thenReturn(true).thenReturn(false); // Simulate one row returned
        when(monitor.getNanoTime()).thenReturn(0L).thenReturn(1279L); // Simulate fetch duration
        when(monitor.shouldMonitor("SELECT * FROM test", false, "testHint")).thenReturn(true);

        try (ResultSetMonitoringProxy proxy = new ResultSetMonitoringProxy(
                monitor, context, resultSet, dataSource, connection, statement, false
        )) {
            boolean actual = proxy.next();
            assertTrue(actual);
            actual = proxy.next();
            assertFalse(actual);
        }

        // Verify that the monitor was called with the correct parameters
        verify(monitor).onQueryFetchCompleted(
                context, 1, 1279L
        );
    }

    @Test
    void close_shouldNotTriggerTheMonitorIfAlreadyClosed() throws Exception {
        DbQueryContext context = new DbQueryContext("SELECT * FROM test", false, "testHint");
        when(monitor.getNanoTime()).thenReturn(0L).thenReturn(1L); // Simulate fetch duration
        when(monitor.shouldMonitor("SELECT * FROM test", false, "testHint")).thenReturn(true);

        ResultSetMonitoringProxy proxy = new ResultSetMonitoringProxy(
                monitor, context, resultSet, dataSource, connection, statement, false
        );

        proxy.close(); // Close the first time
        when(resultSet.isClosed()).thenReturn(true); // Simulate that the result set is closed

        // Now close again, should not trigger the monitor
        proxy.close();

        // Verify that the monitor was called only once
        verify(monitor).onQueryFetchCompleted(context, 0, 1L);
    }

    @ParameterizedTest
    @CsvSource({
        // Threshold higher than rows fetched — no warning expected
        "200, 101, 0",
        // Threshold lower than rows fetched — warning expected
        "50, 51, 1"
    })
    void next_shouldWarnOnlyWhenRowsExceedConfiguredThreshold(long threshold, long rowsToFetch, int expectedWarnings)
            throws SQLException {
        setStringSystemProperty(ConstantValues.queryMonitorExceedingRowsThreshold,String.valueOf(threshold));
        DbQueryContext context = new DbQueryContext(
                "SELECT * FROM nodes", false, "test-hint", false,
                "searchAql", "REST", "search", "maven", "admin");
        when(resultSet.next()).thenReturn(true);
        ResultSetMonitoringProxy proxy = new ResultSetMonitoringProxy(
                null, context, resultSet, null, null, null, false);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        Logger logger = (Logger) LoggerFactory.getLogger(
                ResultSetMonitoringProxy.class);
        Level originalLevel = logger.getLevel();
        try {
            appender.start();
            logger.addAppender(appender);
            logger.setLevel(Level.WARN);

            for (long i = 0; i < rowsToFetch; i++) {
                proxy.next();
            }

            assertEquals(expectedWarnings, appender.list.size());
        } finally {
            logger.setLevel(originalLevel);
            logger.detachAppender(appender);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void next_shouldPersistViolationOnlyWhenPersistEnabled(boolean persistEnabled)
            throws SQLException {
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsThreshold, "5");
        setStringSystemProperty(
                ConstantValues.queryMonitorExceedingRowsViolationEnablePersist,
                String.valueOf(persistEnabled));
        DbQueryContext context = new DbQueryContext(
                "SELECT * FROM nodes", false, "hint", false,
                "searchAql", "REST", "search", "maven", "admin");
        when(resultSet.next()).thenReturn(true);
        ExceedingRowsViolationService violationService =
                mock(ExceedingRowsViolationService.class);
        ResultSetMonitoringProxy proxy = new ResultSetMonitoringProxy(
                null, context, resultSet, null, null, null, false,
                violationService);
        long rowsCount = 6L;

        for (int i = 0; i < rowsCount; i++) {
            proxy.next();
        }

        if (persistEnabled) {
            verify(violationService).persistViolationAsync(context, rowsCount);
        } else {
            verify(violationService, never())
                    .persistViolationAsync(context, rowsCount);
        }
    }
}