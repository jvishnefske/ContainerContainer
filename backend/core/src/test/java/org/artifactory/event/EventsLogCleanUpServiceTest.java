package org.artifactory.event;

import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.event.migration.EventTableRepoKeyMigration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.federation.FederatedRepoService;
import org.artifactory.schedule.TaskService;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorType;
import org.artifactory.storage.db.event.error.service.NodeEventsErrorsStorageService;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.artifactory.storage.db.event.service.NodeEventCursorService;
import org.artifactory.storage.event.EventsService;
import org.artifactory.storage.fs.service.ConfigsServiceKey;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.DbType;
import org.joda.time.DateTime;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.artifactory.common.ConstantValues.ageOfEventsLogEntriesToDiscardDays;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.DISABLED;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.DISABLED_BY_SYSTEM;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.EXHAUSTED;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.FS_RUNNING;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.HEALTHY;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.OUT_OF_SYNC;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.PENDING_FS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

@Test
public class EventsLogCleanUpServiceTest extends ArtifactoryHomeBoundTest {

    @Mock
    private EventsService eventsService;

    @Mock
    private NodeEventsErrorsStorageService eventsErrorsService;

    @Mock
    private TaskService taskService;

    @Mock
    NodeEventCursorService nodeEventCursorService;
    @Mock
    EventTableRepoKeyMigration eventTableRepoKeyMigration;
    @Mock
    FederatedRepoService federatedRepoService;

    private EventsLogCleanUpService eventsLogCleanUpService;
    private AutoCloseable mocks;

    @DataProvider
    public static Object[][] cursorStatusProvider() {
        return new Object[][] {
                {HEALTHY, true},
                {DISABLED, true},
                {EXHAUSTED, true},
                {PENDING_FS, true},
                {FS_RUNNING, false},
                {OUT_OF_SYNC, false},
        };
    }

    @BeforeMethod
    public void init() {
        mocks = MockitoAnnotations.openMocks(this);
        eventsLogCleanUpService = Mockito.spy(new EventsLogCleanUpService(taskService, eventsService,
                nodeEventCursorService, eventsErrorsService, eventTableRepoKeyMigration, federatedRepoService));
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ageOfEventsLogEntriesToDiscardDays.getPropertyName(), "0");
    }

    @AfterMethod
    public void tearDown() throws Exception {
        mocks.close();
    }

    @Test(dataProvider = "MaxBoundaryProvider")
    public void testGetMaxBoundary(long minCursor, long minError, long configBoundary,
            EventsLogCleanUpService.MaxBoundary expected) {
        // Given
        doAnswer(invocation -> configBoundary).when(eventsLogCleanUpService).getConfigBoundary(any(Date.class));
        when(nodeEventCursorService.oldestTimestampForEventLogOperator()).thenReturn(minCursor);
        when(eventsErrorsService.getFirstEventErrorTimestampWithEventId()).thenReturn(minError);

        // When
        EventsLogCleanUpService.MaxBoundary maxBoundary = eventsLogCleanUpService.getMaxBoundary();

        // Then
        assertEquals(maxBoundary.getTimestamp(), expected.getTimestamp());
        assertEquals(maxBoundary.isShouldDeleteExpiredEvents(), expected.isShouldDeleteExpiredEvents());
        verify(eventsLogCleanUpService, times(1)).getMaxBoundary();
        verify(nodeEventCursorService, times(1)).oldestTimestampForEventLogOperator();
        verify(eventsErrorsService, times(1)).getFirstEventErrorTimestampWithEventId();

    }

    @DataProvider(name = "MaxBoundaryProvider")
    public Object[][] getMaxBoundaryProvider() {
        long time = System.currentTimeMillis();

        // {minCursor, minError, configBoundary, expected}
        return new Object[][] {
                {time - HOURS.toMillis(10),
                        time - HOURS.toMillis(5),
                        time - HOURS.toMillis(20),
                        EventsLogCleanUpService.MaxBoundary.builder()
                                .timestamp(time - HOURS.toMillis(10)).build()},
                {time - HOURS.toMillis(10),
                        time - HOURS.toMillis(15),
                        time - HOURS.toMillis(20),
                        EventsLogCleanUpService.MaxBoundary.builder()
                                .timestamp(time - HOURS.toMillis(15)).build()},
                {time - HOURS.toMillis(10),
                        time - HOURS.toMillis(5),
                        time,
                        EventsLogCleanUpService.MaxBoundary.builder()
                                .timestamp(time).shouldDeleteExpiredEvents(true).build()},
                {time - HOURS.toMillis(10),
                        time - HOURS.toMillis(15),
                        time,
                        EventsLogCleanUpService.MaxBoundary.builder()
                                .timestamp(time).shouldDeleteExpiredEvents(true).build()}
        };
    }


    @Test
    public void testCleanUpLargeRange() throws JobExecutionException {
        when(eventsService.getFirstEventTimestamp()).thenReturn(new Date().getTime() - HOURS.toMillis(10));
        when(eventsService.getEventsCount()).thenReturn(10L);
        eventsLogCleanUpService.cleanup();
        verify(eventsService, atLeast(3)).deleteRange(anyLong(), anyLong());
        verify(eventsErrorsService, atLeast(1)).deleteBeforeWithEventId(anyLong());
    }

    @Test
    public void testCleanUpLargeRangeOldestMarkerIsFromBeforeAllEvents() throws JobExecutionException {
        ArtifactoryHome.get().getArtifactoryProperties()
                .setProperty(ageOfEventsLogEntriesToDiscardDays.getPropertyName(), "1");
        when(eventsService.getFirstEventTimestamp()).thenReturn(new Date().getTime() - HOURS.toMillis(10));
        when(nodeEventCursorService.oldestTimestampForEventLogOperator())
                .thenReturn((new Date().getTime() - HOURS.toMillis(11)));
        when(eventsErrorsService.getFirstEventErrorTimestampWithEventId())
                .thenReturn((new Date().getTime() - HOURS.toMillis(10)));
        when(eventsService.getEventsCount()).thenReturn(10L);
        eventsLogCleanUpService.cleanup();
        verify(eventsService, times(0)).deleteRange(anyLong(), anyLong());
        verify(eventsErrorsService, times(0)).deleteBeforeWithEventId(anyLong());
    }

    @Test
    public void testCleanUpSmallRange() throws JobExecutionException {

        // Given
        DateTime time = new DateTime(100000);
        /*
          cur time         = 100000
          conf boundary    = 100000
          first            = 100000 - 1H
          events           = 0
          errors           = 0
         */

        doAnswer(invocation -> {
            // Force the getConfigBoundary to use the same base time (to enable debug)
            ((Date) invocation.getArguments()[0]).setTime(time.getMillis());
            return invocation.callRealMethod();
        }).when(eventsLogCleanUpService).getConfigBoundary(any(Date.class));

        when(eventsService.getFirstEventTimestamp()).thenReturn(time.minusHours(1).getMillis());
        when(eventsService.getEventsCount()).thenReturn(10L);


        // When
        eventsLogCleanUpService.cleanup();

        // Then
        // Verify one time delete
        verify(eventsService, times(1)).deleteRange(anyLong(), anyLong());
        verify(eventsErrorsService, times(1)).deleteBeforeWithEventId(anyLong());
    }

    @Test
    public void testCleanUpAllEventsAreTooYoung() throws JobExecutionException {

        // Given
        DateTime time = new DateTime(1000);
        /*
          cur time         = 1000
          conf boundary    = 1000
          first            = 2000
          events           = 0
          errors           = 0
         */

        doAnswer(invocation -> {
            // Force the getConfigBoundary to use the same base time (to enable debug)
            ((Date) invocation.getArguments()[0]).setTime(time.getMillis());
            return invocation.callRealMethod();
        }).when(eventsLogCleanUpService).getConfigBoundary(any(Date.class));
        when(eventsService.getFirstEventTimestamp()).thenReturn(time.plusMillis(1000).getMillis());
        when(eventsService.getEventsCount()).thenReturn(10L);

        // When
        eventsLogCleanUpService.cleanup();

        // Then
        // Verify no cleanup
        verify(eventsService, never()).deleteRange(anyLong(), anyLong());
        verify(eventsErrorsService, never()).deleteBeforeWithEventId(anyLong());
    }

    @Test(dataProvider = "cursorStatusProvider")
    public void testMarkFederatedAsOOS(NodeEventCursorStatus status, boolean markAsOOS) {
        when(nodeEventCursorService.getEventIdCursorsBy(status, 3L)).thenReturn(getNodeCursorData());
        when(eventsService.countEventsRange(0L, 3L, "repo2",
                QueueCursorType.FEDERATED_REPLICATION.name())).thenReturn(1);
        when(eventsService.hasEventsAfterTimestamp(3L, "repo2")).thenReturn(true);
        when(eventsService.countEventsRange(0L, 3L, "repo",
                QueueCursorType.FEDERATED_REPLICATION.name())).thenReturn(0);
        eventsLogCleanUpService.markCursorsAsOutOfSyncIfNeeded(1L, 3L);

        Iterator<NodeEventCursor> cursorIter = getNodeCursorData().iterator();
        verify(nodeEventCursorService, times(markAsOOS ? 1 : 0))
                .updateCursorWithStatus(cursorIter.next().getOperatorId(), NodeEventCursorStatus.OUT_OF_SYNC);
        verify(nodeEventCursorService, times(0))
                .updateCursorWithStatus(cursorIter.next().getOperatorId(), NodeEventCursorStatus.OUT_OF_SYNC);
    }

    public void testMarkDisabledBySystemFedQueueAsOOS() {
        NodeEventCursor nodeEventCursor = NodeEventCursor.builder()
                .operatorId("jfrog_rtfs_internal-federation-servicehttp://0.0.0.0:8081/artifactory/federation_queue")
                .type(NodeEventCursorType.FEDERATION_SERVICE)
                .status(DISABLED_BY_SYSTEM).build();
        when(nodeEventCursorService.getEventIdCursorsBy(DISABLED_BY_SYSTEM, 3L)).thenReturn(List.of(nodeEventCursor));
        when(eventsService.countEventsRange(0L, 3L, null,
                QueueCursorType.FEDERATION_SERVICE.name())).thenReturn(1);
        when(eventsService.hasEventsAfterTimestamp(3L, null)).thenReturn(true);
        eventsLogCleanUpService.markCursorsAsOutOfSyncIfNeeded(1L, 3L);

        verify(nodeEventCursorService, never())
                .updateCursorWithStatus(nodeEventCursor.getOperatorId(), NodeEventCursorStatus.OUT_OF_SYNC);
        verify(federatedRepoService, times(1)).addOrUpdateConfig(ConfigsServiceKey.RTFS_QUEUE_STATE_MD_NEGOTIATION,
                OUT_OF_SYNC.name());
    }

    /**
     * Test that when a member is idle, it should not be marked as OOS if it has no events after the last cursor
     * This is our way to differentiate if events are "just old" or if something is wrong with the member
     */
    @Test
    public void testMarkFederatedWhenMemberIdleShouldNotMarkOOS() {
        when(nodeEventCursorService.getEventIdCursorsBy(HEALTHY, 3L)).thenReturn(getNodeCursorData());
        when(eventsService.countEventsRange(0L, 3L, "repo2",
                QueueCursorType.FEDERATED_REPLICATION.name())).thenReturn(1);
        when(eventsService.hasEventsAfterTimestamp(3L, "repo2")).thenReturn(false);
        eventsLogCleanUpService.markCursorsAsOutOfSyncIfNeeded(1L, 3L);

        Iterator<NodeEventCursor> cursorIter = getNodeCursorData().iterator();
        verify(nodeEventCursorService, times(0))
                .updateCursorWithStatus(cursorIter.next().getOperatorId(), NodeEventCursorStatus.OUT_OF_SYNC);
    }

    @Test
    public void testGetEventsDeleteInterval() {
        long value;
        // check default value of 4 hours
        value = eventsLogCleanUpService.getEventsDeleteInterval();
        assertEquals(4 * 3600 * 1000, value);

        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ConstantValues.eventsCleanupIntervalHours.getPropertyName(), "1");
        value = eventsLogCleanUpService.getEventsDeleteInterval();
        assertEquals(1 * 3600 * 1000, value);

        // check fractions of an hour is supported
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ConstantValues.eventsCleanupIntervalHours.getPropertyName(), "0.5");
        value = eventsLogCleanUpService.getEventsDeleteInterval();
        assertEquals(1800 * 1000, value);

        // Invalid value (non-numeric) would fall back to default of 4 hours.
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ConstantValues.eventsCleanupIntervalHours.getPropertyName(), "blabla");
        value = eventsLogCleanUpService.getEventsDeleteInterval();
        assertEquals(4 * 3600 * 1000, value);
    }

    @Test
    public void testGetTmpCleanupTaskCron() {
        String defaultCron = "0 30 0/1 ? * * *";
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ConstantValues.tmpEventsLogTruncateTaskCron.getPropertyName(), "blabla");
        assertEquals(defaultCron, eventsLogCleanUpService.getTmpTruncateTaskCron());
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ConstantValues.tmpEventsLogTruncateTaskCron.getPropertyName(), "0 29 0/1 ? * * ");
        assertEquals(eventsLogCleanUpService.getTmpTruncateTaskCron(), "0 29 0/1 ? * * ");
        ArtifactoryHome
                .get().getArtifactoryProperties()
                .setProperty(ConstantValues.tmpEventsLogTruncateTaskCron.getPropertyName(), "");
        assertNull(eventsLogCleanUpService.getTmpTruncateTaskCron());
    }

    @Test
    public void testOnContextCreated() {
        if (ArtifactoryHome.get().getDBProperties().getDbType().equals(DbType.POSTGRESQL)) {
            eventsLogCleanUpService.onContextCreated();
            verify(taskService, times(2)).startTask(any(), anyBoolean());
        } else {
            eventsLogCleanUpService.onContextCreated();
            verify(taskService, times(1)).startTask(any(), anyBoolean());
        }
    }

    private List<NodeEventCursor> getNodeCursorData() {
        return List.of(NodeEventCursor.builder()
                        .operatorId("repo2-federated_replicationhttp://0.0.0.0:8081/artifactory/repo2-cursor")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION).build(),
                NodeEventCursor.builder()
                        .operatorId("repo-federated_replicationhttp://0.0.0.0:8081/artifactory/repo-cursor")
                        .type(NodeEventCursorType.FEDERATED_REPLICATION).build());
    }
}