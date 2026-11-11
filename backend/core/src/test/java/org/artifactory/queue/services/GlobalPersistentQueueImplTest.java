package org.artifactory.queue.services;

import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.queue.model.PersistentQueueRunningContext;
import org.artifactory.repo.event.EventInfo;
import org.artifactory.spring.ArtifactoryApplicationContext;
import org.artifactory.storage.event.EventsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalPersistentQueueImplTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private QueueConfiguration queueConfiguration;
    @Mock
    private PersistentQueueRunningContext context;
    @Mock
    private EventsService eventsService;
    @Mock
    private ArtifactoryApplicationContext applicationContext;

    private GlobalPersistentQueueImpl globalPersistentQueue;

    @BeforeEach
    void setUp() {
        when(applicationContext.beanForType(EventsService.class)).thenReturn(eventsService);
        ArtifactoryContextThreadBinder.bind(applicationContext);
        mockQueueConfiguration();
        globalPersistentQueue = new GlobalPersistentQueueImpl(queueConfiguration, "globalKey");
    }

    @AfterEach
    void tearDown() {
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testGetEventsAfter() {
        long eventId = 123L;
        int maxBulkSize = 10;
        when(context.getStartingMarker()).thenReturn(eventId);
        when(eventsService.getEventsAfter(eventId, "", "", maxBulkSize, "INCREMENTAL_INDEX"))
                .thenReturn(Collections.emptyList());

        List<EventInfo> events = globalPersistentQueue.getEventsAfter(context, null, maxBulkSize);

        assertNotNull(events);
        assertTrue(events.isEmpty());
        verify(eventsService).getEventsAfter(eventId, "", "", maxBulkSize, "INCREMENTAL_INDEX");
    }

    @Test
    void testShouldAlwaysTrigger() {
        assertTrue(globalPersistentQueue.shouldAlwaysTrigger());
    }

    private void mockQueueConfiguration() {
        when(queueConfiguration.getCursorType()).thenReturn(QueueCursorType.INCREMENTAL_INDEX);
        when(queueConfiguration.getBackoffSupplier().getMaxWaitHours()).thenReturn(1);
        when(queueConfiguration.getBackoffSupplier().getFactor()).thenReturn(1);
    }
}