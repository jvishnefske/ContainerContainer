package org.artifactory.ui.rest.service.artifacts.search.syntax;

import org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchOperations.StageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Igor Usenko
 */
class SyntaxSearchServiceRaceTest {

    private static final long SLOW_MILLIS = 500;

    private SyntaxSearchService service;

    @BeforeEach
    void setUp() {
        service = new SyntaxSearchService(null, null, null, null, null, null, null);
    }

    @Test
    void bothSucceedClean_returnsACleanResultFromOneOfTheVariants() {
        StageResult<String> disabledResult = clean(List.of("ds"), true);
        StageResult<String> enabledResult = clean(List.of("en"), false);

        StageResult<String> winner = service.race(disableIndexes -> disableIndexes ? disabledResult : enabledResult);

        assertFalse(winner.hasTimeout());
        assertTrue(winner == disabledResult || winner == enabledResult,
                "winner must be one of the two clean results");
    }

    @Test
    void bothSucceedClean_fastDisabledVariantWinsOverSlowEnabledVariant() {
        StageResult<String> winner = service.race(disableIndexes -> {
            if (!disableIndexes) {
                sleep(SLOW_MILLIS);
            }
            return clean(List.of(disableIndexes ? "ds" : "en"), disableIndexes);
        });

        assertEquals(List.of("ds"), winner.rows());
        assertTrue(winner.disableIndexesUsed());
        assertFalse(winner.hasTimeout());
    }

    @Test
    void bothSucceedClean_fastEnabledVariantWinsOverSlowDisabledVariant() {
        StageResult<String> winner = service.race(disableIndexes -> {
            if (disableIndexes) {
                sleep(SLOW_MILLIS);
            }
            return clean(List.of(disableIndexes ? "ds" : "en"), disableIndexes);
        });

        assertEquals(List.of("en"), winner.rows());
        assertFalse(winner.disableIndexesUsed());
        assertFalse(winner.hasTimeout());
    }

    @Test
    void invokesBothPlannerVariantsExactlyOnce() throws InterruptedException {
        Set<Boolean> seen = ConcurrentHashMap.newKeySet();
        AtomicInteger invocations = new AtomicInteger();
        CountDownLatch bothInvoked = new CountDownLatch(2);

        service.race(disableIndexes -> {
            seen.add(disableIndexes);
            invocations.incrementAndGet();
            bothInvoked.countDown();
            return clean(List.of("x"), disableIndexes);
        });

        assertTrue(bothInvoked.await(10, TimeUnit.SECONDS), "both planner variants should be invoked");
        assertEquals(Set.of(true, false), seen);
        assertEquals(2, invocations.get());
    }

    @Test
    void disabledVariantFails_enabledCleanResultWins() {
        StageResult<String> enabledResult = clean(List.of("en"), false);

        StageResult<String> winner = service.race(disableIndexes -> {
            if (disableIndexes) {
                throw new RuntimeException("disabled variant failed");
            }
            return enabledResult;
        });

        assertSame(enabledResult, winner);
    }

    @Test
    void enabledVariantFails_disabledCleanResultWins() {
        StageResult<String> disabledResult = clean(List.of("ds"), true);

        StageResult<String> winner = service.race(disableIndexes -> {
            if (!disableIndexes) {
                throw new RuntimeException("enabled variant failed");
            }
            return disabledResult;
        });

        assertSame(disabledResult, winner);
    }

    @Test
    void bothVariantsFail_propagatesTheDisabledVariantErrorWrapped() {
        RuntimeException disabledError = new RuntimeException("disabled boom");
        RuntimeException enabledError = new RuntimeException("enabled boom");

        SyntaxSearchServiceException thrown = assertThrows(SyntaxSearchServiceException.class,
                () -> service.race(disableIndexes -> {
                    throw disableIndexes ? disabledError : enabledError;
                }));

        Throwable cause = thrown.getCause();
        assertInstanceOf(ExecutionException.class, cause, "expected ExecutionException, got " + cause);
        assertSame(disabledError, cause.getCause());
    }

    @Test
    void cleanResultIsPreferredOverTimedOutResult() {
        StageResult<String> enabledClean = clean(List.of("en"), false);

        StageResult<String> winner = service.race(disableIndexes -> {
            if (disableIndexes) {
                sleep(SLOW_MILLIS);
                return timedOut(List.of("ds"), true);
            }
            return enabledClean;
        });

        assertSame(enabledClean, winner);
        assertFalse(winner.hasTimeout());
    }

    @Test
    void cleanResultIsPreferredEvenWhenItFinishesLast() {
        StageResult<String> enabledClean = clean(List.of("en"), false);

        StageResult<String> winner = service.race(disableIndexes -> {
            if (disableIndexes) {
                return timedOut(List.of("ds"), true);
            }
            sleep(SLOW_MILLIS);
            return enabledClean;
        });

        assertSame(enabledClean, winner);
        assertFalse(winner.hasTimeout());
    }

    @Test
    void bothTimeOut_fallsBackToTheDisabledPartialResult() {
        StageResult<String> disabledTimeout = timedOut(List.of("ds"), true);
        StageResult<String> enabledTimeout = timedOut(List.of("en"), false);

        StageResult<String> winner =
                service.race(disableIndexes -> disableIndexes ? disabledTimeout : enabledTimeout);

        assertSame(disabledTimeout, winner);
        assertTrue(winner.hasTimeout());
    }

    @Test
    void disabledTimedOutAndEnabledFailed_fallsBackToTheDisabledPartialResult() {
        StageResult<String> disabledTimeout = timedOut(List.of("ds"), true);

        StageResult<String> winner = service.race(disableIndexes -> {
            if (disableIndexes) {
                return disabledTimeout;
            }
            throw new RuntimeException("enabled variant failed");
        });

        assertSame(disabledTimeout, winner);
        assertTrue(winner.hasTimeout());
    }

    @Test
    void disabledFailedAndEnabledTimedOut_fallsBackToTheEnabledPartialResult() {
        StageResult<String> enabledTimeout = timedOut(List.of("en"), false);

        StageResult<String> winner = service.race(disableIndexes -> {
            if (disableIndexes) {
                throw new RuntimeException("disabled variant failed");
            }
            return enabledTimeout;
        });

        assertSame(enabledTimeout, winner);
        assertTrue(winner.hasTimeout());
    }

    private static StageResult<String> clean(List<String> rows, boolean disableIndexesUsed) {
        return new StageResult<>(rows, false, disableIndexesUsed);
    }

    private static StageResult<String> timedOut(List<String> rows, boolean disableIndexesUsed) {
        return new StageResult<>(rows, true, disableIndexesUsed);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
