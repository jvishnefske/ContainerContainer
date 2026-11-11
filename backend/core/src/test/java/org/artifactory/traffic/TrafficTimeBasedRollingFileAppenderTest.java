package org.artifactory.traffic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests {@link TrafficTimeBasedRollingFileAppender#internalRollover()}: the rollover is skipped when the active
 * file is empty, missing, or unset, and proceeds normally when the file has content.
 */
class TrafficTimeBasedRollingFileAppenderTest {

    @TempDir
    Path tempDir;

    private TrafficTimeBasedRollingFileAppender appenderWithFile(String activeFilePath) {
        TrafficTimeBasedRollingFileAppender appender = spy(new TrafficTimeBasedRollingFileAppender());
        doReturn(activeFilePath).when(appender).getFile();
        // Stub the actual rollover so assertions target only the empty-file decision, not logback's file rotation.
        doNothing().when(appender).rollover();
        return appender;
    }

    @Test
    void shouldSkipRollover_whenActiveFileIsEmpty() throws Exception {
        Path emptyFile = Files.createFile(tempDir.resolve("artifactory-traffic-v3.log"));
        TrafficTimeBasedRollingFileAppender appender = appenderWithFile(emptyFile.toString());
        appender.internalRollover();
        verify(appender, never()).rollover();
    }

    @Test
    void shouldSkipRollover_whenActiveFileIsMissing() throws Exception {
        Path file = Files.createFile(tempDir.resolve("artifactory-traffic-v3.log"));
        Files.delete(file);
        TrafficTimeBasedRollingFileAppender appender = appenderWithFile(file.toString());
        appender.internalRollover();
        verify(appender, never()).rollover();
    }

    @Test
    void shouldSkipRollover_whenActiveFilePathIsBlank() {
        TrafficTimeBasedRollingFileAppender appender = appenderWithFile("");
        appender.internalRollover();
        verify(appender, never()).rollover();
    }

    @Test
    void shouldSkipRollover_whenActiveFilePathIsNull() {
        TrafficTimeBasedRollingFileAppender appender = appenderWithFile(null);
        appender.internalRollover();
        verify(appender, never()).rollover();
    }

    @Test
    void shouldRollOver_whenActiveFileHasContent() throws Exception {
        Path file = tempDir.resolve("artifactory-traffic-v3.log");
        Files.writeString(file, "20260608163341|trace|DOWNLOAD|USER|DIRECT|::1|repo:path/file.txt|9|9|9|repo\n");
        TrafficTimeBasedRollingFileAppender appender = appenderWithFile(file.toString());
        appender.internalRollover();
        verify(appender).rollover();
    }
}
