package org.artifactory.storage.db.locks.service;

import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for PrematureLockReleaseDetector focusing on real-world scenarios
 * and timing-sensitive behavior.
 */
@ExtendWith(MockitoExtension.class)
class PrematureLockReleaseDetectorTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private DbLock dbLock;

    private ScheduledExecutorService scheduler;
    private HaInitLock.PrematureLockReleaseDetector detector;

    @BeforeEach
    void setUp() {
        when(dbLock.getLockKey()).thenReturn("ha-init-lock");
        detector = spy(new HaInitLock.PrematureLockReleaseDetector(dbLock));
        scheduler = Executors.newScheduledThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(5)
    void testDetector_ScheduledExecution_LockHeldDuringEntireInit() {
        // Arrange
        when(dbLock.isLockedByMe()).thenReturn(true);

        // Act - run detector every 100ms
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                detector, 0, 100, TimeUnit.MILLISECONDS);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(
                () -> verify(dbLock, atLeast(1)).isLockedByMe());

        future.cancel(true);
    }

    @Test
    @Timeout(5)
    void testDetector_DetectsLockLossDuringInitialization() throws Exception {
        // Detector shouldn't actually kill JVM in test
        doNothing().when(detector).systemExit();

        // Simulate lock loss (without marking as properly released)
        CyclicBarrier barrier = new CyclicBarrier(2, () -> {
            when(dbLock.isLockedByMe()).thenReturn(false);
        });

        // Act - run concurrently
        scheduler.submit(() -> {
            try {
                barrier.await();
                detector.run();
            } catch (Exception e) {
                // Ignore
            }
        });

        barrier.await();
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        verify(dbLock, atLeast(1)).isLockedByMe();
        verify(detector, atLeast(1)).systemExit();
    }

    @Test
    @Timeout(5)
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testDetector_ConcurrentMarkReleaseAndRun() throws Exception {
        // Start with lock held
        when(dbLock.isLockedByMe()).thenReturn(true);

        // Detector shouldn't actually kill JVM in test
        doNothing().when(detector).systemExit();

        CyclicBarrier barrier = new CyclicBarrier(2, () -> {
            detector.markRelease();
            when(dbLock.isLockedByMe()).thenReturn(false);
        });

        // Act - run concurrently
        scheduler.submit(() -> {
            try {
                barrier.await();
                detector.run();
            } catch (Exception e) {
                // Ignore
            }
        });

        barrier.await();

        scheduler.shutdown();
        scheduler.awaitTermination(3, TimeUnit.SECONDS);
        verify(detector, times(0)).systemExit();
    }
}