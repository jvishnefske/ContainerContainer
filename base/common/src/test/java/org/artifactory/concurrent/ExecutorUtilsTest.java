package org.artifactory.concurrent;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutorUtilsTest {

    // --- shutdownWithin tests ---

    @Test
    void shutdownWithin_shouldShutdownGracefully_whenExecutorTerminatesWithinTimeout() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS);

        verify(executor).shutdown();
        verify(executor).awaitTermination(5, TimeUnit.SECONDS);
        verify(executor, never()).shutdownNow();
    }

    @Test
    void shutdownWithin_shouldForceShutdown_whenExecutorDoesNotTerminateWithinTimeout() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);

        ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS);

        verify(executor).shutdown();
        verify(executor).awaitTermination(5, TimeUnit.SECONDS);
        verify(executor).shutdownNow();
    }

    @Test
    void shutdownWithin_shouldForceShutdownAndRestoreInterrupt_whenInterrupted() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("test interrupt"));

        ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS);

        verify(executor).shutdown();
        verify(executor).shutdownNow();
        assertTrue(Thread.currentThread().isInterrupted());

        Thread.interrupted();
    }

    @Test
    void shutdownWithin_shouldRunCleanup_whenShutdownSucceeds() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);
        AtomicBoolean cleanupRan = new AtomicBoolean(false);

        ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS, () -> cleanupRan.set(true));

        assertTrue(cleanupRan.get());
    }

    @Test
    void shutdownWithin_shouldRunCleanup_whenShutdownTimesOut() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(false);
        AtomicBoolean cleanupRan = new AtomicBoolean(false);

        ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS, () -> cleanupRan.set(true));

        assertTrue(cleanupRan.get());
    }

    @Test
    void shutdownWithin_shouldRunCleanup_whenInterrupted() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("test interrupt"));
        AtomicBoolean cleanupRan = new AtomicBoolean(false);

        ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS, () -> cleanupRan.set(true));

        assertTrue(cleanupRan.get());

        Thread.interrupted();
    }

    @Test
    void shutdownWithin_shouldWorkWithRealExecutor_whenTaskCompletes() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {});

        assertDoesNotThrow(() -> ExecutorUtils.shutdownWithin(executor, 5, TimeUnit.SECONDS));
        assertTrue(executor.isShutdown());
    }

    // --- submitAndAwait tests ---

    @Test
    void submitAndAwait_shouldCompleteSuccessfully_whenTaskFinishesWithinTimeout() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean taskRan = new AtomicBoolean(false);
        AtomicBoolean onFailureRan = new AtomicBoolean(false);

        ExecutorUtils.submitAndAwait(executor, () -> taskRan.set(true),
                5, TimeUnit.SECONDS, () -> onFailureRan.set(true));

        assertTrue(taskRan.get());
        assertFalse(onFailureRan.get());
        assertTrue(executor.isShutdown());
    }

    @Test
    void submitAndAwait_shouldInvokeOnFailure_whenTaskTimesOut() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean onFailureRan = new AtomicBoolean(false);

        ExecutorUtils.submitAndAwait(executor, () -> {
            try {
                // Block longer than the timeout
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, 1, TimeUnit.MILLISECONDS, () -> onFailureRan.set(true));

        assertTrue(onFailureRan.get());
        assertTrue(executor.isShutdown());
    }

    @Test
    void submitAndAwait_shouldInvokeOnFailure_whenTaskThrows() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicBoolean onFailureRan = new AtomicBoolean(false);

        ExecutorUtils.submitAndAwait(executor, () -> {
            throw new RuntimeException("task failed");
        }, 5, TimeUnit.SECONDS, () -> onFailureRan.set(true));

        assertTrue(onFailureRan.get());
        assertTrue(executor.isShutdown());
    }

    @Test
    void submitAndAwait_shouldNotThrow_whenOnFailureIsNull() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        assertDoesNotThrow(() -> ExecutorUtils.submitAndAwait(executor, () -> {
            throw new RuntimeException("task failed");
        }, 5, TimeUnit.SECONDS, null));

        assertTrue(executor.isShutdown());
    }

    @Test
    void submitAndAwait_shouldShutdownExecutor_whenTaskSucceeds() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        ExecutorUtils.submitAndAwait(executor, () -> {}, 5, TimeUnit.SECONDS, null);

        assertTrue(executor.isShutdown());
    }

    @Test
    void submitAndAwait_shouldShutdownExecutor_whenTaskFails() {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        ExecutorUtils.submitAndAwait(executor, () -> {
            throw new RuntimeException("boom");
        }, 5, TimeUnit.SECONDS, null);

        assertTrue(executor.isShutdown());
    }

    @Test
    void submitAndAwait_shouldHandleInterrupt_whenWaitingForTask() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch taskStarted = new CountDownLatch(1);
        AtomicBoolean onFailureRan = new AtomicBoolean(false);

        Thread testThread = new Thread(() ->
                ExecutorUtils.submitAndAwait(executor, () -> {
                    taskStarted.countDown();
                    try {
                        TimeUnit.SECONDS.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, 30, TimeUnit.SECONDS, () -> onFailureRan.set(true)));

        testThread.start();
        taskStarted.await(5, TimeUnit.SECONDS);
        TimeUnit.MILLISECONDS.sleep(100);
        testThread.interrupt();
        testThread.join(5000);

        assertTrue(onFailureRan.get());
    }
}
