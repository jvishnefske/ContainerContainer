package org.artifactory.devopsagent.errorcatalog.job;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jfrog.test.log.MemoryAppender;
import com.jfrog.test.log.MemoryAppenderUtil;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.devopsagent.errorcatalog.ErrorCatalogEvent;
import org.artifactory.devopsagent.errorcatalog.ingestion.ErrorCatalogEventBuffer;
import org.artifactory.devopsagent.errorcatalog.ingestion.SignatureToTemplateIdCache;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.dao.ErrorCatalogTemplateDao;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.RawEventInsertRequest;
import org.artifactory.storage.db.devopsagent.errorcatalog.model.TemplateUpsertRequest;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErrorCatalogFlushJobTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    ArtifactoryContext context;
    @Mock
    ErrorCatalogEventBuffer buffer;
    @Mock
    ErrorCatalogTemplateDao templatesDao;
    @Mock
    ErrorCatalogDao rawDao;
    @Mock
    SignatureToTemplateIdCache cache;
    AutoCloseable mocks;

    private ErrorCatalogFlushJob job;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(context.isReady()).thenReturn(true);
        when(context.beanForType(ErrorCatalogEventBuffer.class)).thenReturn(buffer);
        when(context.beanForType(ErrorCatalogTemplateDao.class)).thenReturn(templatesDao);
        when(context.beanForType(ErrorCatalogDao.class)).thenReturn(rawDao);
        when(context.beanForType(SignatureToTemplateIdCache.class)).thenReturn(cache);
        ArtifactoryContextThreadBinder.bind(context);
        job = new ErrorCatalogFlushJob();
    }

    @AfterEach
    void tearDown() throws Exception {
        ArtifactoryContextThreadBinder.unbind();
        mocks.close();
    }

    @Test
    void onExecuteDrainsBufferUpsertsTemplatesAndInsertsRaw() throws JobExecutionException {
        Map<String, ErrorCatalogEventBuffer.AggregatedEntry> aggregated = new HashMap<>();
        aggregated.put("sigA", aggEntry("sigA", "ERROR", 2));
        aggregated.put("sigB", aggEntry("sigB", "WARN", 1));
        List<ErrorCatalogEvent> raw = List.of(
                event("sigA", 1_000L), event("sigA", 1_500L), event("sigB", 2_000L));
        when(buffer.drain()).thenReturn(new ErrorCatalogEventBuffer.DrainResult(aggregated, raw));
        when(cache.getIfPresent(anyString())).thenReturn(null);
        when(templatesDao.findIdsBySignatureHashes(any())).thenReturn(Map.of("sigA", 11L, "sigB", 22L));

        job.onExecute(mock(JobExecutionContext.class));

        ArgumentCaptor<List<TemplateUpsertRequest>> upsertCaptor = ArgumentCaptor.forClass(List.class);
        verify(templatesDao, times(1)).upsertBatch(upsertCaptor.capture());
        assertEquals(2, upsertCaptor.getValue().size());

        verify(cache).put("sigA", 11L);
        verify(cache).put("sigB", 22L);

        ArgumentCaptor<List<RawEventInsertRequest>> rawCaptor = ArgumentCaptor.forClass(List.class);
        verify(rawDao, times(1)).insertBatch(rawCaptor.capture());
        assertEquals(3, rawCaptor.getValue().size());
    }

    @Test
    void onExecuteSkipsRawInsertWhenTemplateIdsUnresolved() throws JobExecutionException {
        Map<String, ErrorCatalogEventBuffer.AggregatedEntry> aggregated = new HashMap<>();
        aggregated.put("sigA", aggEntry("sigA", "ERROR", 1));
        aggregated.put("sigB", aggEntry("sigB", "ERROR", 1));
        List<ErrorCatalogEvent> raw = List.of(event("sigA", 1L), event("sigB", 2L));
        when(buffer.drain()).thenReturn(new ErrorCatalogEventBuffer.DrainResult(aggregated, raw));
        when(cache.getIfPresent(anyString())).thenReturn(null);
        when(templatesDao.findIdsBySignatureHashes(any())).thenReturn(Collections.emptyMap());

        job.onExecute(mock(JobExecutionContext.class));

        verify(templatesDao).upsertBatch(any());
        ArgumentCaptor<List<RawEventInsertRequest>> rawCaptor = ArgumentCaptor.forClass(List.class);
        verify(rawDao).insertBatch(rawCaptor.capture());
        assertTrue(rawCaptor.getValue().isEmpty(), "no resolved ids → no raw rows inserted");
    }

    @Test
    void onExecuteWarnsAndRethrowsWhenDaoThrows() {
        Map<String, ErrorCatalogEventBuffer.AggregatedEntry> aggregated = new HashMap<>();
        aggregated.put("sigA", aggEntry("sigA", "ERROR", 1));
        when(buffer.drain()).thenReturn(new ErrorCatalogEventBuffer.DrainResult(
                aggregated, List.of(event("sigA", 1L))));
        doThrow(new StorageException("upsert failed")).when(templatesDao).upsertBatch(any());

        MemoryAppender appender = MemoryAppenderUtil.createMemoryAppender(
                ErrorCatalogFlushJob.class, Level.WARN);

        assertThrows(StorageException.class, () -> job.onExecute(mock(JobExecutionContext.class)));

        verify(rawDao, never()).insertBatch(any());
        List<ILoggingEvent> logged = appender.getLoggedEvents();
        assertTrue(logged.stream().anyMatch(e -> e.getFormattedMessage().contains("flush failed")),
                "warn log on flush failure expected");
    }

    @Test
    void onExecuteIsNoOpWhenContextNotReady() throws JobExecutionException {
        when(context.isReady()).thenReturn(false);

        job.onExecute(mock(JobExecutionContext.class));

        verify(buffer, never()).drain();
        verify(templatesDao, never()).upsertBatch(any());
        verify(rawDao, never()).insertBatch(any());
    }

    @Test
    void onExecuteIsNoOpWhenBufferDrainIsEmpty() throws JobExecutionException {
        when(buffer.drain()).thenReturn(new ErrorCatalogEventBuffer.DrainResult(
                Map.of(), List.of()));

        job.onExecute(mock(JobExecutionContext.class));

        verify(templatesDao, never()).upsertBatch(any());
        verify(rawDao, never()).insertBatch(any());
    }

    @Test
    void onExecuteCacheHitSkipsDaoSignatureLookup() throws JobExecutionException {
        Map<String, ErrorCatalogEventBuffer.AggregatedEntry> aggregated = new HashMap<>();
        aggregated.put("sigA", aggEntry("sigA", "ERROR", 1));
        aggregated.put("sigB", aggEntry("sigB", "WARN", 1));
        List<ErrorCatalogEvent> raw = List.of(event("sigA", 1L), event("sigB", 2L));
        when(buffer.drain()).thenReturn(new ErrorCatalogEventBuffer.DrainResult(aggregated, raw));
        when(cache.getIfPresent("sigA")).thenReturn(11L);
        when(cache.getIfPresent("sigB")).thenReturn(22L);

        job.onExecute(mock(JobExecutionContext.class));

        verify(templatesDao, never()).findIdsBySignatureHashes(any());
        verify(cache, never()).put(anyString(), anyLong());
        ArgumentCaptor<List<RawEventInsertRequest>> rawCaptor = ArgumentCaptor.forClass(List.class);
        verify(rawDao).insertBatch(rawCaptor.capture());
        assertEquals(2, rawCaptor.getValue().size());
    }

    private static ErrorCatalogEventBuffer.AggregatedEntry aggEntry(String sig, String severity, long count) {
        return new ErrorCatalogEventBuffer.AggregatedEntry(
                sig, severity, "Logger", "java.lang.Exception",
                "template", 1L, 1L, count);
    }

    private static ErrorCatalogEvent event(String sig, long occurredAtMs) {
        return new ErrorCatalogEvent(
                "ERROR", "Logger", "java.lang.Exception", "template", "raw", sig, occurredAtMs);
    }
}
