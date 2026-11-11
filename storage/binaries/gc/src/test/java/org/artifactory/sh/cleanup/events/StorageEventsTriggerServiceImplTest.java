package org.artifactory.sh.cleanup.events;

import org.artifactory.common.ConstantValues;
import org.artifactory.event.queue.QueueTriggeringService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.test.ArtifactoryHomeStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author TamirH
 */
class StorageEventsTriggerServiceImplTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void init() {
        final ArtifactoryHomeStub homeStub = getOrCreateArtifactoryHomeStub();
        homeStub.setProperty(ConstantValues.gcTrashBinariesCleanupMaxQueueLength, "2");
        homeStub.setProperty(ConstantValues.gcEventsCleanupDelayMillis, "1");
        homeStub.setProperty(ConstantValues.gcEventsCleanupContinuesTriggerEnabled, "true");
    }

    @AfterEach
    void after() {
        unbindArtifactoryHome();
    }

    @Test
    public void testQueueSizeLimit() {
        final QueueTriggeringService queueTriggeringService = mock(QueueTriggeringService.class);
        final StorageEventsTriggerServiceImpl storageEventsTriggerService =
                new StorageEventsTriggerServiceImpl(queueTriggeringService);
        final ScheduledExecutorService executor =
                (ScheduledExecutorService) ReflectionTestUtils.getField(storageEventsTriggerService, "executor");
        if (executor != null) {
            executor.shutdownNow();
        }
        final ScheduledExecutorService executorService = mock(ScheduledExecutorService.class);
        final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        when(executorService.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenReturn(scheduledFuture);
        ReflectionTestUtils.setField(storageEventsTriggerService, "executor", executorService);
        storageEventsTriggerService.scheduleTrigger();
        storageEventsTriggerService.scheduleTrigger();
        storageEventsTriggerService.scheduleTrigger();
        verify(executorService, times(2)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        storageEventsTriggerService.run();
        storageEventsTriggerService.scheduleTrigger();
        storageEventsTriggerService.scheduleTrigger();
        verify(executorService, times(3)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        storageEventsTriggerService.run();
        verify(executorService, times(3)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }
}