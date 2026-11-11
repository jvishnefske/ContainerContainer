package org.artifactory.queue;

import ch.qos.logback.classic.Level;
import jakarta.annotation.Nonnull;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.event.queue.QueueEventException;
import org.artifactory.queue.monitor.PersistentQueueRunnerReport;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueueImpl;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.test.TestUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.queue.services.PersistentQueue.TriggerResult.FINISHED_WITH_UNHANDLED_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Test
public class PersistentQueueRunnerReportTest extends PersistentQueueBaseTest {

    NodeEventCursor nodeEventCursor;
    PersistentQueueRunnerReport persistentQueueRunnerReport;

    @BeforeClass
    public void before() {
        TestUtils.setLoggingLevel(PersistentQueueRunnerReport.class, Level.DEBUG);
    }

    @BeforeClass
    public void init() {
        nodeEventCursor = mock(NodeEventCursor.class);
    }

    @AfterClass
    public void after() {
        TestUtils.setLoggingLevel(PersistentQueueRunnerReport.class, Level.ERROR);
    }

    public void emptyEvents() {
        events(Lists.newArrayList());
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(nodeEventCursor);
        persistentQueue.trigger();
    }

    public void finishedAllEvents() {
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue();
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(nodeEventCursor);
        persistentQueue.trigger();
    }

    public void finishedWithRuntimeException() {
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue(((events, key) -> {
            throw new RuntimeException("unhandled error");
        }), 6);
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(nodeEventCursor);
        persistentQueue.trigger();
    }

    public void finishedWithQueueEventException() {
        events(createDemoEvents());
        QueueConfiguration configuration = createIncrementalIndexQueue(((events, key) -> {
            for (QueueEvent event : events) {
                throw new QueueEventException(event, "fail on purpose");
            }
        }), 6);
        PersistentQueue persistentQueue = new PersistentQueueImpl(configuration, "repo1");
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(nodeEventCursor);
        persistentQueue.trigger();
    }

    @Nonnull
    private List<QueueEvent> createDemoEvents() {
        List<QueueEvent> events = Lists.newArrayList();
        events.add(new QueueEventImpl(new EventInfo(1, 100, EventType.create, "repo1/a/b/c/", null)));
        events.add(new QueueEventImpl(new EventInfo(2, 100, EventType.update, "repo1/a/b/c/", null)));
        events.add(new QueueEventImpl(new EventInfo(3, 101, EventType.update, "repo1/a/b/c/", null)));
        events.add(new QueueEventImpl(new EventInfo(5, 101, EventType.props, "repo1/a/b/c/", null)));
        events.add(new QueueEventImpl(new EventInfo(6, 102, EventType.delete, "repo1/a/b/c/", null)));
        events.add(new QueueEventImpl(new EventInfo(7, 103, EventType.create, "repo1/a/b/c/", null)));
        return events;
    }

    public void fullReport() {
        List<QueueEvent> demoEvents = createDemoEvents();
        List<QueueEvent> afterFilter = new ArrayList<>();
        afterFilter.add(demoEvents.get(0));
        afterFilter.add(demoEvents.get(2));
        afterFilter.add(demoEvents.get(4));
        afterFilter.add(demoEvents.get(5));
        Set<Long> includedErrors = Set.of(3L, 5L, 7L);
        PersistentQueue persistentQueue = mock(PersistentQueue.class);
        when(persistentQueue.getName()).thenReturn("testingReport");
        persistentQueueRunnerReport = new PersistentQueueRunnerReport();
        persistentQueueRunnerReport.start(persistentQueue);
        persistentQueueRunnerReport.startMarker(persistentQueue.getName(), demoEvents.get(0).getEventId());
        persistentQueueRunnerReport.loadedEvents(persistentQueue.getName(), demoEvents);
        persistentQueueRunnerReport.includedErrors(persistentQueue.getName(), includedErrors);
        persistentQueueRunnerReport.addFilterEvents(persistentQueue.getName(), demoEvents, afterFilter);
        persistentQueueRunnerReport.afterImplementor(persistentQueue.getName(), "group1",
                new QueueEventException(demoEvents.get(4), "failure1"));
        persistentQueueRunnerReport.afterImplementor(persistentQueue.getName(), "group2",
                new InterruptedException("interrupted"));
        persistentQueueRunnerReport.afterImplementor(persistentQueue.getName(), "group3",
                new QueueEventException(demoEvents.get(5), "failure2"));
        persistentQueueRunnerReport.endMarker(persistentQueue.getName(), demoEvents.get(3).getEventId());
        persistentQueueRunnerReport.end(persistentQueue.getName(), FINISHED_WITH_UNHANDLED_ERROR);
    }

    private void events(List<QueueEvent> result) {
        when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString())).thenReturn(
                result.stream().map(e -> new EventInfo(e.getEventId(), e.getTimestamp(), e.getType(), e.getPath(),
                        e.getHints())).collect(Collectors.toList()));
    }

}
