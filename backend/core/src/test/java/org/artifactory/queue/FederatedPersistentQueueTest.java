package org.artifactory.queue;

import org.artifactory.event.queue.QueueConfiguration;
import org.artifactory.event.queue.QueueCursorType;
import org.artifactory.queue.services.FederatedPersistentQueue;
import org.artifactory.queue.services.FederatedPersistentQueueImpl;
import org.artifactory.queue.services.PersistentQueueCursorInfo;
import org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus;
import org.artifactory.storage.db.event.model.NodeEventCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.artifactory.common.ConstantValues.federatedRepoAsyncFullSyncEnabled;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.EXHAUSTED;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.FS_RUNNING;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.HEALTHY;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.OUT_OF_SYNC;
import static org.artifactory.storage.db.event.entity.enums.NodeEventCursorStatus.PENDING_FS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedPersistentQueueTest extends PersistentQueueBaseTest {

    @BeforeEach
    void setUp() throws Exception {
        prepareMocks();
    }

    @Test
    void testTriggerRecovery() {
        FederatedPersistentQueue queue1 = mock(FederatedPersistentQueue.class);
        FederatedPersistentQueue queue2 = mock(FederatedPersistentQueue.class);

        Set<FederatedPersistentQueue> exhaustedQueues = new HashSet<>();
        exhaustedQueues.add(queue1);
        exhaustedQueues.add(queue2);

        exhaustedQueues.parallelStream().forEach(FederatedPersistentQueue::recoverExhausted);

        verify(queue1).recoverExhausted();
        verify(queue2).recoverExhausted();
    }

    @ParameterizedTest
    @MethodSource("nodeCursorStateProvider")
    public void testIsExhausted(NodeEventCursorStatus status, boolean expectedResult) {
        NodeEventCursor nodeEventCursor = mock(NodeEventCursor.class);
        when(nodeEventCursor.getStatus()).thenReturn(status);
        when(queueCursorService.getCursorAndCreateIfNeeded(anyString(), any())).thenReturn(nodeEventCursor);

        QueueConfiguration configuration = createFederatedReplicationQueue();
        FederatedPersistentQueue federatedPersistentQueue = new FederatedPersistentQueueImpl(configuration, "repoKey");

        assertEquals(expectedResult, federatedPersistentQueue.isStatus(EXHAUSTED));
    }

    @Test
    void testRecoverExhausted() {
        FederatedPersistentQueue federatedPersistentQueue =
                new FederatedPersistentQueueImpl(createFederatedReplicationQueue(), "repoKey");

        federatedPersistentQueue.recoverExhausted();

        verify(queueErrorService).triggerRecovery(federatedPersistentQueue);
        verify(queueCursorService).updateFromStatusToStatus(federatedPersistentQueue.getName(), EXHAUSTED, HEALTHY);
        verify(queueTriggeringService).triggerQueues(federatedPersistentQueue.getRepoKey(),
                QueueCursorType.FEDERATED_REPLICATION);
    }

    @ParameterizedTest
    @EnumSource(NodeEventCursorStatus.class)
    void testResetStatusIfEquals(NodeEventCursorStatus status) {
        FederatedPersistentQueue federatedPersistentQueue =
                new FederatedPersistentQueueImpl(createFederatedReplicationQueue(), "repoKey");

        federatedPersistentQueue.resetStatusIfEquals(status);

        verify(queueCursorService).updateFromStatusToStatus(federatedPersistentQueue.getName(), status, HEALTHY);
        verify(queueTriggeringService).triggerQueues(federatedPersistentQueue.getRepoKey(),
                QueueCursorType.FEDERATED_REPLICATION);
    }

    @Test
    void testIsPausedWhenFederatedRepoFullSyncEnabled() {
        mockProperties.setProperty(federatedRepoAsyncFullSyncEnabled.getPropertyName(), "true");

        FederatedPersistentQueue federatedPersistentQueue =
                new FederatedPersistentQueueImpl(createFederatedReplicationQueue(), "repoKey");
        NodeEventCursor cursor = mock(NodeEventCursor.class);
        when(queueCursorService.getCursorAndCreateIfNeeded(federatedPersistentQueue.getName(),
                QueueCursorType.FEDERATED_REPLICATION)).thenReturn(cursor);
        // given
        when(cursor.getStatus()).thenReturn(HEALTHY);

        // when
        boolean result = federatedPersistentQueue.isPaused();

        // then
        assertFalse(result);

        // given
        when(cursor.getStatus()).thenReturn(PENDING_FS);

        // when
        result = federatedPersistentQueue.isPaused();

        //then
        assertTrue(result);
    }

    @Test
    void testIsPausedWhenFederatedRepoFullSyncDisabled() {
        mockProperties.setProperty(federatedRepoAsyncFullSyncEnabled.getPropertyName(), "false");

        FederatedPersistentQueue federatedPersistentQueue =
                new FederatedPersistentQueueImpl(createFederatedReplicationQueue(), "repoKey");
        NodeEventCursor cursor = mock(NodeEventCursor.class);
        when(queueCursorService.getCursorAndCreateIfNeeded(federatedPersistentQueue.getName(),
                QueueCursorType.FEDERATED_REPLICATION)).thenReturn(cursor);
        // given
        when(cursor.getStatus()).thenReturn(HEALTHY);

        // when
        boolean result = federatedPersistentQueue.isPaused();

        // then
        assertFalse(result);

        // given
        when(cursor.getStatus()).thenReturn(PENDING_FS);

        // when
        result = federatedPersistentQueue.isPaused();

        //then
        assertFalse(result);

        // given
        when(cursor.getStatus()).thenReturn(FS_RUNNING);

        // when
        result = federatedPersistentQueue.isPaused();

        //then
        assertTrue(result);
    }

    @Test
    public void testResetStatus() {
        QueueConfiguration configuration = createIncrementalIndexQueue();
        FederatedPersistentQueue persistentQueue = new FederatedPersistentQueueImpl(configuration, "repo1");
        persistentQueue.resetStatus();
        String cursorName = new PersistentQueueCursorInfo("repo1",
                configuration.getCursorType(),
                configuration.getVariant()
        ).getCursorName();
        verify(queueCursorService, times(1)).updateCursorStatus(cursorName, NodeEventCursorStatus.HEALTHY);
    }

    static Stream<Arguments> nodeCursorStateProvider() {
        return Stream.of(
                Arguments.of(OUT_OF_SYNC, false),
                Arguments.of(HEALTHY, false),
                Arguments.of(EXHAUSTED, true)
        );
    }
}