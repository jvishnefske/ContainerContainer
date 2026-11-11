package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.common.status.StatusEntryLevel;
import org.artifactory.common.status.StatusEntryWithDifferentLogLevel;
import org.artifactory.repo.RepoPath;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.CopyMoveArtifact;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseCopyMoveArtifactServiceTest {

    private MemoryAppender memoryAppender;
    private BaseCopyMoveArtifactService service;
    private BaseCopyMoveArtifactService.DoInBackground doInBackground;

    @BeforeEach
    void setUp() {
        memoryAppender = MemoryAppenderUtil.createMemoryAppender(BaseCopyMoveArtifactService.class, null);
        ((Logger) LoggerFactory.getLogger(BaseCopyMoveArtifactService.class)).setLevel(Level.DEBUG);
        service = new BaseCopyMoveArtifactService() {
            @Override
            public void execute(ArtifactoryRestRequest request, RestResponse response) {
                //ignore
            }

            @Override
            MoveMultiStatusHolder doOperation(CopyMoveArtifact artifact, RepoPath sourcePath, RepoPath targetPath) {
                return null;
            }

            @Override
            OperationType getOperationType() {
                return OperationType.COPY;
            }
        };
        doInBackground = service.new DoInBackground(null, null, null);
    }

    @AfterEach
    void tearDown() {
        memoryAppender.stop();
        ((Logger) LoggerFactory.getLogger(BaseCopyMoveArtifactService.class)).detachAppender(memoryAppender);
    }

    @Test
    void logErrorAtLogLevel_regularStatusEntry_shouldLogAtError() {
        String message = "regular error message";
        StatusEntry entry = new StatusEntry(500, StatusEntryLevel.ERROR, message, null);

        doInBackground.logErrorAtLogLevel(entry);

        assertEquals(1, memoryAppender.search(message, Level.ERROR).size());
        assertEquals(0, memoryAppender.search(message, Level.WARN).size());
    }

    @Test
    void logErrorAtLogLevel_statusEntryWithWarnLevel_shouldLogAtWarn() {
        String message = "warn level error message";
        StatusEntryWithDifferentLogLevel entry = new StatusEntryWithDifferentLogLevel(
                500, StatusEntryLevel.ERROR, message, null, Level.WARN);

        doInBackground.logErrorAtLogLevel(entry);

        assertEquals(1, memoryAppender.search(message, Level.WARN).size());
        assertEquals(0, memoryAppender.search(message, Level.ERROR).size());
    }

    @Test
    void logErrorAtLogLevel_statusEntryWithErrorLevel_shouldLogAtError() {
        String message = "error level error message";
        StatusEntryWithDifferentLogLevel entry = new StatusEntryWithDifferentLogLevel(
                500, StatusEntryLevel.ERROR, message, null, Level.ERROR);

        doInBackground.logErrorAtLogLevel(entry);

        assertEquals(1, memoryAppender.search(message, Level.ERROR).size());
        assertEquals(0, memoryAppender.search(message, Level.WARN).size());
    }

    @Test
    void logErrorAtLogLevel_statusEntryWithInfoLevel_shouldDefaultToError() {
        String message = "info level error message";
        StatusEntryWithDifferentLogLevel entry = new StatusEntryWithDifferentLogLevel(
                500, StatusEntryLevel.ERROR, message, null, Level.INFO);

        doInBackground.logErrorAtLogLevel(entry);

        assertEquals(1, memoryAppender.search(message, Level.ERROR).size());
        assertEquals(0, memoryAppender.search(message, Level.INFO).size());
    }

    @Test
    void logErrorAtLogLevel_statusEntryWithDebugLevel_shouldDefaultToError() {
        String message = "debug level error message";
        StatusEntryWithDifferentLogLevel entry = new StatusEntryWithDifferentLogLevel(
                500, StatusEntryLevel.ERROR, message, null, Level.DEBUG);

        doInBackground.logErrorAtLogLevel(entry);

        assertEquals(1, memoryAppender.search(message, Level.ERROR).size());
        assertEquals(0, memoryAppender.search(message, Level.DEBUG).size());
    }
}
