package org.artifactory.util;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RetryHelperTest {

    @Test
    void execute_shouldReturnTrue_whenStopConditionMetOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(5)
                .retryIntervalMillis(0)
                .operation(attempts::incrementAndGet)
                .stopCondition(() -> true)
                .build()
                .execute();

        assertTrue(result);
        assertEquals(1, attempts.get());
    }

    @Test
    void execute_shouldReturnTrue_whenStopConditionMetAfterMultipleAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(5)
                .retryIntervalMillis(0)
                .operation(attempts::incrementAndGet)
                .stopCondition(() -> attempts.get() >= 3)
                .build()
                .execute();

        assertTrue(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void execute_shouldReturnFalse_whenRetriesExhausted() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(3)
                .retryIntervalMillis(0)
                .operation(attempts::incrementAndGet)
                .stopCondition(() -> false)
                .build()
                .execute();

        assertFalse(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void execute_shouldReturnFalse_whenInterruptedDuringSleep() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        Thread testThread = Thread.currentThread();

        Thread interruptor = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            testThread.interrupt();
        });
        interruptor.start();

        boolean result = RetryHelper.builder()
                .maxRetries(100)
                .retryIntervalMillis(5000)
                .operation(attempts::incrementAndGet)
                .stopCondition(() -> false)
                .build()
                .execute();

        interruptor.join();
        assertFalse(result);
        assertTrue(Thread.interrupted());
    }

    @Test
    void execute_shouldAcceptLoggerAndDescription() {
        boolean result = RetryHelper.builder()
                .maxRetries(1)
                .retryIntervalMillis(0)
                .operation(() -> {})
                .stopCondition(() -> true)
                .logger(mock(Logger.class))
                .description("Test operation")
                .build()
                .execute();

        assertTrue(result);
    }

    @Test
    void execute_shouldContinueRetrying_whenOperationThrowsException() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(3)
                .retryIntervalMillis(0)
                .operation(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("transient failure");
                })
                .stopCondition(() -> false)
                .build()
                .execute();

        assertFalse(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void execute_shouldSucceed_whenOperationFailsThenRecovers() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(5)
                .retryIntervalMillis(0)
                .operation(() -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new RuntimeException("not ready yet");
                    }
                })
                .stopCondition(() -> attempts.get() >= 3)
                .build()
                .execute();

        assertTrue(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void execute_shouldCheckStopCondition_evenWhenOperationThrows() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(5)
                .retryIntervalMillis(0)
                .operation(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("always fails");
                })
                .stopCondition(() -> attempts.get() >= 2)
                .build()
                .execute();

        assertTrue(result);
        assertEquals(2, attempts.get());
    }

    @Test
    void build_shouldThrow_whenMaxRetriesNotPositive() {
        assertThrows(IllegalArgumentException.class, () ->
                RetryHelper.builder()
                        .maxRetries(0)
                        .retryIntervalMillis(0)
                        .operation(() -> {})
                        .stopCondition(() -> true)
                        .build());
    }

    @Test
    void build_shouldThrow_whenOperationIsNull() {
        assertThrows(NullPointerException.class, () ->
                RetryHelper.builder()
                        .maxRetries(1)
                        .retryIntervalMillis(0)
                        .stopCondition(() -> true)
                        .build());
    }

    @Test
    void build_shouldThrow_whenStopConditionIsNull() {
        assertThrows(NullPointerException.class, () ->
                RetryHelper.builder()
                        .maxRetries(1)
                        .retryIntervalMillis(0)
                        .operation(() -> {})
                        .build());
    }

    @Test
    void build_shouldThrow_whenRetryIntervalMillisIsNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                RetryHelper.builder()
                        .maxRetries(1)
                        .retryIntervalMillis(-1)
                        .operation(() -> {})
                        .stopCondition(() -> true)
                        .build());
    }

    @Test
    void execute_shouldWorkWithoutLoggerOrDescription() {
        // Both logger and description are null
        boolean result = RetryHelper.builder()
                .maxRetries(2)
                .retryIntervalMillis(0)
                .operation(() -> {})
                .stopCondition(() -> false)
                .build()
                .execute();

        assertFalse(result);
    }

    @Test
    void execute_shouldWorkWithLoggerButNoDescription() {
        boolean result = RetryHelper.builder()
                .maxRetries(1)
                .retryIntervalMillis(0)
                .operation(() -> {})
                .stopCondition(() -> true)
                .logger(mock(Logger.class))
                .build()
                .execute();

        assertTrue(result);
    }

    @Test
    void execute_shouldWorkWithDescriptionButNoLogger() {
        boolean result = RetryHelper.builder()
                .maxRetries(1)
                .retryIntervalMillis(0)
                .operation(() -> {})
                .stopCondition(() -> true)
                .description("orphan cleanup")
                .build()
                .execute();

        assertTrue(result);
    }

    @Test
    void execute_shouldLogWarning_whenOperationThrowsWithLoggerAndDescription() {
        // Exercises the warn path in the catch block with both logger and description set
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(2)
                .retryIntervalMillis(0)
                .operation(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("boom");
                })
                .stopCondition(() -> false)
                .logger(mock(Logger.class))
                .description("Flaky operation")
                .build()
                .execute();

        assertFalse(result);
        assertEquals(2, attempts.get());
    }

    @Test
    void execute_shouldContinueRetrying_whenStopConditionThrowsException() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(3)
                .retryIntervalMillis(0)
                .operation(attempts::incrementAndGet)
                .stopCondition(() -> { throw new RuntimeException("check failed"); })
                .build()
                .execute();

        assertFalse(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void execute_shouldSucceed_whenStopConditionFailsThenRecovers() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(5)
                .retryIntervalMillis(0)
                .operation(attempts::incrementAndGet)
                .stopCondition(() -> {
                    if (attempts.get() < 3) {
                        throw new RuntimeException("not ready");
                    }
                    return true;
                })
                .build()
                .execute();

        assertTrue(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void execute_shouldContinue_whenBothOperationAndStopConditionThrow() {
        AtomicInteger attempts = new AtomicInteger();

        boolean result = RetryHelper.builder()
                .maxRetries(2)
                .retryIntervalMillis(0)
                .operation(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("op failed");
                })
                .stopCondition(() -> { throw new RuntimeException("check failed"); })
                .logger(mock(Logger.class))
                .description("Double failure")
                .build()
                .execute();

        assertFalse(result);
        assertEquals(2, attempts.get());
    }
}
