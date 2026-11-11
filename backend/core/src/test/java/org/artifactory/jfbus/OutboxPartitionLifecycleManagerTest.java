package org.artifactory.jfbus;

import org.jfrog.eventing.jfbus.outbox.processor.OutboxOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxPartitionLifecycleManagerTest {

    @Mock
    private OutboxOperator outboxOperator;

    @Test
    void startRunsMaintenanceImmediately() {
        OutboxPartitionLifecycleManager manager = new OutboxPartitionLifecycleManager(outboxOperator, 60);

        manager.start();
        verify(outboxOperator, times(1)).managePartitions(null);

        manager.stop();
    }

    @Test
    void startIsIdempotent() {
        OutboxPartitionLifecycleManager manager = new OutboxPartitionLifecycleManager(outboxOperator, 60);

        manager.start();
        manager.start();

        verify(outboxOperator, times(1)).managePartitions(null);
        manager.stop();
    }

    @Test
    void runMaintenanceSwallowsExceptions() {
        OutboxPartitionLifecycleManager manager = new OutboxPartitionLifecycleManager(outboxOperator, 60);
        org.mockito.Mockito.doThrow(new RuntimeException("partition ddl failed"))
                .when(outboxOperator).managePartitions(null);

        manager.runMaintenance();

        verify(outboxOperator).managePartitions(null);
    }
}
