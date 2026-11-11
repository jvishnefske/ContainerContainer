package org.artifactory.workqueue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkQueueMetricMonitorServiceTest {
    private static final String QUEUE_NAME = "queue name";
    private static final int QUEUE_SIZE = 1;
    private static final int CURRENT_WORKERS_SIZE = 1;
    private static final int WORKERS_SIZE = 2;

    WorkQueueMetricMonitorService<WorkItem> workQueueMetricMonitorService;
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    WorkQueue<WorkItem> customizeWorkQueue;
    @Mock
    WorkQueue<WorkItem> workQueue;

    @BeforeEach
    void setup() {
        when(workQueue.getKey()).thenReturn(QUEUE_NAME);
        lenient().when(workQueue.getMaxNumberOfWorkers()).thenReturn(1);
        workQueueMetricMonitorService = spy(new WorkQueueMetricMonitorService<>(meterRegistry));
    }

    @Test
    void skipFederatedQueues() {
        when(workQueue.getKey()).thenReturn("Persistent Queue Triggering Federated test-generic-dev-local");
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        Assertions.assertEquals(workQueue, customizeWorkQueue);
    }
    @Test
    void skipDisabledQueues() {
        when(workQueue.getMaxNumberOfWorkers()).thenReturn(0);
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        Assertions.assertEquals(workQueue, customizeWorkQueue);
    }

    @Test
    void testPreDefinedMetrics() {
        when(workQueue.getQueueSize()).thenReturn(QUEUE_SIZE);
        when(workQueue.getMaxNumberOfWorkers()).thenReturn(WORKERS_SIZE);
        when(workQueue.availablePermits()).thenReturn(1);
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        Assertions.assertEquals(CURRENT_WORKERS_SIZE, meterRegistry.get("workqueue.active.workers").gauge().value());
        Assertions.assertEquals(0, meterRegistry.get("workqueue.tasks.published").counter().count());
        Assertions.assertEquals(2, meterRegistry.get("workqueue.max.workers").gauge().value());
        Assertions.assertEquals(QUEUE_SIZE, meterRegistry.get("workqueue.pending.size").gauge().value());
    }

    @Test
    void testOfferWork() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.offerWork(null, null);
        verify(workQueue, times(1)).offerWork(null, null);
    }

    @Test
    void testGetKey() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        clearInvocations(workQueue);
        customizeWorkQueue.getKey();
        verify(workQueue, times(1)).getKey();
    }


    @Test
    void testAvailablePermits() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.availablePermits();
        verify(workQueue, times(1)).availablePermits();
    }

    @Test
    void testStopQueue() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.stopQueue();
        verify(workQueue, times(1)).stopQueue();
    }

    @Test
    void testWaitForDone() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.waitForItemDone(null);
        verify(workQueue, times(1)).waitForItemDone(null);
    }

    @Test
    void testGetQueueSize() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.getQueueSize();
        verify(workQueue, times(1)).getQueueSize();
    }

    @Test
    void testGetMaxNumberOfWorkers() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.getMaxNumberOfWorkers();
        verify(workQueue, times(2)).getMaxNumberOfWorkers();
    }

    @Test
    void testUpdateWorkers() throws InterruptedException {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.updateWorkers(2);
        verify(workQueue, times(1)).updateWorkers(2);
    }

    @Test
    void testEmptyExecute() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.execute();
        verify(workQueue, times(1)).execute();
    }

    @Test
    void testExecutor() {
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.executor();
        verify(workQueue, times(1)).executor();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5, 6})
    void testExecute(int identicalItemSize) {
        WorkItem workItem = Mockito.mock(WorkItem.class);
        List<WorkItem> identicalWorkItems = new ArrayList<>();
        for (int i = 1; i < identicalItemSize; i++) {
            identicalWorkItems.add(Mockito.mock(WorkItem.class));
        }
        identicalWorkItems.add(workItem);
        when(workItem.getIdenticalWorkItems()).thenReturn(identicalWorkItems);
        when(workItem.getAddedToQueueTimestamp()).thenReturn(System.currentTimeMillis() - 50);
        doAnswer(invocationOnMock -> {
            Runnable runnable = invocationOnMock.getArgument(1);
            runnable.run();
            return null;
        }).when(workQueue).execute(eq(workItem), any());
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.execute(workItem, () -> {});
        verify(workQueue, times(1)).execute(eq(workItem), any());
        String expectedSizeBucket = "1";
        if (identicalItemSize >= 2 && identicalItemSize <= 5) {
            expectedSizeBucket = "2-5";
        } else if (identicalItemSize > 5) {
            expectedSizeBucket = "6+";
        }
        Assertions.assertEquals(1,
                meterRegistry.get("workqueue.execution.duration").tags("size", expectedSizeBucket, "ex", "").timer()
                        .count());
        Assertions.assertEquals(identicalItemSize, meterRegistry.get("workqueue.pending.duration").timer().count());
        Assertions.assertEquals(identicalItemSize,
                meterRegistry.get("workqueue.tasks.consumed").counter().count());
        Assertions.assertEquals(1, meterRegistry.get("workqueue.tasks.published").counter().count());
    }

    @Test
    void testExecuteWithError() {
        WorkItem workItem = Mockito.mock(WorkItem.class);
        when(workItem.getIdenticalWorkItems()).thenReturn(List.of(workItem));
        when(workItem.getAddedToQueueTimestamp()).thenReturn(System.currentTimeMillis() - 50);
        doAnswer(invocationOnMock -> {
            Runnable runnable = invocationOnMock.getArgument(1);
            runnable.run();
            return null;
        }).when(workQueue).execute(eq(workItem), any());
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        Assertions.assertThrows(RuntimeException.class,
                () -> customizeWorkQueue.execute(workItem, () -> {throw new RuntimeException("myException");}));
        verify(workQueue, times(1)).execute(eq(workItem), any());
        Assertions.assertEquals(1,
                meterRegistry.get("workqueue.execution.duration").tags("size", "1", "ex", "RuntimeException").timer()
                        .count());
        Assertions.assertEquals(1, meterRegistry.get("workqueue.pending.duration").timer().count());
        Assertions.assertEquals(1, meterRegistry.get("workqueue.tasks.published").counter().count());
    }

    @Test
    void testIgnoreTaskMetric() {
        WorkItem workItem = Mockito.mock(WorkItem.class);
        when(workQueue.execute(eq(workItem), any())).thenReturn(false);
        customizeWorkQueue = workQueueMetricMonitorService.customizeWorkQueue(workQueue);
        customizeWorkQueue.execute(workItem,()->{});
        Assertions.assertEquals(1, meterRegistry.get("workqueue.tasks.published").counter().count());
        Assertions.assertEquals(1, meterRegistry.get("workqueue.tasks.ignored").counter().count());
        verify(workQueue, times(1)).execute(eq(workItem), any());
    }
}