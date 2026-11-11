package org.artifactory.throttling.rrl.strategy;


import com.google.common.util.concurrent.RateLimiter;
import org.artifactory.download.RateLimiterException;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.artifactory.common.ConstantValues.rrlGenericIsBlockingModeEnabled;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

class GenericRequestRateLimiterTest extends ArtifactoryHomeBoundJUnitTest {

    private final static RequestRateLimiterHost DOCKERHUB = new RequestRateLimiterHost(".docker.io", "dockerhub");
    private final static RequestRateLimiterHost GCR = new RequestRateLimiterHost(".gcr.io", "gcr");
    private final static RequestRateLimiterHost QUAY = new RequestRateLimiterHost(".quay.io", "quay");

    @BeforeEach
    void setUp() {
        getBound().setProperty(rrlGenericIsBlockingModeEnabled.getPropertyName(), "true");
    }

    @ParameterizedTest
    @MethodSource("acquirePermitArgsProvider")
    void testAcquirePermit(int totalPermits, int timeFrameMillis, boolean isSimulation) {
        getBound().setProperty(rrlGenericIsBlockingModeEnabled.getPropertyName(), isSimulation ? "false" : "true");

        var dropRateLimiter
                =
                GenericRequestRateLimiter.create(DOCKERHUB.name(), DOCKERHUB.domainSuffix(), totalPermits,
                        timeFrameMillis, isSimulation);
        RateLimiter rateLimiter = spy((RateLimiter) getField(dropRateLimiter, "rateLimiter"));
        setField(dropRateLimiter, "rateLimiter", rateLimiter);

        dropRateLimiter.acquirePermit();
        int availablePermits = getAvailablePermits(dropRateLimiter);
        assertEquals(totalPermits - 1, availablePermits);
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);

        dropRateLimiter.acquirePermit();
        availablePermits = getAvailablePermits(dropRateLimiter);
        assertEquals(totalPermits - 2, availablePermits);
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
    }

    @ParameterizedTest
    @MethodSource("acquirePermitArgsProvider")
    void testAcquirePermitSimulationModeNegativeFlow(int totalPermits, int timeFrameMillis, boolean isSimulation) {
        var dropRateLimiter =
                GenericRequestRateLimiter.create(DOCKERHUB.name(), DOCKERHUB.domainSuffix(), totalPermits,
                        timeFrameMillis, isSimulation);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        setField(dropRateLimiter, "rateLimiter", rateLimiter);
        when(rateLimiter.tryAcquire(anyInt(), anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);

        getBound().setProperty(rrlGenericIsBlockingModeEnabled.getPropertyName(), isSimulation ? "false" : "true");
        if (!isSimulation) {
            assertThrows(RateLimiterException.class, dropRateLimiter::acquirePermit);
            return;
        }

        dropRateLimiter.acquirePermit();
        dropRateLimiter.acquirePermit();
        verify(rateLimiter, times(2)).tryAcquire(totalPermits, 1, NANOSECONDS);
        AtomicLong dropCount = (AtomicLong) getField(dropRateLimiter, "rateLimitedRequestsCounter");
        assert dropCount != null;
        assertEquals(2, dropCount.get());
    }

    // Slow - Just to understand the concept.
    @Test
    void rateLimiter() throws InterruptedException {
        // 30 requests in 3000 ms which is 30 requests in 3 seconds
        var genericRateLimiter =
                GenericRequestRateLimiter.create(DOCKERHUB.name(), DOCKERHUB.domainSuffix(), 30, 3000, false);

        // Do all 30 in less than a sec
        for (int i = 0; i < 30; i++) {
            genericRateLimiter.acquirePermit();
        }
        AtomicLong totalRequestsOutCounter =
                (AtomicLong) getField(genericRateLimiter, "totalUnblockedOutgoingRequestsCounter");
        assert totalRequestsOutCounter != null;
        assertEquals(30, totalRequestsOutCounter.get());

        // Get assertion for 31
        assertThrows(RateLimiterException.class, genericRateLimiter::acquirePermit);
        // Wait 2 sec
        Thread.sleep(2000);
        assertThrows(RateLimiterException.class, genericRateLimiter::acquirePermit);
        // Wait 1 sec
        Thread.sleep(1000);

        // Successful as 3 sec passed
        assertDoesNotThrow(() -> genericRateLimiter.acquirePermit());
        totalRequestsOutCounter = (AtomicLong) getField(genericRateLimiter, "totalUnblockedOutgoingRequestsCounter");


        AtomicLong dropCount = (AtomicLong) getField(genericRateLimiter, "rateLimitedRequestsCounter");
        assert dropCount != null;
        assertEquals(2, dropCount.get());

        var metrics = genericRateLimiter.getMetrics();
        assertEquals(2, metrics.get("Drop Count"));
    }

    private static Stream<Arguments> acquirePermitArgsProvider() {
        return Stream.of(
                Arguments.of(10_000, 1_000, true),
                Arguments.of(10_000, 5_000, true),
                Arguments.of(10_000, 500, true),
                Arguments.of(10_000, 1_000, false),
                Arguments.of(10_000, 5_000, false),
                Arguments.of(10_000, 500, false)
        );
    }

    private static int getAvailablePermits(GenericRequestRateLimiter dropRateLimiter) {
        return (int) getField(dropRateLimiter, "availablePermits");
    }

    public record RequestRateLimiterHost(String domainSuffix, String name) {
    }
}