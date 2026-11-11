package org.artifactory.queue.monitor;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.jfrog.test.log.MemoryAppender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.commons.compress.utils.Lists;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueEvent;
import org.artifactory.event.queue.QueueEventException;
import org.artifactory.model.RepoConfig;
import org.artifactory.queue.QueueEventImpl;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentQueueMetricsMonitorTest extends ArtifactoryHomeBoundJUnitTest {
    private static final String CONFIGURATION_NAME = "Configuration name";
    private static final String QUEUE_LABEL = "configuration name";

    PersistentQueueMetricsMonitor persistentQueueMetricsMonitor;
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    @Mock
    PersistentQueue persistentQueue;
    @Mock
    QueueConfiguration queueConfiguration;
    @Mock
    RepositoryService repositoryService;
    MemoryAppender memoryAppender;


    @BeforeEach
    void setup() {
        persistentQueueMetricsMonitor = spy(new PersistentQueueMetricsMonitor(meterRegistry, repositoryService));
        lenient().when(queueConfiguration.getName()).thenReturn(CONFIGURATION_NAME);
        lenient().when(persistentQueue.getQueueConfiguration()).thenReturn(queueConfiguration);
        lenient().when(persistentQueue.getName()).thenReturn("pqname");

        setupLoggingAssertions();
    }

    @ParameterizedTest
    @EnumSource(EventType.class)
    void testReportEvent(EventType eventType) {
        String repoName = "docker-local";
        EventInfo eventInfo = mock(EventInfo.class);
        when(eventInfo.getType()).thenReturn(eventType);
        when(eventInfo.getPath()).thenReturn(repoName + "/alpine/v1/manifest.json");
        RepoConfig config = mock(RepoConfig.class);
        when(config.getPackageType()).thenReturn(RepoType.Docker);
        when(config.getRepoType()).thenReturn(RepoDetailsType.LOCAL);
        when(repositoryService.getRepoConfigByKey(repoName)).thenReturn(config);
        persistentQueueMetricsMonitor.reportEvent(eventInfo);
        assertEquals(1, meterRegistry.get("pq.events.queued").tag("type", eventType.name()).counter().count());
    }

    @ParameterizedTest
    @EnumSource(PersistentQueue.TriggerResult.class)
    void testTrigger(PersistentQueue.TriggerResult triggerResult) {
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(1L)  // start time
                .thenReturn(21L);  // end time
        persistentQueueMetricsMonitor.setCurrentTimeSupplier(currentTimeSupplier);
        persistentQueueMetricsMonitor.start(persistentQueue);
        persistentQueueMetricsMonitor.end(persistentQueue.getName(), triggerResult);
        Tags expectedTags = Tags.of("queue", QUEUE_LABEL, "result", triggerResult.name());
        Timer timerTotal = meterRegistry.find("pq.events.bulk.consume.duration").tags(expectedTags).timer();
        assertNotNull(timerTotal);
        assertEquals(1.0, timerTotal.count());
        assertEquals(20.0, timerTotal.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(20.0, timerTotal.max(TimeUnit.MILLISECONDS));
    }

    @Test
    void testBeforeAndAfterImplementor() {
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong())
                .thenReturn(1L)  // start time, not relevant
                .thenReturn(1L)  // before implementor
                .thenReturn(2L)  // before implementor
                .thenReturn(3L)  // before implementor
                .thenReturn(4L)  // before implementor
                .thenReturn(5L)  // after implementor
                .thenReturn(6L)  // after implementor
                .thenReturn(7L)  // after implementor
                .thenReturn(8L);  // after implementor
        persistentQueueMetricsMonitor.setCurrentTimeSupplier(currentTimeSupplier);
        persistentQueueMetricsMonitor.start(persistentQueue);
        persistentQueueMetricsMonitor.beforeImplementor(persistentQueue.getName(), "group1", 1);
        persistentQueueMetricsMonitor.beforeImplementor(persistentQueue.getName(), "group2", 3);
        persistentQueueMetricsMonitor.beforeImplementor(persistentQueue.getName(), "group3", 5);
        persistentQueueMetricsMonitor.beforeImplementor(persistentQueue.getName(), "group4", 10);
        persistentQueueMetricsMonitor.afterImplementor(persistentQueue.getName(), "group1",
                new QueueEventException(mock(QueueEvent.class), "failure1"));
        persistentQueueMetricsMonitor.afterImplementor(persistentQueue.getName(), "group2",
                new RuntimeException("runtime"));
        persistentQueueMetricsMonitor.afterImplementor(persistentQueue.getName(), "group3",
                new QueueEventException(mock(QueueEvent.class), "failure2"));
        persistentQueueMetricsMonitor.afterImplementor(persistentQueue.getName(), "group4", null);
        Timer group4Timer =
                meterRegistry.find("pq.events.bulk.process.duration").tags(getTagsForImplementor("10+", ""))
                        .timer();
        Timer group3Timer = meterRegistry.find("pq.events.bulk.process.duration")
                .tags(getTagsForImplementor("2-9", "QueueEventException")).timer();
        Timer group2Timer = meterRegistry.find("pq.events.bulk.process.duration")
                .tags(getTagsForImplementor("2-9", "RuntimeException")).timer();
        Timer group1Timer = meterRegistry.find("pq.events.bulk.process.duration")
                .tags(getTagsForImplementor("1", "QueueEventException")).timer();
        List<Timer> timers = List.of(group1Timer, group2Timer, group3Timer, group4Timer);
        for (Timer timerTotal : timers) {
            assertNotNull(timerTotal);
            assertEquals(1.0, timerTotal.count());
            assertEquals(4.0, timerTotal.totalTime(TimeUnit.MILLISECONDS));
            assertEquals(4.0, timerTotal.max(TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void testLoadEvents() {
        List<QueueEvent> loadedEvents = new ArrayList<>();
        loadedEvents.add(new QueueEventImpl(new EventInfo(1, 100, EventType.create, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(2, 101, EventType.update, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(3, 102, EventType.update, "repo1/a/b/c/", null)));
        LongSupplier currentTimeSupplier = mock(LongSupplier.class);
        when(currentTimeSupplier.getAsLong()).thenReturn(103L);
        persistentQueueMetricsMonitor.setCurrentEpochTimeSupplier(currentTimeSupplier);
        persistentQueueMetricsMonitor.start(persistentQueue);
        persistentQueueMetricsMonitor.loadedEvents(persistentQueue.getName(), loadedEvents);
        Timer timerTotal = meterRegistry.find("pq.events.pending.duration").tags("queue", QUEUE_LABEL).timer();
        assertNotNull(timerTotal);
        assertEquals(loadedEvents.size(), timerTotal.count());
        assertEquals(6.0, timerTotal.totalTime(TimeUnit.MILLISECONDS));
        assertEquals(3.0, timerTotal.max(TimeUnit.MILLISECONDS));
        assertEquals(loadedEvents.size(),
                meterRegistry.get("pq.events.consumed").tag("queue", QUEUE_LABEL).counter().count());
        for (QueueEvent event : loadedEvents) {
            String msg = String.format(
                    "The event with id:'%s', path:'%s'", event.getEventId(), event.getPath());
            Assertions.assertEquals(0, memoryAppender.search(msg, Level.DEBUG).size());
        }
    }

    @ParameterizedTest
    @MethodSource("testLoadEventsLogLongPendingProvider")
    void testLoadEventsLogLongPending(long threshold, String configurationListStr, boolean shouldLog) {
        long originalThreshold = ConstantValues.persistentQueueLagThresholdMilis.getLong();
        String originalConfigurationListStr = ConstantValues.persistentQueueLagConfigurationNameFilterList.getString();
        try {
            setLongSystemProperty(ConstantValues.persistentQueueLagThresholdMilis, threshold);
            setStringSystemProperty(ConstantValues.persistentQueueLagConfigurationNameFilterList, configurationListStr);
            long currentTime = threshold + 1000L;
            List<QueueEvent> loadedEvents = new ArrayList<>();
            loadedEvents.add(new QueueEventImpl(new EventInfo(1, 1, EventType.create, "repo1/a/b/c/", null)));
            loadedEvents.add(new QueueEventImpl(new EventInfo(2, 100, EventType.update, "repo1/a/b/c/", null)));
            LongSupplier currentTimeSupplier = mock(LongSupplier.class);
            when(currentTimeSupplier.getAsLong()).thenReturn(currentTime);
            when(persistentQueue.getName()).thenReturn("queueName");
            persistentQueueMetricsMonitor.setCurrentEpochTimeSupplier(currentTimeSupplier);
            persistentQueueMetricsMonitor.start(persistentQueue);
            persistentQueueMetricsMonitor.loadedEvents(persistentQueue.getName(), loadedEvents);
            for (QueueEvent event : loadedEvents) {
                String msg = String.format(
                        "The event with id:'%s', path:'%s', timestamp:'%s', type:'%s' took '%s' ms to be consumed for" +
                                " " +
                                "queue '%s'", event.getEventId(), event.getPath(), event.getTimestamp(),
                        event.getType(),
                        currentTime - event.getTimestamp(), persistentQueue.getName());
                Assertions.assertEquals(shouldLog ? 1 : 0, memoryAppender.search(msg, Level.DEBUG).size());
            }
        } finally {
            setLongSystemProperty(ConstantValues.persistentQueueLagThresholdMilis, originalThreshold);
            setStringSystemProperty(ConstantValues.persistentQueueLagConfigurationNameFilterList,
                    originalConfigurationListStr);


        }
    }

    @Test
    void testFilterEvents() {
        List<QueueEvent> loadedEvents = getQueueEvents();
        List<QueueEvent> filterEvents = loadedEvents.subList(0, 5);
        List<QueueEvent> executedEvents = loadedEvents.subList(0, 4);
        persistentQueueMetricsMonitor.start(persistentQueue);
        persistentQueueMetricsMonitor.loadedEvents(persistentQueue.getName(), loadedEvents);
        persistentQueueMetricsMonitor.addFilterEvents(persistentQueue.getName(), filterEvents, executedEvents);
        assertEquals(loadedEvents.size() - filterEvents.size(),
                meterRegistry.get("pq.events.filtered").tag("queue", QUEUE_LABEL).tag("type", "event").counter()
                        .count());
        assertEquals(filterEvents.size() - executedEvents.size(),
                meterRegistry.get("pq.events.filtered").tag("queue", QUEUE_LABEL).tag("type", "reduction")
                        .counter().count());
    }

    @Test
    void testReportHandleNullCase() {
        Assertions.assertDoesNotThrow(() -> {
            persistentQueueMetricsMonitor.loadedEvents(persistentQueue.getName(), null);
            persistentQueueMetricsMonitor.addFilterEvents(persistentQueue.getName(), null, null);
            persistentQueueMetricsMonitor.beforeImplementor(persistentQueue.getName(), null, 10);
            persistentQueueMetricsMonitor.afterImplementor(persistentQueue.getName(), null, null);
            persistentQueueMetricsMonitor.end(persistentQueue.getName(), null);
        });
    }

    private static List<QueueEvent> getQueueEvents() {
        List<QueueEvent> loadedEvents = Lists.newArrayList();
        loadedEvents.add(new QueueEventImpl(new EventInfo(1, 100, EventType.create, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(2, 100, EventType.update, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(3, 101, EventType.update, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(5, 101, EventType.props, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(6, 102, EventType.delete, "repo1/a/b/c/", null)));
        loadedEvents.add(new QueueEventImpl(new EventInfo(7, 103, EventType.create, "repo1/a/b/c/", null)));
        return loadedEvents;
    }

    private static Iterable<Tag> getTagsForImplementor(String bucketSize, String exceptionName) {
        return Tags.of("queue", QUEUE_LABEL, "size", bucketSize, "ex", exceptionName);
    }

    private void setupLoggingAssertions() {
        memoryAppender = new MemoryAppender();
        memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        Logger serviceLogger =
                (Logger) LoggerFactory.getLogger("org.artifactory.queue.monitor.PersistentQueueMetricsMonitor");
        serviceLogger.setLevel(Level.DEBUG);
        serviceLogger.addAppender(memoryAppender);
        memoryAppender.start();
    }

    private static Stream<Arguments> testLoadEventsLogLongPendingProvider() {
        return Stream.of(
                Arguments.of(1000, "", true),
                Arguments.of(1000, "Stam," + CONFIGURATION_NAME.toLowerCase(), true),
                Arguments.of(1000, "Stam", false),
                Arguments.of(-1, "", false)
        );
    }
}