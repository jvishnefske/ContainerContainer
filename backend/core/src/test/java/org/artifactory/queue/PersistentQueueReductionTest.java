package org.artifactory.queue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.event.queue.QueueReductionBehaviours;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.queue.behaviors.QueueReductionBehavioursImpl;
import org.artifactory.queue.error.PersistentQueueErrorService;
import org.artifactory.queue.services.PersistentQueue;
import org.artifactory.queue.services.PersistentQueueCursorService;
import org.artifactory.queue.services.PersistentQueueImpl;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.event.EventsService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;


public class PersistentQueueReductionTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    EventsService eventsService;
    @Mock
    PersistentQueueCursorService queueCursorService;
    @Mock
    ArtifactoryContext artifactoryContext;
    @Mock
    PersistentQueueErrorService queueErrorService;
    @Mock
    QueueTriggeringService queueTriggeringService;
    AutoCloseable closeable;

    Logger logger = (Logger) LoggerFactory.getLogger(PersistentQueueImpl.class);
    ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        logger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        listAppender.start();
        when(artifactoryContext.beanForType(EventsService.class)).thenReturn(eventsService);
        when(artifactoryContext.beanForType(PersistentQueueCursorService.class)).thenReturn(queueCursorService);
        when(artifactoryContext.beanForType(PersistentQueueErrorService.class)).thenReturn(queueErrorService);
        when(artifactoryContext.beanForType(QueueTriggeringService.class)).thenReturn(queueTriggeringService);
        ArtifactoryContextThreadBinder.bind(artifactoryContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
        listAppender.stop();
    }


    @ParameterizedTest
    @MethodSource("params")
    void testFedReductionBehaviourSkipCreateEliminateDeleteBehavior(String shouldSkipCreateEliminatesDelete,
            int eventsAfterDedup) throws Exception {
        Boolean skipCreateEliminatesDelete = getOrCreateArtifactoryHomeStub().getArtifactoryProperties()
                .getBooleanProperty(ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication);
        try {
            logger.addAppender(listAppender);
            getOrCreateArtifactoryHomeStub().setProperty(
                    ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication, shouldSkipCreateEliminatesDelete);
            // Force reload the behavior using reflection
            Field behaviorField = QueueReductionBehavioursImpl.class.getDeclaredField("ReductionBehaviours");
            behaviorField.setAccessible(true);

            @SuppressWarnings("unchecked")
            EnumMap<QueueReductionBehaviours, QueueReductionBehavioursImpl> map =
                    (EnumMap<QueueReductionBehaviours, QueueReductionBehavioursImpl>) behaviorField.get(null);

            // Store original for restoration
            EnumMap<QueueReductionBehaviours, QueueReductionBehavioursImpl> original =
                    new EnumMap<>(QueueReductionBehaviours.class);
            original.putAll(map);

            // Get fresh behavior based on current property value
            Method getFederatedMethod =
                    QueueReductionBehavioursImpl.class.getDeclaredMethod("getFederatedReplicationBehavior");
            getFederatedMethod.setAccessible(true);
            QueueReductionBehavioursImpl freshBehavior =
                    (QueueReductionBehavioursImpl) getFederatedMethod.invoke(null);

            // Update the map with fresh behavior
            map.put(QueueReductionBehaviours.FederatedReplication, freshBehavior);

            // Test setup
            EventInfo deleteEvent = new EventInfo(5, 5, EventType.delete, "repo1/a/b/e/", null);
            EventInfo createEvent = new EventInfo(10, 10, EventType.create, "repo1/a/b/e/", null);
            EventInfo updateEvent = new EventInfo(11, 11, EventType.update, "repo1/a/b/e/", null);
            when(eventsService.getEventsAfter(anyLong(), any(), any(), anyInt(), anyString()))
                    .thenReturn(Stream.of(deleteEvent, createEvent, updateEvent).collect(Collectors.toList()));

            QueueConfiguration configuration = QueueConfiguration.builder()
                    .implementor((events, key) -> {})
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

            persistentQueue.trigger();

            List<ILoggingEvent> iLoggingEvents =
                    listAppender.list.stream()
                            .filter(event -> event.getLevel().equals(Level.DEBUG))
                            .filter(event -> event.getMessage().startsWith("Events before"))
                            .toList();

            assertThat(iLoggingEvents.size()).isEqualTo(1);
            assertThat(iLoggingEvents.get(0).getFormattedMessage())
                    .isEqualTo(String.format("Events before %s and events after filter chain %s", 3, eventsAfterDedup));

        } finally {
            logger.detachAppender(listAppender);
            try {
                getOrCreateArtifactoryHomeStub().setProperty(
                        ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication,
                        skipCreateEliminatesDelete.toString());
                Field behaviorField = QueueReductionBehavioursImpl.class.getDeclaredField("ReductionBehaviours");
                behaviorField.setAccessible(true);

                @SuppressWarnings("unchecked")
                EnumMap<QueueReductionBehaviours, QueueReductionBehavioursImpl> map =
                        (EnumMap<QueueReductionBehaviours, QueueReductionBehavioursImpl>) behaviorField.get(null);

                // Re-initialize with original behaviors
                Method federatedMethod =
                        QueueReductionBehavioursImpl.class.getDeclaredMethod("getFederatedReplicationBehavior");
                federatedMethod.setAccessible(true);
                map.put(QueueReductionBehaviours.FederatedReplication,
                        (QueueReductionBehavioursImpl) federatedMethod.invoke(null));

            } catch (Exception e) {
                logger.error("Failed to restore original behaviors", e);
            }

        }
    }

    public static Stream<Arguments> params() {
        return Stream.of(Arguments.of("true", 2),
                Arguments.of("false", 1));
    }

}
