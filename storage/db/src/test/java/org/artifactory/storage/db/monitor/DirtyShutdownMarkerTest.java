package org.artifactory.storage.db.monitor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

/**
 * Unit tests for {@link DirtyShutdownMarker}.
 *
 * @author Eli Fishman
 */
class DirtyShutdownMarkerTest extends ArtifactoryHomeBoundJUnitTest {

    @TempDir
    File tempDir;

    private Logger logger;
    private Level originalLevel;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DirtyShutdownMarker.class);
        originalLevel = logger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    @Test
    void privateConstructor_isUnreachableByDesign() throws Exception {
        Constructor<DirtyShutdownMarker> ctor =
                DirtyShutdownMarker.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        assertDoesNotThrow(() -> ctor.newInstance(),
                "Private utility constructor must not throw");
    }

    // ── getMarkerForCurrentNode ───────────────────────────────────────────────

    @Test
    void getMarkerForCurrentNode_returnsPerNodeMarkerPath_whenHomeBound() {
        File file = DirtyShutdownMarker.getMarkerForCurrentNode();

        assertNotNull(file, "Bound ArtifactoryHome must produce a resolved path");
        assertTrue(file.getName().startsWith("dirty."),
                "File name must start with the 'dirty.' prefix");
        assertTrue(file.getName().endsWith(".flag"),
                "File name must end with the '.flag' suffix");
    }

    @Test
    void getMarkerForCurrentNode_returnsNull_andLogsDebug_whenHomeThrows() {
        try (MockedStatic<ArtifactoryHome> mocked = Mockito.mockStatic(ArtifactoryHome.class)) {
            mocked.when(ArtifactoryHome::get)
                    .thenThrow(new IllegalStateException("home not bound"));

            assertNull(DirtyShutdownMarker.getMarkerForCurrentNode(),
                    "Exception from ArtifactoryHome must be swallowed and null returned");
            assertTrue(anyLogContains(Level.WARN, "Cannot resolve dirty-shutdown marker path"),
                    "Failure must be logged at DEBUG");
        }
    }

    // ── createIfAbsent ────────────────────────────────────────────────────────

    @Test
    void createIfAbsent_returnsFalse_onNullArg() {
        assertFalse(DirtyShutdownMarker.createIfAbsent(null),
                "Null path must be rejected with false (no exception)");
    }

    @Test
    void createIfAbsent_createsFile_onFirstCall_andReturnsFalse_onRepeat() throws IOException {
        File marker = new File(tempDir, "dirty.test.flag");

        assertTrue(DirtyShutdownMarker.createIfAbsent(marker),
                "First call must create the file and return true");
        assertTrue(marker.exists(), "File must exist on disk after a successful create");

        assertFalse(DirtyShutdownMarker.createIfAbsent(marker),
                "Second call must return false because the file already exists");
    }

    @Test
    void createIfAbsent_logsWarn_andReturnsFalse_onIoException() {
        // File.createNewFile() throws IOException when the parent directory does not exist.
        File marker = new File(tempDir, "missing-subdir/dirty.test.flag");

        assertFalse(DirtyShutdownMarker.createIfAbsent(marker),
                "IOException must be caught and surfaced as false");
        assertTrue(anyLogContains(Level.WARN, "Cannot create dirty-shutdown marker"),
                "I/O failure must produce the WARN diagnostic with the path");
    }

    // ── deleteIfExists ────────────────────────────────────────────────────────

    @Test
    void deleteIfExists_isSilentNoOp_onNullArg() {
        assertDoesNotThrow(() -> DirtyShutdownMarker.deleteIfExists(null),
                "Null path must be a silent no-op with no exception");
        assertTrue(noWarnLogged(), "Null path must not emit any WARN");
    }

    @Test
    void deleteIfExists_deletesExistingFile_withoutWarn() throws IOException {
        File marker = new File(tempDir, "dirty.test.flag");
        assertTrue(marker.createNewFile());

        DirtyShutdownMarker.deleteIfExists(marker);

        assertFalse(marker.exists(), "File must be gone after a successful delete");
        assertTrue(noWarnLogged(), "Successful delete must not emit any WARN");
    }

    @Test
    void deleteIfExists_isSilentNoOp_whenFileDoesNotExist() {
        File marker = new File(tempDir, "dirty.test.flag");
        assertFalse(marker.exists());

        assertDoesNotThrow(() -> DirtyShutdownMarker.deleteIfExists(marker),
                "deleteIfExists on a non-existent file must not throw");
        assertTrue(noWarnLogged(),
                "Missing file must not produce a WARN (unlike old OomFlagFile behaviour)");
    }

    @Test
    void deleteIfExists_logsWarn_onIoException() {
        File marker = new File(tempDir, "dirty.test.flag");
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class)))
                    .thenThrow(new IOException("disk error"));

            DirtyShutdownMarker.deleteIfExists(marker);

            assertTrue(anyLogContains(Level.WARN, "Cannot delete dirty-shutdown marker"),
                    "IOException during delete must produce the WARN diagnostic with the path");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean anyLogContains(Level level, String needle) {
        return appender.list.stream()
                .filter(e -> e.getLevel().equals(level))
                .anyMatch(e -> e.getFormattedMessage().contains(needle));
    }

    private boolean noWarnLogged() {
        return appender.list.stream().noneMatch(e -> e.getLevel().equals(Level.WARN));
    }
}
