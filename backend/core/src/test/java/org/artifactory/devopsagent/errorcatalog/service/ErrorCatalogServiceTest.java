package org.artifactory.devopsagent.errorcatalog.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogEventBuffer;
import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogLogbackAppender;
import org.artifactory.schedule.Task;
import org.artifactory.schedule.TaskService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorCatalogServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private TaskService taskService;
    @Mock
    private ErrorCatalogEventBuffer buffer;
    @Mock
    private ArtifactoryDbProperties dbProperties;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        // Enable the feature for tests (defaults to false)
        setStringSystemProperty(org.artifactory.common.ConstantValues.devopsTroubleshootingEnabled, "true");
    }

    @AfterEach
    void tearDown() throws Exception {
        ErrorCatalogLogbackAppender.uninstallBuffer();
        mocks.close();
    }

    @Test
    void initializeSkipsAllJobsWhenFeatureFlagDisabled() {
        setStringSystemProperty(org.artifactory.common.ConstantValues.devopsTroubleshootingEnabled, "false");
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

        new ErrorCatalogService(taskService, buffer, dbProperties).initialize();

        verify(taskService, never()).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void initializeInstallsBufferAndSchedulesThreeJobsOnPg() {
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);

        new ErrorCatalogService(taskService, buffer, dbProperties).initialize();

        verify(taskService, times(3)).startTask(any(Task.class), anyBoolean());
    }

    @Test
    void initializeSkipsGinReindexJobOnNonPg() {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);

        new ErrorCatalogService(taskService, buffer, dbProperties).initialize();

        verify(taskService, times(2)).startTask(any(Task.class), anyBoolean());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-a-cron", "0/5 * * * * ?"})
    void scheduleJobHandlesBlankInvalidAndValidCron(String cron) {
        when(dbProperties.getDbType()).thenReturn(DbType.DERBY);
        setStringSystemProperty(org.artifactory.common.ConstantValues.errorCatalogFlushCron, cron);
        setStringSystemProperty(org.artifactory.common.ConstantValues.errorCatalogRetentionCron, cron);

        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                ErrorCatalogService.class, Level.DEBUG);

        new ErrorCatalogService(taskService, buffer, dbProperties).initialize();

        List<ILoggingEvent> logged = appender.getLoggedEvents();
        if (cron.isBlank()) {
            verify(taskService, never()).startTask(any(Task.class), anyBoolean());
            assertEquals(2, logged.stream()
                    .filter(e -> e.getMessage().contains("Skipping schedule") && e.getLevel() == Level.INFO)
                    .count(), "blank cron expected two info-level skip logs");
        } else if (!org.quartz.CronExpression.isValidExpression(cron)) {
            verify(taskService, never()).startTask(any(Task.class), anyBoolean());
            assertEquals(2, logged.stream()
                    .filter(e -> e.getMessage().contains("Skipping schedule") && e.getLevel() == Level.WARN)
                    .count(), "invalid cron expected two warn-level skip logs");
        } else {
            verify(taskService, times(2)).startTask(any(Task.class), anyBoolean());
            assertTrue(logged.stream().anyMatch(e -> e.getMessage().contains("Scheduled")),
                    "valid cron expected a 'Scheduled' info log");
        }
    }

    @Test
    void scheduleJobSwallowsTaskServiceException() {
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        when(taskService.startTask(any(Task.class), anyBoolean()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn("retention-task")
                .thenReturn("gin-task");

        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                ErrorCatalogService.class, Level.ERROR);

        new ErrorCatalogService(taskService, buffer, dbProperties).initialize();

        verify(taskService, times(3)).startTask(any(Task.class), anyBoolean());
        List<ILoggingEvent> logged = appender.getLoggedEvents();
        assertEquals(1, logged.size(), "exactly one error log expected (from the boom)");
        assertTrue(logged.get(0).getFormattedMessage().contains("Failed to schedule"),
                "error log must reference the failed schedule");
    }
}
