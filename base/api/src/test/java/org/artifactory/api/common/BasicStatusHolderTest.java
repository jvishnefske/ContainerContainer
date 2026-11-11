package org.artifactory.api.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.status.StatusEntryWithDifferentLogLevel;
import org.artifactory.common.status.StatusEntryLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class BasicStatusHolderTest {
    private MemoryAppender memoryAppender;

    private Marker marker;

    @BeforeEach
    void setUp() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(BasicStatusHolder.class, null);
        marker = Mockito.mock(Marker.class);
    }

    @AfterEach
    void tearDown() {
        stopMemoryAppender(memoryAppender);
    }

    @ParameterizedTest
    @MethodSource("provideArgumentsForPlugins")
    void errorWithDifferentLogLevel(Exception e, Level level) {
        String msg = "message";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);
        if (level == null) {
            statusHolder.error(msg, statusCode, e, logger);
            assertTrue(statusHolder.isError());
            assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());
        } else {
            statusHolder.errorWithDifferentLogLevel(msg, statusCode, e, logger, level);
            assertTrue(statusHolder.isError());
            assertEquals(1, memoryAppender.search(msg, level).size());
            statusHolder.setVerbose(true);
            statusHolder.errorWithDifferentLogLevel(msg, statusCode, e, logger, level);
            assertTrue(statusHolder.isError());
            assertEquals(2, memoryAppender.search(msg, level).size());
        }
    }

    @Test
    void errorWithDifferentLogLevelNoException() {
        String msg = "message";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);
        statusHolder.errorWithDifferentLogLevel(msg, statusCode, logger, Level.ERROR);
        assertTrue(statusHolder.isError());
        assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void errorWithDifferentLogLevelDisableLog(boolean verbose) {
        String msg = "message";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setVerbose(verbose);
        Exception e = new Exception();
        logger.setLevel(Level.OFF);
        statusHolder.errorWithDifferentLogLevel(msg, statusCode, e, logger, Level.WARN);
        assertEquals(0, memoryAppender.search(msg,  Level.WARN).size());
        statusHolder.errorWithDifferentLogLevel(msg, statusCode, e, logger, Level.ERROR);
        assertEquals(0, memoryAppender.search(msg,  Level.ERROR).size());
    }

    @Test
    void errorWithMarker() {
        String msg = "message";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);
        statusHolder.error(msg, statusCode, logger, marker);
        assertTrue(statusHolder.isError());
        assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());
    }

    @Test
    void logWithoutThrowable() {
        String msg = "message";
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);

        statusHolder.error(msg, logger);
        assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());

        statusHolder.warn(msg, logger);
        assertEquals(1, memoryAppender.search(msg, Level.WARN).size());

        statusHolder.debug(msg, logger);
        assertEquals(1, memoryAppender.search(msg, Level.DEBUG).size());

        statusHolder.status(msg, logger);
        assertEquals(1, memoryAppender.search(msg, Level.INFO).size());
    }

    @Test
    void logWithVerbose() {
        String msg = "message";
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setVerbose(true);
        logger.setLevel(Level.DEBUG);

        statusHolder.error(msg, new Exception(), logger);
        assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());
    }

    @Test
    void logWithVerboseAndMarker() {
        String msg = "message";
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setVerbose(true);
        logger.setLevel(Level.DEBUG);

        statusHolder.error(msg,400, logger, marker);
        assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());
    }

    @Test
    void logWithThrowableDifferentLogLevel() {
        String msg = "message";
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);

        int statusCode = 400;
        // Test with different log levels
        statusHolder.errorWithDifferentLogLevel(msg, statusCode, new Exception(), logger, Level.ERROR);
        assertEquals(1, memoryAppender.search(msg, Level.ERROR).size());

        statusHolder.errorWithDifferentLogLevel(msg, statusCode, new Exception(), logger, Level.WARN);
        assertEquals(1, memoryAppender.search(msg, Level.WARN).size());

        statusHolder.errorWithDifferentLogLevel(msg, statusCode, new Exception(), logger, Level.INFO);
        assertEquals(1, memoryAppender.search(msg, Level.INFO).size());

        statusHolder.errorWithDifferentLogLevel(msg, statusCode, new Exception(), logger, Level.DEBUG);
        assertEquals(1, memoryAppender.search(msg, Level.DEBUG).size());
    }

    @Test
    void errorWithStatusEntryEithDifferentLogLevel_shouldRecordErrorAndLogAtSpecifiedLevel() {
        String msg = "pom validation failed";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);
        Exception exception = new Exception("bad pom");

        statusHolder.errorWithStatusEntryWithDifferentLogLevel(msg, statusCode, exception, logger, Level.WARN);

        assertTrue(statusHolder.isError());
        assertEquals(1, memoryAppender.search(msg, Level.WARN).size());
        assertEquals(0, memoryAppender.search(msg, Level.ERROR).size());

        StatusEntry lastError = statusHolder.getLastError();
        assertTrue(lastError instanceof StatusEntryWithDifferentLogLevel);
        StatusEntryWithDifferentLogLevel typedEntry = (StatusEntryWithDifferentLogLevel) lastError;
        assertEquals(Level.WARN, typedEntry.getLogLevel());
        assertEquals(statusCode, typedEntry.getStatusCode());
        assertEquals(msg, typedEntry.getMessage());
        assertEquals(exception, typedEntry.getException());
        assertTrue(typedEntry.isError());
    }

    @Test
    void errorWithStatusEntryEithDifferentLogLevel_withDebugLevel() {
        String msg = "debug level error";
        int statusCode = 500;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        logger.setLevel(Level.DEBUG);

        statusHolder.errorWithStatusEntryWithDifferentLogLevel(msg, statusCode, new Exception(), logger, Level.DEBUG);

        assertTrue(statusHolder.isError());
        assertEquals(1, memoryAppender.search(msg, Level.DEBUG).size());
        assertEquals(0, memoryAppender.search(msg, Level.ERROR).size());
        assertTrue(statusHolder.getLastError() instanceof StatusEntryWithDifferentLogLevel);
    }

    @Test
    void errorWithStatusEntryEithDifferentLogLevel_withVerbose_shouldLogWithException() {
        String msg = "verbose error";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setVerbose(true);
        logger.setLevel(Level.DEBUG);

        statusHolder.errorWithStatusEntryWithDifferentLogLevel(msg, statusCode, new Exception("test"), logger,
                Level.WARN);

        assertTrue(statusHolder.isError());
        assertEquals(1, memoryAppender.search(msg, Level.WARN).size());
    }

    @Test
    void testFastFail() {
        String msg = "message";
        int statusCode = 400;
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        MutableStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setFastFail(true);
        Exception e = new Exception("Test Exception");

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> statusHolder.error(msg, statusCode, e, logger));

        assertEquals("Fast fail exception: " + msg, thrown.getMessage());
        assertEquals(e, thrown.getCause());
    }

    @Test
    void testGetMostImportantErrorStatus_NoError_ReturnNull() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        assertNull(statusHolder.getMostImportantErrorStatusCode());
    }

    @Test
    void testGetMostImportantErrorStatus_PreferredStatus() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.error("Internal Server Error", SC_INTERNAL_SERVER_ERROR, null, log);
        statusHolder.error("Service Unavailable", SC_SERVICE_UNAVAILABLE, null, log);

        int mostImportantError = statusHolder.getMostImportantErrorStatusCode().getStatusCode();

        assertEquals(SC_INTERNAL_SERVER_ERROR, mostImportantError);
    }

    @Test
    void testGetMostImportantErrorStatus() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.error("Bad Gateway", SC_BAD_GATEWAY, null, log);
        statusHolder.error("Service Unavailable", SC_SERVICE_UNAVAILABLE, null, log);

        int mostImportantError = statusHolder.getMostImportantErrorStatusCode().getStatusCode();

        assertEquals(SC_SERVICE_UNAVAILABLE, mostImportantError);
    }

    @Test
    void testGetMostImportantWarningStatus_NoWarning_ReturnNull() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        assertNull(statusHolder.getMostImportantWarningsStatusCode());
    }

    @ParameterizedTest
    @MethodSource("provideStatusEntriesForOverallStatus")
    void overallStatusMsgAggregatesErrorsAndWarnings(List<StatusEntry> errors, List<StatusEntry> warnings,
            String expectedMessage) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        errors.forEach(statusHolder::addStatusEntry);
        warnings.forEach(statusHolder::addStatusEntry);

        String overallStatusMsg = statusHolder.getAggregatedStatusMsg();

        assertEquals(expectedMessage, overallStatusMsg);
    }

    @ParameterizedTest
    @MethodSource("provideEmptyStatusEntries")
    void overallStatusMsgReturnsLastStatusWhenNoErrorsOrWarnings(List<StatusEntry> statuses, String expectedMessage) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statuses.forEach(statusHolder::addStatusEntry);

        String overallStatusMsg = statusHolder.getAggregatedStatusMsg();

        assertEquals(expectedMessage, overallStatusMsg);
    }

    private static Stream<Arguments> provideStatusEntriesForOverallStatus() {
        return Stream.of(
                Arguments.of(
                        List.of(new StatusEntry(500, StatusEntryLevel.ERROR, "Error 1", null),
                                new StatusEntry(400, StatusEntryLevel.ERROR, "Error 2", null)),
                        List.of(new StatusEntry(300, StatusEntryLevel.WARNING, "Warning 1", null)),
                        "Error 1, Error 2, Warning 1"
                ),
                Arguments.of(
                        List.of(),
                        List.of(new StatusEntry(300, StatusEntryLevel.WARNING, "Warning 1", null)),
                        "Warning 1"
                )
        );
    }

    private static Stream<Arguments> provideEmptyStatusEntries() {
        return Stream.of(
                Arguments.of(
                        List.of(new StatusEntry(200, StatusEntryLevel.INFO, "Last Status", null)),
                        "Last Status"
                ),
                Arguments.of(
                        List.of(),
                        ""
                )
        );
    }

    private static void stopMemoryAppender(MemoryAppender memoryAppender) {
        memoryAppender.stop();
        ((Logger) LoggerFactory.getLogger(BasicStatusHolder.class)).detachAppender(memoryAppender);
    }

    private static Stream<Arguments> provideArgumentsForPlugins() {
        return Stream.of(
                Arguments.of(new Exception("warn"), Level.WARN),
                Arguments.of(new Exception("info"), Level.INFO),
                Arguments.of(new Exception("debug"), Level.DEBUG),
                Arguments.of(null, Level.ERROR),
                Arguments.of(new Exception(), Level.ERROR),
                Arguments.of(new Exception(), null)
        );
    }

    @Test
    void errorWithThrottle_shouldRecordError_whenCalledWithMessageAndException() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);
        String errorMessage = "Test error message";
        Exception exception = new Exception("Test exception");

        statusHolder.errorWithThrottle(errorMessage, exception, logger);

        assertTrue(statusHolder.hasErrors(), "Error should be recorded in statusHolder");
        assertEquals(errorMessage, statusHolder.getLastError().getMessage());
        assertEquals(exception, statusHolder.getLastError().getException());
    }

    @Test
    void errorWithThrottle_shouldRecordError_whenCalledWithMessageOnly() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);
        String errorMessage = "Test error message without exception";

        statusHolder.errorWithThrottle(errorMessage, null, logger);

        assertTrue(statusHolder.hasErrors(), "Error should be recorded in statusHolder");
        assertEquals(errorMessage, statusHolder.getLastError().getMessage());
        assertNull(statusHolder.getLastError().getException());
    }

    @Test
    void errorWithThrottle_shouldUseLogThrottleMarker_whenLogging() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);
        String errorMessage = "Test error with throttling";

        statusHolder.errorWithThrottle(errorMessage, new Exception("Test"), logger);

        assertTrue(statusHolder.hasErrors(), "Error should be recorded in statusHolder");
        assertEquals(1, statusHolder.getErrors().size());
    }

    @Test
    void warnWithThrottle_shouldRecordWarning_whenCalledWithMessageAndException() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);
        String warningMessage = "Test warning message";
        Exception exception = new Exception("Test exception");

        statusHolder.warnWithThrottle(warningMessage, exception, logger);

        assertTrue(statusHolder.hasWarnings(), "Warning should be recorded in statusHolder");
        assertEquals(warningMessage, statusHolder.getLastWarning().getMessage());
        assertEquals(exception, statusHolder.getLastWarning().getException());
    }

    @Test
    void warnWithThrottle_shouldRecordWarning_whenCalledWithMessageOnly() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);
        String warningMessage = "Test warning message without exception";

        statusHolder.warnWithThrottle(warningMessage, null, logger);

        assertTrue(statusHolder.hasWarnings(), "Warning should be recorded in statusHolder");
        assertEquals(warningMessage, statusHolder.getLastWarning().getMessage());
        assertNull(statusHolder.getLastWarning().getException());
    }

    @Test
    void warnWithThrottle_shouldUseLogThrottleMarker_whenLogging() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);
        String warningMessage = "Test warning with throttling";

        statusHolder.warnWithThrottle(warningMessage, new Exception("Test"), logger);

        assertTrue(statusHolder.hasWarnings(), "Warning should be recorded in statusHolder");
        assertEquals(1, statusHolder.getWarnings().size());
    }

    @Test
    void errorWithThrottle_shouldNotAffectHasErrors_whenLoggingDisabled() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setActivateLogging(false);
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        String errorMessage = "Test error with logging disabled";

        statusHolder.errorWithThrottle(errorMessage, new Exception("Test"), logger);

        assertTrue(statusHolder.hasErrors(), "Error should still be recorded even when logging is disabled");
    }

    @Test
    void warnWithThrottle_shouldNotAffectHasWarnings_whenLoggingDisabled() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.setActivateLogging(false);
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        String warningMessage = "Test warning with logging disabled";

        statusHolder.warnWithThrottle(warningMessage, new Exception("Test"), logger);

        assertTrue(statusHolder.hasWarnings(), "Warning should still be recorded even when logging is disabled");
    }

    @Test
    void errorWithThrottle_shouldRecordMultipleErrors_whenCalledMultipleTimes() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);

        statusHolder.errorWithThrottle("Error 1", new Exception("Exception 1"), logger);
        statusHolder.errorWithThrottle("Error 2", new Exception("Exception 2"), logger);

        assertTrue(statusHolder.hasErrors(), "Errors should be recorded");
        assertEquals(2, statusHolder.getErrors().size());
        assertEquals("Error 2", statusHolder.getLastError().getMessage());
    }

    @Test
    void warnWithThrottle_shouldRecordMultipleWarnings_whenCalledMultipleTimes() {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.DEBUG);

        statusHolder.warnWithThrottle("Warning 1", new Exception("Exception 1"), logger);
        statusHolder.warnWithThrottle("Warning 2", new Exception("Exception 2"), logger);

        assertTrue(statusHolder.hasWarnings(), "Warnings should be recorded");
        assertEquals(2, statusHolder.getWarnings().size());
        assertEquals("Warning 2", statusHolder.getLastWarning().getMessage());
    }

    @Test
    void newHolderDoesNotAllocateQueuesUntilFirstEntry() throws Exception {
        BasicStatusHolder holder = new BasicStatusHolder();
        assertFalse(holder.isError());
        assertTrue(holder.getEntries().isEmpty());
        assertNull(getQueue(holder, "statusEntries"));
        assertNull(getQueue(holder, "errorEntries"));
        assertNull(getQueue(holder, "warningEntries"));
    }

    @SuppressWarnings("unchecked")
    private static ArrayBlockingQueue<StatusEntry> getQueue(BasicStatusHolder holder, String fieldName)
            throws Exception {
        Field field = BasicStatusHolder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((AtomicReference<ArrayBlockingQueue<StatusEntry>>) field.get(holder)).get();
    }

    @Test
    void merge_appendsErrorsWarningsAndStatusEntriesFromOtherHolder() {
        Logger logger = (Logger) LoggerFactory.getLogger(BasicStatusHolder.class);
        logger.setLevel(Level.OFF);

        BasicStatusHolder primary = new BasicStatusHolder();
        primary.status("primary-status-a", logger);
        primary.warn("primary-warn-a", logger);
        primary.error("primary-error-a", 400, null, logger);

        BasicStatusHolder secondary = new BasicStatusHolder();
        secondary.status("secondary-status-a", logger);
        secondary.warn("secondary-warn-a", logger);
        secondary.error("secondary-error-a", 400, null, logger);
        secondary.error("secondary-error-b", 400, null, logger);

        primary.merge(secondary);

        assertTrue(primary.hasErrors());
        assertTrue(primary.hasWarnings());
        assertEquals("secondary-error-a", primary.getLastError().getMessage());
        assertEquals("secondary-warn-a", primary.getLastWarning().getMessage());
        Set<String> expectedMessages = primary.getEntries().stream().map(StatusEntry::getMessage).collect(toSet());
        assertEquals(Set.of(
                "primary-status-a",
                "primary-warn-a",
                "primary-error-a",
                "secondary-status-a",
                "secondary-warn-a",
                "secondary-error-a",
                "secondary-error-b"
        ), expectedMessages);
    }
}