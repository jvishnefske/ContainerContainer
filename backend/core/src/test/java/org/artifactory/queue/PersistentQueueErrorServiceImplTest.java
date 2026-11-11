package org.artifactory.queue;

import com.google.common.collect.Sets;
import jakarta.annotation.Nonnull;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.error.PersistentQueueErrorServiceImpl;
import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.queue.register.RepoAndCursor;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.error.dao.EventAndErrorInfoDao;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.error.model.RepoAndCategory;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.jfrog.common.ClockUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.artifactory.event.EventType.create;
import static org.artifactory.queue.model.PersistentQueueRunningResult.failure;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author avishayh
 */
@Test
public class PersistentQueueErrorServiceImplTest extends PersistentQueueBaseTest {

    @Mock
    EventAndErrorInfoDao errorInfoDao;
    @Mock
    NodeEventsErrorsStorageService storageService;
    @Mock
    PersistentQueue persistentQueue;

    PersistentQueueErrorService errorService;
    QueueConfiguration queueConfiguration;

    @BeforeMethod
    public void startup() {
        errorService = new PersistentQueueErrorServiceImpl(storageService, errorInfoDao);
        queueConfiguration = createIncrementalIndexQueue();
        when(persistentQueue.getQueueConfiguration()).thenReturn(queueConfiguration);
        when(persistentQueue.getName()).thenReturn("name");
    }

    public void getErroneousQueues() throws Exception {
        // Given
        when(storageService.getErroneousRepos()).thenReturn(Sets.newHashSet(new RepoAndCategory("test",
                QueueCursorType.INCREMENTAL_INDEX.name())));

        // When
        Set<RepoAndCursor> repos = errorService.getErroneousQueues();

        // Then
        verify(storageService, times(1)).getErroneousRepos();
        assertThat(repos.size()).isEqualTo(1);
        assertThat(repos.iterator().next().getRepoKey()).isEqualTo("test");
        assertThat(repos.iterator().next().getCursorType()).isEqualTo(
                QueueCursorTypeMapper.get(NodeEventCursorType.INCREMENTAL_INDEX));
    }

    public void getErrors() throws SQLException {
        errorService.getErrors(persistentQueue);
        verify(errorInfoDao).loadErrorEvents(
                eq(QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName()),
                any(),
                eq(-1));
    }

    public void getErrorCount() throws SQLException {
        errorService.getErrorCount(persistentQueue);
        verify(errorInfoDao).getErrorCount(eq(QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName()),
                any());
    }

    public void errorSupportDisabled() throws SQLException {
        QueueConfiguration configuration = getIncrementalIndexBuilder()
                .enableErrorSupport(false)
                .build();
        when(persistentQueue.getQueueConfiguration()).thenReturn(configuration);
        assertThat(errorService.getErrorCount(persistentQueue)).isZero();
        verify(errorInfoDao, times(0)).getErrorCount(any(), any());
        assertThat(errorService.getErrors(persistentQueue)).isEmpty();
        verify(errorInfoDao, times(0)).loadErrorEvents(any(), any(), anyInt());
        errorService.deleteErrors(persistentQueue);
        verify(storageService, times(0)).deleteAll(any(), any());
        PersistentQueueRunningContext context = mock(PersistentQueueRunningContext.class);
        errorService.processErrors(persistentQueue, context);
        when(context.getExistingErrors()).thenThrow(new IllegalStateException("should not reach here"));
    }

    public void noErrors() {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        context.setEvents(createEvents());
        context.setExistingErrors(Map.of());
        context.setFailedResults(Set.of());
        errorService.processErrors(persistentQueue, context);

        verify(storageService, times(0)).add(any());
        verify(storageService, times(0)).updateById(any(), anyLong());
        verify(storageService, times(0)).delete(anyLong());
    }

    public void newError() throws InterruptedException {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        List<QueueEventImpl> events = createEvents();
        QueueEvent event = events.get(0);
        context.setEvents(events);
        context.setExistingErrors(Map.of());
        context.setFailedResults(Set.of(failure(event.getEventId(), "err", false)));

        long now = ClockUtils.epochMillis();
        Thread.sleep(10);

        errorService.processErrors(persistentQueue, context);

        ArgumentCaptor<NodeEventsErrorInfo> captor = ArgumentCaptor.forClass(NodeEventsErrorInfo.class);
        verify(storageService, times(1)).add(captor.capture());
        verify(storageService, times(0)).updateById(any(), anyLong());
        verify(storageService, times(0)).delete(anyLong());
        NodeEventsErrorInfo errorInfo = captor.getValue();
        Assert.assertEquals(Objects.requireNonNull(errorInfo.getEventId()).longValue(), event.getEventId());
        Assert.assertEquals(errorInfo.getPath().toPath(), event.getPath());
        Assert.assertEquals(errorInfo.getEventType(), event.getType());
        assertThat(errorInfo.getFirstErrorTime()).isGreaterThan(now);
        Assert.assertEquals(errorInfo.getErrorCount(), 1);
    }

    public void omitNewError() throws InterruptedException {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        List<QueueEventImpl> events = createEvents();
        QueueEvent event = events.get(0);
        context.setEvents(events);
        context.setExistingErrors(Map.of());
        context.setFailedResults(Set.of(failure(event.getEventId(), "err", true)));
        errorService.processErrors(persistentQueue, context);
        verify(storageService, never()).add(any());
    }

    public void existingErrorFixed() {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        List<QueueEventImpl> events = createEvents();
        context.setEvents(events);
        QueueEvent event = events.get(0);
        long errorId = 15L;
        context.setExistingErrors(Map.of(event.getEventId(), createError(event, errorId)));
        context.setFailedResults(Set.of());

        errorService.processErrors(persistentQueue, context);
        verify(storageService, times(0)).add(any());
        verify(storageService, times(0)).updateById(any(), anyLong());
        verify(storageService, times(1)).delete(eq(errorId));
    }

    public void existingErrorUpdated() {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        List<QueueEventImpl> events = createEvents();
        context.setEvents(events);
        QueueEvent event = events.get(0);
        long errorId = 15L;
        context.setExistingErrors(Map.of(event.getEventId(), createError(event, errorId)));
        context.setFailedResults(Set.of(failure(event.getEventId(), "err", false)));

        errorService.processErrors(persistentQueue, context);

        ArgumentCaptor<NodeEventsErrorInfo> captor = ArgumentCaptor.forClass(NodeEventsErrorInfo.class);
        verify(storageService, times(0)).add(any());
        verify(storageService, times(1)).updateById(captor.capture(), anyLong());
        verify(storageService, times(0)).delete(anyLong());
        NodeEventsErrorInfo errorInfo = captor.getValue();
        Assert.assertEquals(Objects.requireNonNull(errorInfo.getEventId()).longValue(), event.getEventId());
        Assert.assertEquals(errorInfo.getPath().toPath(), event.getPath());
        Assert.assertEquals(errorInfo.getEventType(), event.getType());
        Assert.assertEquals(errorInfo.getFirstErrorTime(), 50L);
        Assert.assertEquals(errorInfo.getErrorCount(), 2);
    }

    public void existingErrorExhausted() {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        List<QueueEventImpl> events = createEvents();
        context.setEvents(events);
        QueueEvent event = events.get(0);
        long errorId = 15L;
        context.setExistingErrors(Map.of(event.getEventId(), createError(event, errorId)));
        context.setFailedResults(Set.of(failure(event.getEventId(), "err", false)));

        //set max retires to 1
        QueueConfiguration queueConfiguration = createIncrementalIndexQueue((e, k) -> {}, 1);
        when(persistentQueue.getQueueConfiguration()).thenReturn(queueConfiguration);

        errorService.processErrors(persistentQueue, context);
        Assert.assertTrue(context.getExhaustedErrors().contains(event.getEventId()));

        verify(storageService, times(0)).add(any());
        verify(storageService, times(0)).updateById(any(), anyLong());
        verify(storageService, times(1)).delete(errorId);
    }

    public void selfManagedErrorsShouldNotBeDeleted() {
        PersistentQueueRunningContext context = new PersistentQueueRunningContext();
        List<QueueEventImpl> events = createEvents();
        context.setEvents(events);
        QueueEvent event = events.get(0);
        long errorId = 15L;
        context.setExistingErrors(Map.of(event.getEventId(), createError(event, errorId)));
        context.setFailedResults(Set.of(failure(event.getEventId(), "err", false)));

        //set max retires to 1
        QueueConfiguration queueConfiguration = createFederatedReplicationQueue((e, k) -> {});
        when(persistentQueue.getQueueConfiguration()).thenReturn(queueConfiguration);

        errorService.processErrors(persistentQueue, context);
        Assert.assertTrue(context.getExhaustedErrors().contains(event.getEventId()));

        verify(storageService, times(0)).add(any());
        verify(storageService, times(1)).updateById(any(), anyLong());
        verify(storageService, times(0)).delete(errorId);
    }

    public void deleteErrors() {
        errorService.deleteErrors(persistentQueue);
        verify(storageService).deleteAll(eq(QueueCursorTypeMapper.get(queueConfiguration.getCursorType()).getName()),
                eq("name"));
    }

    public void triggerRecovery() {
        EventAndErrorInfo errorInfo =
                EventAndErrorInfo.builder().eventInfo(new EventInfo(create, "path")).errorCount(2).build();
        when(errorService.getErrors(persistentQueue)).thenReturn(List.of(errorInfo));

        errorService.triggerRecovery(persistentQueue);

        ArgumentCaptor<NodeEventsErrorInfo> captor =
                ArgumentCaptor.forClass(NodeEventsErrorInfo.class);
        verify(storageService, times(1)).updateById(captor.capture(), anyLong());
        NodeEventsErrorInfo value = captor.getValue();
        assertEquals(0, value.getErrorCount());
    }

    private EventAndErrorInfo createError(QueueEvent event, long errorId) {
        return EventAndErrorInfo.builder()
                .eventInfo(((QueueEventImpl) event).getEvent())
                .errorId(errorId)
                .firstErrorTime(50)
                .errorCount(1)
                .build();
    }

    @Nonnull
    private List<QueueEventImpl> createEvents() {
        return List.of(
                new QueueEventImpl(new EventInfo(1, 100, create, "path1/", null)),
                new QueueEventImpl(new EventInfo(2, 150, create, "path2/", null))
        );
    }

}
