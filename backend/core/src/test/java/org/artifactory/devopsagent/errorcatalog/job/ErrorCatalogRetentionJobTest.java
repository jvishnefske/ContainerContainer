package org.artifactory.devopsagent.errorcatalog.job;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.DbType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorCatalogRetentionJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext context;
    @Mock
    ErrorCatalogTemplateDao templatesDao;
    @Mock
    ErrorCatalogDao rawDao;
    @Mock
    ArtifactoryDbProperties dbProperties;
    AutoCloseable mocks;

    private ErrorCatalogRetentionJob job;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.isReady()).thenReturn(true);
        when(context.beanForType(ErrorCatalogTemplateDao.class)).thenReturn(templatesDao);
        when(context.beanForType(ErrorCatalogDao.class)).thenReturn(rawDao);
        when(context.beanForType(ArtifactoryDbProperties.class)).thenReturn(dbProperties);
        when(dbProperties.getDbType()).thenReturn(DbType.POSTGRESQL);
        ArtifactoryContextThreadBinder.bind(context);
        job = new ErrorCatalogRetentionJob();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void onExecuteSweepsRawAndTemplatesAndRecordsSuccess() throws JobExecutionException {
        setLongSystemProperty(ConstantValues.errorCatalogRetentionDays, 7L);
        setLongSystemProperty(ConstantValues.errorCatalogRetentionDeleteBatchSize, 10_000L);
        setLongSystemProperty(ConstantValues.errorCatalogRetentionDeleteBatchSleepMs, 50L);
        when(rawDao.deleteOlderThanChunked(anyLong(), anyInt(), anyLong())).thenReturn(100);
        when(templatesDao.deleteOlderThan(anyLong())).thenReturn(5);

        long beforeMs = System.currentTimeMillis();
        job.onExecute(mock(JobExecutionContext.class));
        long afterMs = System.currentTimeMillis();

        ArgumentCaptor<Long> cutoffCaptor = ArgumentCaptor.forClass(Long.class);
        verify(rawDao).deleteOlderThanChunked(cutoffCaptor.capture(), eq(10_000), eq(50L));
        long retentionWindowMs = 7L * 86_400_000L;
        long lowerBound = beforeMs - retentionWindowMs;
        long upperBound = afterMs - retentionWindowMs;
        long cutoff = cutoffCaptor.getValue();
        assertTrue(cutoff >= lowerBound && cutoff <= upperBound,
                "cutoff " + cutoff + " must fall within [" + lowerBound + "," + upperBound + "]");

        verify(templatesDao).deleteOlderThan(anyLong());
    }

    @Test
    void onExecuteIsNoOpWhenContextNotReady() throws JobExecutionException {
        when(context.isReady()).thenReturn(false);

        job.onExecute(mock(JobExecutionContext.class));

        verify(rawDao, never()).deleteOlderThanChunked(anyLong(), anyInt(), anyLong());
        verify(templatesDao, never()).deleteOlderThan(anyLong());
    }

    @Test
    void onExecuteLogsErrorAndRethrowsOnFailure() {
        when(rawDao.deleteOlderThanChunked(anyLong(), anyInt(), anyLong()))
                .thenThrow(new StorageException("chunked delete failed"));

        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                ErrorCatalogRetentionJob.class, Level.ERROR);

        assertThrows(JobExecutionException.class,
                () -> job.onExecute(mock(JobExecutionContext.class)));

        List<ILoggingEvent> logged = appender.getLoggedEvents();
        assertTrue(logged.stream().anyMatch(e -> e.getFormattedMessage().contains("retention run failed")),
                "error log on retention failure expected");
    }
}
