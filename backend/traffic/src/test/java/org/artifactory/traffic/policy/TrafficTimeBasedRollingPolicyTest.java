package org.artifactory.traffic.policy;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TrafficTimeBasedRollingPolicyTest {

    @TempDir
    Path tempDir;

    private TrafficTimeBasedRollingPolicy policy;
    private RollingFileAppender<?> parentAppender;
    private Context context;

    @BeforeEach
    void setUp() {
        policy = spy(new TrafficTimeBasedRollingPolicy());
        parentAppender = mock(RollingFileAppender.class);
        context = mock(Context.class);
        policy.setParent(parentAppender);
        policy.setContext(context);
        // TimeBasedRollingPolicy requires fileNamePattern to be set before start()
        policy.setFileNamePattern(tempDir.resolve("archived").toString() + "/artifactory-traffic.%d{yyyyMMdd}");
    }

    @AfterEach
    void tearDown() {
        if (policy != null && policy.isStarted()) {
            policy.stop();
        }
    }

    @Test
    void start_shouldThrowException_whenActiveLogFilePathIsNull() {
        when(policy.getParentsRawFileProperty()).thenReturn(null);
        policy.activeLogFilePath = null;

        assertThrows(IllegalStateException.class, () -> policy.start(),
                "Appender file name must be set.");
    }

    @Test
    void start_shouldInitializeActiveLogFilePathFromParent_whenActiveLogFilePathIsNull() {
        String expectedPath = tempDir.resolve("traffic").toString();
        when(policy.getParentsRawFileProperty()).thenReturn(expectedPath);

        policy.start();

        assertEquals(expectedPath, policy.activeLogFilePath);
    }

    @Test
    void start_shouldUseExistingActiveLogFilePath_whenAlreadySet() {
        String existingPath = tempDir.resolve("existing-traffic").toString();
        policy.activeLogFilePath = existingPath;

        policy.start();

        assertEquals(existingPath, policy.activeLogFilePath);
    }

    @Test
    void start_shouldCreateDirectory_whenDirectoryDoesNotExist() {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());

        assertFalse(logsDir.exists());

        policy.start();

        assertTrue(logsDir.exists());
    }

    @Test
    void start_shouldNotFail_whenListFilesReturnsNull() {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());

        // Create directory but make it non-readable to simulate listFiles() returning null
        logsDir.mkdirs();
        logsDir.setReadable(false);

        try {
            policy.start();

            // Should set startedTime when no last active log file is found
            assertTrue(policy.startedTime > 0);
        } finally {
            logsDir.setReadable(true);
        }
    }

    @Test
    void start_shouldSetStartedTime_whenNoLastActiveLogFileFound() {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());
        logsDir.mkdirs();

        policy.start();

        assertTrue(policy.startedTime > 0);
    }

    @Test
    void start_shouldFindAndSetStartedTimeFromLastActiveLogFile_whenLastActiveLogFileExists() throws IOException {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());
        logsDir.mkdirs();

        long expectedStartedTime = 1234567890L;
        File lastActiveLogFile = new File(logsDir, "artifactory-traffic." + expectedStartedTime + ".log");
        Files.createFile(lastActiveLogFile.toPath());

        policy.start();

        assertEquals(expectedStartedTime, policy.startedTime);
    }

    @Test
    void start_shouldNotUseRolledOverLogFile_whenOnlyRolledOverFileExists() throws IOException {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());
        logsDir.mkdirs();

        // Create a rolled-over file (contains date separator)
        File rolledOverFile = new File(logsDir, "artifactory-traffic.1234567890-9876543210.log");
        Files.createFile(rolledOverFile.toPath());

        policy.start();

        // Should set new startedTime since rolled-over file is not considered active
        assertTrue(policy.startedTime > 0);
        assertTrue(policy.startedTime != 1234567890L);
    }

    @Test
    void start_shouldIgnoreNonLogFiles_whenDirectoryContainsOtherFiles() throws IOException {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());
        logsDir.mkdirs();

        // Create non-log files
        Files.createFile(logsDir.toPath().resolve("other-file.txt"));
        Files.createFile(logsDir.toPath().resolve("artifactory-traffic.backup"));

        policy.start();

        // Should set new startedTime since no valid active log file found
        assertTrue(policy.startedTime > 0);
    }

    @Test
    void start_shouldNotChangeStartedTime_whenStartedTimeIsAlreadySet() {
        File logsDir = tempDir.resolve("logs").toFile();
        File logFile = new File(logsDir, "artifactory-traffic");
        when(policy.getParentsRawFileProperty()).thenReturn(logFile.getAbsolutePath());
        logsDir.mkdirs();

        long existingStartedTime = 9999999999L;
        policy.startedTime = existingStartedTime;

        policy.start();

        assertEquals(existingStartedTime, policy.startedTime);
    }

    @Test
    void start_shouldSetStartedTime_whenLogsDirIsNull() {
        String logFileNameWithoutPath = "artifactory-traffic";
        when(policy.getParentsRawFileProperty()).thenReturn(logFileNameWithoutPath);

        policy.start();

        assertTrue(policy.startedTime > 0);
    }
}
