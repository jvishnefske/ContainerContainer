package org.artifactory.queue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.annotation.Nonnull;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.compress.utils.Sets;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.event.queue.QueueEventException;
import org.artifactory.event.queue.QueueReductionBehaviours;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.error.PersistentQueueErrorServiceImpl;
import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.queue.monitor.PersistentQueueMonitor;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueueCursorInfo;
import org.artifactory.queue.services.PersistentQueueImpl;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.sh.cleanup.events.StorageEventsService;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.error.model.EventAndErrorInfo;
import org.artifactory.storage.db.event.error.model.NodeEventsErrorInfo;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.queue.services.PersistentQueue.TriggerResult.FINISHED_ALL;
import static org.artifactory.queue.services.PersistentQueue.TriggerResult.FINISHED_WITH_HANDLED_ERROR;
import static org.artifactory.queue.services.PersistentQueue.TriggerResult.FINISHED_WITH_UNHANDLED_ERROR;
import static org.artifactory.queue.services.PersistentQueue.TriggerResult.NOTHING_TO_DO;
import static org.artifactory.queue.services.PersistentQueue.TriggerResult.NOT_AVAILABLE;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

@Test
public class PersistentQueueTest extends PersistentQueueBaseTest {
    
    ListAppender<ILoggingEvent> listAppender;
    
    @BeforeMethod
    void setup() {
        Logger logger = (Logger) LoggerFactory.getLogger(PersistentQueueImpl.class);
        logger.setLevel(Level.TRACE);
        listAppender = new ListAppender<>();
        listAppender.start();
    }
    
    @AfterMethod
    void clean() {
        listAppender.stop();
    }
    public void triggerAfterShutdown() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        persistentQueue.shutdown();
        triggerAndAssert(persistentQueue, NOT_AVAILABLE);
    }

    public void pauseAndProceed() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        assertThat(persistentQueue.isPaused()).isEqualTo(false);
        triggerAndAssert(persistentQueue, NOTHING_TO_DO);
        persistentQueue.pause();
        assertThat(persistentQueue.isPaused()).isEqualTo(true);
        triggerAndAssert(persistentQueue, NOT_AVAILABLE);
        persistentQueue.proceed();
        verify(artifactoryContext.beanForType(QueueTriggeringService.class), times(1))
                .triggerQueues(anyString(), any(QueueCursorType.class));
        assertThat(persistentQueue.isPaused()).isEqualTo(false);
        triggerAndAssert(persistentQueue, NOTHING_TO_DO);
    }

    public void emptyEvents() {
        events(Lists.newArrayList());
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, NOTHING_TO_DO);
    }

    public void finishedAllEvents() {
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_ALL);
    }

    public void exceptionInHandlingEvents() {
        PersistentQueueMonitor monitor = mock(PersistentQueueMonitor.class);
        Map<String, PersistentQueueMonitor> monitorMap = Map.of("key", monitor);
        when(artifactoryContext.beansForType(PersistentQueueMonitor.class)).thenReturn(monitorMap);
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = spy(new PersistentQueueImpl(configuration, "repo1"));
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenThrow(NullPointerException.class);

        doAnswer(invocation -> {
            PersistentQueue.TriggerResult triggerResult = invocation.getArgument(1);
            assertThat(triggerResult).isEqualTo(FINISHED_WITH_UNHANDLED_ERROR);
            return null;
        }).when(monitor).end(any(), any());

        assertThrows(NullPointerException.class, persistentQueue::trigger);
    }

    /**
     * Verify the use case when all events that loaded from the db filtered: Marker must be promoted
     */
    public void allEventsFilteredCursorUpdated() {
        List<EventInfo> demoEvents = createDemoEvents();
        events(demoEvents);
        events(createDemoEvents());
        QueueConfiguration configuration = QueueConfiguration.builder()
                .name("test")
                .filter(event -> false)
                .implementor((events, key) -> {})
                .cursorType(QueueCursorType.INCREMENTAL_INDEX)
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");

        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );

        //Verify NOTHING_TO_DO
        triggerAndAssert(persistentQueue, FINISHED_ALL);
        //Verify marker updated
        verify(queueCursorService, times(1))
                .updateCursor(anyString(), eq(demoEvents.get(demoEvents.size() - 1).getEventId()));
    }

    /**
     * Verify the use case when last loaded event from the db filtered: Marker must be promoted
     */
    public void lastEventFilteredCursorUpdated() {
        List<EventInfo> demoEvents = Lists.newArrayList();
        demoEvents.add(new EventInfo(1, 100, EventType.create, "repo1/a/b/c/", null));
        demoEvents.add(new EventInfo(2, 100, EventType.create, "repo1/a/b/d/", Sets.newHashSet("cyclic event")));
        events(demoEvents);
        QueueConfiguration configuration = QueueConfiguration.builder()
                .filter(event -> event.getHints() == null || !event.getHints().contains("cyclic event"))
                .implementor((events, key) -> {})
                .cursorType(QueueCursorType.INCREMENTAL_INDEX)
                .name("test")
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );

        //Verify NOTHING_TO_DO
        triggerAndAssert(persistentQueue, FINISHED_ALL);
        //Verify marker updated
        verify(queueCursorService, times(1))
                .updateCursor(anyString(), eq(demoEvents.get(demoEvents.size() - 1).getEventId()));
    }

    /**
     * Verify the use case when only errors loaded and success: marker should not be promoted
     */
    public void onlyErrorsAndSuccessCursorUpdated() {
        events(Lists.newArrayList());

        List<EventAndErrorInfo> errors = Lists.newArrayList();
        EventAndErrorInfo error = EventAndErrorInfo.builder()
                .eventInfo(new EventInfo(10, 100, EventType.create, "repo1/a/b/c/", null))
                .errorId(1111)
                .errorCount(1)
                .firstErrorTime(105)
                .build();
        errors.add(error);

        when(queueErrorService.getErrors(any(PersistentQueue.class))).thenReturn(errors);

        QueueConfiguration configuration = QueueConfiguration.builder()
                .errorOverflowLimit(1)
                .filter(event -> event.getHints() == null || !event.getHints().contains("cyclic event"))
                .implementor((events, key) -> {})
                .cursorType(QueueCursorType.INCREMENTAL_INDEX)
                .name("test")
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");

        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );

        //Verify FINISHED_ALL
        triggerAndAssert(persistentQueue, FINISHED_ALL);
        //Verify marker updated
        verify(queueCursorService, times(0)).updateCursorStatus(anyString(), eq(NodeEventCursorStatus.OUT_OF_SYNC));
        verify(queueCursorService, times(1)).updateCursor(anyString(), anyLong());
    }

    /**
     * Verify the use case when only errors loaded and failures: marker should not be promoted
     */
    public void onlyErrorsAndFailureCursorNotUpdated() {
        events(Lists.newArrayList());

        List<EventAndErrorInfo> errors = Lists.newArrayList();
        EventAndErrorInfo error = EventAndErrorInfo.builder()
                .eventInfo(new EventInfo(10, 100, EventType.create, "repo1/a/b/c/", null))
                .errorId(1111)
                .errorCount(1)
                .firstErrorTime(105)
                .build();
        errors.add(error);

        when(queueErrorService.getErrors(any(PersistentQueue.class))).thenReturn(errors);

        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(100L)
                        .lastUpdated(1L)
                        .build()
                );

        QueueConfiguration configuration = QueueConfiguration.builder()
                .filter(event -> event.getHints() == null || !event.getHints().contains("cyclic event"))
                .implementor((events, key) -> {
                    throw new QueueEventException(events.get(0), "something went wrong");
                })
                .name("test")
                .cursorType(QueueCursorType.INCREMENTAL_INDEX)
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");


        //Verify FINISHED_WITH_HANDLED_ERROR
        triggerAndAssert(persistentQueue, FINISHED_WITH_HANDLED_ERROR);
        //Verify marker updated
        verify(queueCursorService, never()).updateCursor(any(), anyLong());
    }

    public void eventsAndErrors() {
        //this event has error, it is not returned since the marker passed it ==> it should be handled from the error
        EventInfo event0 = new EventInfo(5, 5, EventType.update, "repo1/a/b/e/", null);
        //this event has error, the marker did not passed it, it is returned from events and errors => should be handled
        EventInfo event1 = new EventInfo(10, 100, EventType.create, "repo1/a/b/c/", null);
        //this event has error, it should be eliminated by backoff
        EventInfo event2 = new EventInfo(20, System.currentTimeMillis(), EventType.props, "repo1/a/b/d/",
                null);
        //this event is on the path like event 1, it should not be eliminated and should be handled due to dependency
        EventInfo event3 = new EventInfo(30, 110, EventType.delete, "repo1/a/b/c/", null);
        //events 4-5 are on the same path, one should be de-duped, event 5 should be used since it is latest
        EventInfo event4 = new EventInfo(40, 120, EventType.update, "repo1/a/b/f/", null);
        EventInfo event5 = new EventInfo(50, 150, EventType.update, "repo1/a/b/f/", null);

        EventAndErrorInfo eventError1 = EventAndErrorInfo.builder()
                .eventInfo(event1)
                .errorId(1111)
                .errorCount(1)
                .firstErrorTime(105)
                .build();
        EventAndErrorInfo eventError2 = EventAndErrorInfo.builder()
                .eventInfo(event2)
                .errorId(1112)
                .errorCount(1)
                .firstErrorTime(event2.getTimestamp())
                .build();
        EventAndErrorInfo eventError3 = EventAndErrorInfo.builder()
                .eventInfo(event0)
                .errorId(1113)
                .errorCount(1)
                .firstErrorTime(5)
                .build();

        when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString()))
                .thenReturn(Stream.of(event1, event2, event3, event4, event5).collect(Collectors.toList()));
        when(queueErrorService.getErrors(any(PersistentQueue.class)))
                .thenReturn(Stream.of(eventError1, eventError2, eventError3).collect(Collectors.toList()));

        QueueConfiguration configuration = QueueConfiguration.builder()
                .name("test")
                .errorOverflowLimit(1)
                .implementor((events, key) -> {})
                .cursorType(QueueCursorType.FEDERATED_REPLICATION)
                .reductionBehaviours(QueueReductionBehaviours.FederatedReplication)
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");

        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(100L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_ALL);

        ArgumentCaptor<PersistentQueueRunningContext> runningContext =
                ArgumentCaptor.forClass(PersistentQueueRunningContext.class);
        verify(queueErrorService, times(1)).processErrors(any(PersistentQueue.class),
                runningContext.capture());
        verify(queueCursorService, never()).updateCursorStatus(anyString(), eq(NodeEventCursorStatus.OUT_OF_SYNC));
        assertThat(runningContext.getValue().getEvents().size()).isEqualTo(3);
        assertThat(runningContext.getValue().getEvents().get(0).getEventId()).isEqualTo(5);
        assertThat(runningContext.getValue().getEvents().get(1).getEventId()).isEqualTo(20);
        assertThat(runningContext.getValue().getEvents().get(2).getEventId()).isEqualTo(50);
    }

    public void testFedReductionBehaviourCreateEliminateDeleteBehaviorWithError() throws Exception {
        EventInfo deleteEvent = new EventInfo(5, 5, EventType.delete, "repo1/a/b/e/", null);
        EventInfo createEvent = new EventInfo(10, 10, EventType.create, "repo1/a/b/e/", null);

        PersistentQueueErrorServiceImpl errorServiceImpl =
                spy(new PersistentQueueErrorServiceImpl(this.errorsStorageService, null));

        when(artifactoryContext.beanForType(eq(PersistentQueueErrorService.class))).thenReturn(errorServiceImpl);

        when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString()))
                .thenReturn(Stream.of(deleteEvent, createEvent).collect(Collectors.toList()));

        doReturn(Collections.emptyList()).when(errorServiceImpl).getErrors(any(PersistentQueue.class));

        QueueConfiguration configuration = QueueConfiguration.builder()
                .implementor((events, key) -> {
                    for (QueueEvent event : events) {
                        throw new QueueEventException(event, "event error", false);
                    }
                })
                .cursorType(QueueCursorType.FEDERATED_REPLICATION)
                .reductionBehaviours(QueueReductionBehaviours.FederatedReplication)
                .name("test")
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_WITH_HANDLED_ERROR);
        ArgumentCaptor<NodeEventsErrorInfo> captor = ArgumentCaptor.forClass(NodeEventsErrorInfo.class);
        verify(errorsStorageService, times(1)).add(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(10);
        verify(queueCursorService, times(1))
                .updateCursor(anyString(), eq(createEvent.getEventId()));
    }

    @DataProvider
    private Object[][] testFedReductionBehaviourDeleteEliminateUpdateOrCreateBehaviorDataProvider() {
        return new Object[][] {
                {new EventInfo(5, 5, EventType.create, "repo1/a/b/e/", null)},
                {new EventInfo(5, 5, EventType.update, "repo1/a/b/e/", null)}
        };
    }

    @Test(dataProvider = "testFedReductionBehaviourDeleteEliminateUpdateOrCreateBehaviorDataProvider")
    public void testFedReductionBehaviourDeleteEliminateUpdateOrCreateBehavior(EventInfo createOrUpdateEvent)
            throws Exception {
        EventInfo deleteEvent = new EventInfo(10, 10, EventType.delete, "repo1/a/b/e/", null);

        PersistentQueueErrorServiceImpl errorServiceImpl =
                spy(new PersistentQueueErrorServiceImpl(this.errorsStorageService, null));

        when(artifactoryContext.beanForType(eq(PersistentQueueErrorService.class))).thenReturn(errorServiceImpl);

        when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString()))
                .thenReturn(Stream.of(createOrUpdateEvent, deleteEvent).collect(Collectors.toList()));

        doReturn(Collections.emptyList()).when(errorServiceImpl).getErrors(any(PersistentQueue.class));

        QueueConfiguration configuration = QueueConfiguration.builder()
                .implementor((events, key) -> {
                    Assert.fail();
                })
                .name("test")
                .cursorType(QueueCursorType.FEDERATED_REPLICATION)
                .reductionBehaviours(QueueReductionBehaviours.FederatedReplication)
                .build();
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_ALL);

        verify(queueCursorService, times(1))
                .updateCursor(anyString(), eq(deleteEvent.getEventId()));
    }

    public void testOmitError() {
        EventInfo deleteEvent = new EventInfo(5, 5, EventType.delete, "repo1/a/b/e/", null);

        PersistentQueueErrorServiceImpl errorServiceImpl =
                spy(new PersistentQueueErrorServiceImpl(this.errorsStorageService, null));

        when(artifactoryContext.beanForType(eq(PersistentQueueErrorService.class))).thenReturn(errorServiceImpl);
        doReturn(Collections.emptyList()).when(errorServiceImpl).getErrors(any(PersistentQueue.class));

        when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString()))
                .thenReturn(Stream.of(deleteEvent).collect(Collectors.toList()));


        QueueConfiguration configuration = QueueConfiguration.builder()
                .implementor((events, key) -> {
                    for (QueueEvent event : events) {
                        throw new QueueEventException(event, "delete event error", true);
                    }
                })
                .name("test")
                .cursorType(QueueCursorType.FEDERATED_REPLICATION)
                .reductionBehaviours(QueueReductionBehaviours.FederatedReplication)
                .build();

        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_WITH_HANDLED_ERROR);

        verify(errorsStorageService, never()).add(any(NodeEventsErrorInfo.class));
        verify(queueCursorService, times(1))
                .updateCursor(anyString(), eq(deleteEvent.getEventId()));
    }

    public void finishedWithRuntimeException() {
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue(((events, key) -> {
            throw new RuntimeException("unhandled error");
        }), 6);
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_WITH_UNHANDLED_ERROR);
    }

    public void finishedWithQueueEventException() {
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue(((events, key) -> {
            for (QueueEvent event : events) {
                throw new QueueEventException(event, "fail on purpose");
            }
        }), 6);
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_WITH_HANDLED_ERROR);
    }

    public void finishedWithQueueEventExceptionAndErrorExhausted() {
        doAnswer(invocation -> {
            // make the failed event as exhausted error
            PersistentQueueRunningContext context = invocation.getArgument(1, PersistentQueueRunningContext.class);
            context.getExhaustedErrors().add(context.getFailedResults().iterator().next().getFailedMarker());
            return null;
        }).when(queueErrorService).processErrors(any(PersistentQueue.class), any(PersistentQueueRunningContext.class));
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue(((events, key) -> {
            for (QueueEvent event : events) {
                throw new QueueEventException(event, "fail on purpose");
            }
        }), 6);
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");

        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_ALL);
    }

    public void finishedWithQueueEventExceptionWhenErrorSupportIsDisabled() {
        events(createDemoEvents());
        QueueConfiguration configuration = getIncrementalIndexBuilder()
                .enableErrorSupport(false)
                .implementor((events, key) -> {
                    throw new QueueEventException(events.get(0), "fail on purpose but error support is disabled");
                })
                .build();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_WITH_UNHANDLED_ERROR);
    }

    public void finishedWithQueueExhausted() {
        events(createDemoEvents());
        QueueConfiguration configuration = getFederatedReplicationBuilder().implementor((events, key) -> {
            throw new QueueEventException(events.get(0), "fail on purpose");
        }).build();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any()))
                .thenReturn(NodeEventCursor.builder()
                        .eventMarker(-1L)
                        .lastUpdated(1L)
                        .status(NodeEventCursorStatus.EXHAUSTED)
                        .build()
                );
        triggerAndAssert(persistentQueue, FINISHED_WITH_UNHANDLED_ERROR);
    }

    public void resetQueue() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        persistentQueue.reset(true);
        verify(queueCursorService).deleteMarker(eq(persistentQueue.getName()));
        verify(queueErrorService).deleteErrors(any(PersistentQueue.class));
        reset(queueCursorService, queueErrorService);
        persistentQueue.reset();
        verify(queueCursorService).resetMarker(eq(persistentQueue.getName()),
                eq(-1L));
        verify(queueErrorService).deleteErrors(any(PersistentQueue.class));
    }

    public void testMarkAsHealthy() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        persistentQueue.updateStatus(NodeEventCursorStatus.HEALTHY);
        String cursorName = new PersistentQueueCursorInfo("repo1",
                configuration.getCursorType(),
                configuration.getVariant()
        ).getCursorName();
        verify(queueCursorService, times(1)).updateCursorStatus(cursorName, NodeEventCursorStatus.HEALTHY);
    }

    /**
     * Test that verify the structure of federated cursor.
     * The structure should be: X-federated_replicationvariant-cursor
     * for queries done on db for monitoring feature
     */
    public void testVerifyStructureOfFederatedCursor() {
        String cursorName = new PersistentQueueCursorInfo("repo1",
                QueueCursorType.FEDERATED_REPLICATION,
                "http://url/artifactory/repo2"
        ).getCursorName();
        Assert.assertTrue(cursorName.contains("/artifactory/"));
        Assert.assertTrue(
                cursorName.startsWith("repo1-" + QueueCursorType.FEDERATED_REPLICATION.toString().toLowerCase()));
        Assert.assertTrue(cursorName.endsWith("-cursor"));
    }


    @Test
    public void testResetStatus() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo");
        persistentQueue.resetStatus();
        verify(queueCursorService).resetMarker(anyString(), eq(-1L));
        verify(queueErrorService).deleteErrors(eq(persistentQueue));
    }

    @Test
    public void testReset() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo");
        long id = 123L;
        persistentQueue.reset(persistentQueue.getRepoKey(), id);
        verify(queueCursorService).resetMarker(anyString(), eq(id));
        verify(queueErrorService).deleteErrors(eq(persistentQueue));
    }

    @Test
    public void testShouldAlwaysTrigger() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo");
        assertThat(persistentQueue.shouldAlwaysTrigger()).isFalse();
    }

    public void createMarkerIfNotExists() {
        // Given
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        // When
        persistentQueue.createMarkerIfNotExists();
        // Then
        verify(queueCursorService, times(1)).createMarkerIfNotExists(eq(persistentQueue.getRepoKey()),
                anyString(), eq(configuration.getCursorType()));
    }

    public void testResetCursorToLastEvent() {
        NodeEventCursor mockCursor = NodeEventCursor.builder()
                .eventMarker(2L)
                .lastUpdated(1L)
                .build();
        events(Collections.emptyList());
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo2");
        when(queueCursorService.getCachedLatestEventId()).thenReturn(5L);
        when(eventsService.getLastEventLogId()).thenReturn(5L);
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(mockCursor);
        triggerAndAssert(persistentQueue, NOTHING_TO_DO);
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(queueCursorService, times(1)).updateCursor(anyString(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(5L);
    }

    public void testNoResetGcCursorToLastEvent() {
        NodeEventCursor mockCursor = NodeEventCursor.builder()
                .eventMarker(2L)
                .lastUpdated(1L)
                .build();
        events(Collections.emptyList());
        QueueConfiguration configuration = spy(createIncrementalIndexQueue());
        doReturn(QueueCursorType.GC).when(configuration).getCursorType();
        final StorageEventsService storageEventsService = mock(StorageEventsService.class);
        when(artifactoryContext.beanForType(StorageEventsService.class)).thenReturn(storageEventsService);
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo2");
        when(queueCursorService.getCachedLatestEventId()).thenReturn(5L);
        when(eventsService.getLastEventLogId()).thenReturn(5L);
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(mockCursor);
        triggerAndAssert(persistentQueue, NOTHING_TO_DO);
        verify(queueCursorService, times(0)).updateCursor(anyString(), anyLong());
    }

    @Nonnull
    private List<EventInfo> createDemoEvents() {
        List<EventInfo> events = Lists.newArrayList();
        events.add(new EventInfo(1, 100, EventType.create, "repo1/a/b/c/", null));
        return events;
    }

    private void triggerAndAssert(PersistentQueue persistentQueue, PersistentQueue.TriggerResult nothingToDo) {
        assertThat(persistentQueue.trigger()).isEqualTo(nothingToDo);
    }

    private void events(List<EventInfo> result) {
        when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString())).thenReturn(result);
    }
}
