package org.artifactory.sh.cleanup.job;

import org.artifactory.sh.cleanup.GCService;
import org.artifactory.sh.cleanup.GarbageCollectorStrategy;
import org.artifactory.storage.spring.ArtifactoryStorageContext;
import org.artifactory.storage.spring.StorageContextHelper;
import org.artifactory.test.ArtifactoryHomeBoundTest;
import org.jfrog.storage.binstore.ifc.GarbageCollectorInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinaryStoreGarbageCollectorJobTest extends ArtifactoryHomeBoundTest {

    private BinaryStoreGarbageCollectorJob job;
    private MockedStatic<StorageContextHelper> storageContextHelper;
    private AutoCloseable mocks;

    @Mock
    private GCService gcService;
    @Mock
    private ArtifactoryStorageContext storageContext;
    @Mock
    private JobExecutionContext jobContext;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        job = new BinaryStoreGarbageCollectorJob();

        storageContextHelper = Mockito.mockStatic(StorageContextHelper.class);
        storageContextHelper.when(StorageContextHelper::get).thenReturn(storageContext);
        when(storageContext.beanForType(GCService.class)).thenReturn(gcService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storageContextHelper != null) {
            storageContextHelper.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testOnExecuteThrowsJobExecutionException() {
        RuntimeException runtimeException = new RuntimeException("GC failed");
        when(gcService.startGarbageCollect()).thenThrow(runtimeException);

        JobExecutionException exception = assertThrows(JobExecutionException.class,
                () -> job.onExecute(jobContext));

        assertEquals(runtimeException, exception.getCause());
        verify(gcService, times(1)).startGarbageCollect();
    }

    @ParameterizedTest(name = "afterExecute with strategy: {0}")
    @EnumSource(GarbageCollectorStrategy.class)
    void testAfterExecuteWithDifferentStrategies(GarbageCollectorStrategy strategy) throws Exception {
        GarbageCollectorInfo gcInfo = mock(GarbageCollectorInfo.class);
        when(gcInfo.getStrategy()).thenReturn(strategy.name());
        when(gcService.startGarbageCollect()).thenReturn(gcInfo);

        job.onExecute(jobContext);
        clearInvocations(gcService);
        job.afterExecute();

        // Assert - Sharding balancer should only run for FULL GC strategy
        if (strategy == GarbageCollectorStrategy.FULL) {
            verify(gcService, times(1)).runShardingBalancerTask();
        } else {
            verify(gcService, never()).runShardingBalancerTask();
        }
    }

    @Test
    void testAfterExecuteWithNullGcInfo() {
        assertDoesNotThrow(() -> job.afterExecute());
        verify(gcService, never()).runShardingBalancerTask();
    }

    @Test
    void testAfterExecuteWhenOnExecuteFailed() {
        when(gcService.startGarbageCollect()).thenThrow(new RuntimeException("GC failed"));

        assertThrows(JobExecutionException.class, () -> job.onExecute(jobContext));
        assertDoesNotThrow(() -> job.afterExecute());

        // Assert - Sharding balancer should not run when gcInfo is null (due to onExecute failure)
        verify(gcService, never()).runShardingBalancerTask();
    }
}