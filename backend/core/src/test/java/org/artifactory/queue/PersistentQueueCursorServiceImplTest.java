package org.artifactory.queue;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.common.property.ArtifactorySystemProperties;
import org.artifactory.event.queue.QueueStatus;
import org.artifactory.queue.services.PersistentQueueCursorServiceImpl;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.jfrog.common.ClockUtils;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.artifactory.event.queue.QueueCursorType.FEDERATED_REPLICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;


class PersistentQueueCursorServiceImplTest {

    @Mock
    private NodeEventCursorService nodeEventCursorService;

    @Mock
    private EventsService eventsService;

    @Mock
    private AddonsManager addonsManager;

    @Mock
    private ArtifactoryHome home;

    @Mock
    private ArtifactorySystemProperties props;

    @Mock
    private HaAddon haAddon;

    @Mock
    private ConflictsGuard conflictsGuard;

    private PersistentQueueCursorServiceImpl cursorService;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = openMocks(this);

        ArtifactoryHome.bind(home);
        when(home.getArtifactoryProperties()).thenReturn(props);
        when(props.getLongProperty(ConstantValues.persistentQueueRepoMinEventCacheExpireSec)).thenReturn(0L);
        when(props.getLongProperty(ConstantValues.lastEventIdCacheTimeoutSec)).thenReturn(30L);
        when(props.getLongProperty(ConstantValues.ageOfEventsLogEntriesToDiscardDays)).thenReturn(3L);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.getConflictsGuard(anyString())).thenReturn(conflictsGuard);

        cursorService = new PersistentQueueCursorServiceImpl(nodeEventCursorService, eventsService, addonsManager);
    }

    @AfterEach
    void teardown() throws Exception {
        ArtifactoryHome.unbind();
        mocks.close();
    }

    @Test
    void testGetStatusNotAvailable() {
        when(nodeEventCursorService.cursorForOperator("cursor1")).thenReturn(null);

        QueueStatus status = cursorService.getStatus("repo1", "cursor1");

        assertEquals(QueueStatus.NOT_AVAILABLE, status);

    }

    @Test
    void testCreateMarkerIfNotExist() throws InterruptedException {
        when(nodeEventCursorService.cursorForOperator("curName")).thenReturn(null);
        when(conflictsGuard.tryToLock(anyString(), anyLong(), any())).thenReturn(true);
        when(nodeEventCursorService.cursorForOperator("curName")).thenReturn(null);

        assertTrue(cursorService.createMarkerIfNotExists("repo", "curName", FEDERATED_REPLICATION));
        ArgumentCaptor<NodeEventCursor> argument = ArgumentCaptor.forClass(NodeEventCursor.class);
        verify(nodeEventCursorService, times(1)).insertCursor(argument.capture());
        assertEquals("curName", argument.getValue().getOperatorId());
        assertEquals(-1L, argument.getValue().getEventMarker().longValue());
        assertEquals(argument.getValue().getStatus(), NodeEventCursorStatus.PENDING_FS);
        assertEquals(argument.getValue().getType(), NodeEventCursorType.FEDERATED_REPLICATION);
    }

    @Test
    void copyCursor() {
        // mocks
        when(nodeEventCursorService.cursorForOperator("curName")).thenReturn(null);
        NodeEventCursor mockNodeEventCursor = mock(NodeEventCursor.class);
        when(mockNodeEventCursor.getEventMarker()).thenReturn(2L);
        when(mockNodeEventCursor.getStatus()).thenReturn(NodeEventCursorStatus.HEALTHY);

        // run function
        cursorService.copyCursor("repo", "curName", mockNodeEventCursor);

        // test
        ArgumentCaptor<NodeEventCursor> argument = ArgumentCaptor.forClass(NodeEventCursor.class);
        verify(nodeEventCursorService, times(1)).insertCursor(argument.capture());
        assertEquals("curName", argument.getValue().getOperatorId());
        assertEquals(2, argument.getValue().getEventMarker().longValue());
        assertEquals(NodeEventCursorStatus.HEALTHY, argument.getValue().getStatus());
    }

    @Test
    void copyCursorNotFound() {
        // mocks
        NodeEventCursor mockNodeEventCursor = mock(NodeEventCursor.class);
        when(nodeEventCursorService.cursorForOperator("curName")).thenReturn(mockNodeEventCursor);
        when(mockNodeEventCursor.getEventMarker()).thenReturn(2L);
        when(mockNodeEventCursor.getStatus()).thenReturn(NodeEventCursorStatus.HEALTHY);

        // run function
        cursorService.copyCursor("repo", "curName", mockNodeEventCursor);

        // test
        ArgumentCaptor<NodeEventCursor> argument = ArgumentCaptor.forClass(NodeEventCursor.class);
        verify(nodeEventCursorService, times(0)).insertCursor(argument.capture());
    }

    @Test
    void testLastEventSupplier() throws InterruptedException {
        when(eventsService.getLastEventLogId()).thenReturn(5L);
        ExecutorService consumerExecutor = Executors.newFixedThreadPool(5);
        List<Callable<Long>> executables = new LinkedList<>();
        IntStream.range(0, 10).forEach(i ->
                executables.add(() -> cursorService.getCachedLatestEventId())
        );

        List<Future<Long>> answers = consumerExecutor.invokeAll(executables);

        answers.forEach(lastEvent -> {
            try {
                assertNotNull(lastEvent.get());
                assertEquals(5L, lastEvent.get().longValue());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed test for event supplier duo to:", e);
            }
        });
        verify(eventsService, times(1)).getLastEventLogId();
    }

    static Stream<Arguments> testGetStatusProvider() {
        return Stream.of(
                Arguments.of(1L, 3L, QueueStatus.DATA_LOST),
                Arguments.of(10L, 5L, QueueStatus.OK),
                Arguments.of(1L, 2L, QueueStatus.OK),
                Arguments.of(2L, 2L, QueueStatus.OK));
    }

    @ParameterizedTest
    @MethodSource("testGetStatusProvider")
    void testGetStatus(long marker, long firstEventId, QueueStatus expectedStatus) {
        NodeEventCursor cursor = Mockito.mock(NodeEventCursor.class);
        when(nodeEventCursorService.cursorForOperator("cursor1")).thenReturn(cursor);
        when(cursor.getEventMarker()).thenReturn(marker);
        when(eventsService.getFirstEventLogId("repo1")).thenReturn(firstEventId);
        QueueStatus status = cursorService.getStatus("repo1", "cursor1");
        assertEquals(status, expectedStatus);
    }

    @Test
    void testUpdateStatusOutOfSync() {
        cursorService.updateCursorStatus("cursor", NodeEventCursorStatus.OUT_OF_SYNC);
        verify(nodeEventCursorService, times(1)).updateCursorWithStatus("cursor",
                NodeEventCursorStatus.OUT_OF_SYNC);
    }

    @Test
    void testGetCurrentMarkerWithNewCursor() {
        when(nodeEventCursorService.cursorForOperator(anyString())).thenReturn(null);
        NodeEventCursor nodeEventCursor = cursorService.getCursorAndCreateIfNeeded(
                "cursor",
                FEDERATED_REPLICATION
        );
        assertEquals(nodeEventCursor.getEventMarker().longValue(), -1L);
        assertEquals(nodeEventCursor.getOperatorId(), "cursor");
        assertEquals(nodeEventCursor.getStatus(), NodeEventCursorStatus.PENDING_FS);
    }

    @Test
    void testGetCurrentMarker() {
        when(nodeEventCursorService.cursorForOperator(anyString()))
                .thenReturn(NodeEventCursor.builder()
                        .operatorId("cursor").build());
        NodeEventCursor nodeEventCursor = cursorService.getCursorAndCreateIfNeeded(
                "cursor",
                FEDERATED_REPLICATION
        );

        assertEquals("cursor", nodeEventCursor.getOperatorId());
    }

    @Test
    void testUpdateCursorByTimestampWithNewTime() {
        ArtifactorySystemProperties properties = mock(ArtifactorySystemProperties.class);
        when(home.getArtifactoryProperties()).thenReturn(properties);
        when(properties.getLongProperty(any())).thenReturn(2L);
        long timestamp = new DateTime(ClockUtils.epochMillis()).minusDays(1).getMillis();
        cursorService.updateCursor("cursor", 1L);
        verify(nodeEventCursorService, times(1))
                .updateCursorMarker("cursor", 1L);
    }

    @Test
    void testResetMarker() {
        when(eventsService.getLastEventLogId()).thenReturn(2L);
        cursorService.resetMarker("cursor", -1);
        verify(nodeEventCursorService, times(1)).updateCursorMarker("cursor", 2L);
    }

    @ParameterizedTest
    @MethodSource("eventIdProvider")
    void testResetMarker(long eventMarker, long eventId, int timesCalled) {
        NodeEventCursor eventCursor = mock(NodeEventCursor.class);
        when(eventCursor.getEventMarker()).thenReturn(eventMarker);
        when(nodeEventCursorService.cursorForOperator("cursorName")).thenReturn(eventCursor);
        cursorService.resetMarker("cursorName", eventId);
        verify(nodeEventCursorService, times(timesCalled)).updateCursorMarker(anyString(), anyLong());
        verify(nodeEventCursorService).updateCursorLastUpdated(any());
    }

    static Stream<Arguments> eventIdProvider() {
        return Stream.of(
                Arguments.of(5000L, 4000L, 0),
                Arguments.of(4000L, 4000L, 0),
                Arguments.of(3999L, 4000L, 1)
        );
    }

}
