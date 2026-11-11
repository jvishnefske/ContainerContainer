package org.artifactory.throttling.qrl.type;

import com.google.common.util.concurrent.RateLimiter;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.artifactory.common.ConstantValues.qrlSimulationModeEnabled;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

/**
 * @author Andrey Tashlitsky
 */
class SimulationQueryRateLimiterTest extends ArtifactoryHomeBoundJUnitTest {

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireCountPermitAcquiredNoRateLimiting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        setField(simulationQueryRateLimiter, "simModeFailedAcquireCountTime", 1_000L);
        RateLimiter rateLimiter = spy((RateLimiter) getField(simulationQueryRateLimiter, "queryCountRateLimiter"));
        setField(simulationQueryRateLimiter, "queryCountRateLimiter", rateLimiter);

        simulationQueryRateLimiter.acquirePermit();
        int availablePermits = (int) getField(simulationQueryRateLimiter, "availablePermits");
        assertEquals(totalPermits - 1, availablePermits);
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);

        simulationQueryRateLimiter.acquirePermit();
        availablePermits = (int) getField(simulationQueryRateLimiter, "availablePermits");
        assertEquals(totalPermits - 2, availablePermits);
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireTimePermitAcquiredNoRateLimiting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        setField(simulationQueryRateLimiter, "simModeFailedQuotaAcquireTime", 1_000L);
        RateLimiter rateLimiter = spy((RateLimiter) getField(simulationQueryRateLimiter, "queryTimeRateLimiter"));
        setField(simulationQueryRateLimiter, "queryTimeRateLimiter", rateLimiter);

        simulationQueryRateLimiter.acquirePermit();
        simulationQueryRateLimiter.updateExecutionTime(1_000);
        AtomicInteger availableTimeQuota = (AtomicInteger) getField(simulationQueryRateLimiter, "availableTimeQuota");
        assertEquals(timeQuota - 1_000, availableTimeQuota.get());
        verify(rateLimiter, times(1)).tryAcquire(timeQuota, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(timeQuota);

        simulationQueryRateLimiter.acquirePermit();
        simulationQueryRateLimiter.updateExecutionTime(1_000);
        availableTimeQuota = (AtomicInteger) getField(simulationQueryRateLimiter, "availableTimeQuota");
        assertEquals(timeQuota - 2_000, availableTimeQuota.get());
        verify(rateLimiter, times(1)).tryAcquire(timeQuota, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(timeQuota);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireCountPermitAcquiredStopCounting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        setField(simulationQueryRateLimiter, "simModeFailedAcquireCountTime", 0);
        RateLimiter rateLimiter = spy((RateLimiter) getField(simulationQueryRateLimiter, "queryCountRateLimiter"));
        setField(simulationQueryRateLimiter, "queryCountRateLimiter", rateLimiter);

        simulationQueryRateLimiter.acquirePermit();
        int availablePermits = (int) getField(simulationQueryRateLimiter, "availablePermits");
        assertEquals(totalPermits - 1, availablePermits);
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);

        simulationQueryRateLimiter.acquirePermit();
        availablePermits = (int) getField(simulationQueryRateLimiter, "availablePermits");
        assertEquals(totalPermits - 2, availablePermits);
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireTimePermitAcquiredStopCounting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        setField(simulationQueryRateLimiter, "simModeFailedQuotaAcquireTime", 0);
        RateLimiter rateLimiter = spy((RateLimiter) getField(simulationQueryRateLimiter, "queryTimeRateLimiter"));
        setField(simulationQueryRateLimiter, "queryTimeRateLimiter", rateLimiter);

        simulationQueryRateLimiter.acquirePermit();
        simulationQueryRateLimiter.updateExecutionTime(1_000);
        AtomicInteger availableTimeQuota = ((AtomicInteger) getField(simulationQueryRateLimiter, "availableTimeQuota"));
        assertEquals(timeQuota - 1_000, availableTimeQuota.get());
        verify(rateLimiter, times(1)).tryAcquire(timeQuota, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);

        simulationQueryRateLimiter.acquirePermit();
        simulationQueryRateLimiter.updateExecutionTime(1_000);
        availableTimeQuota = (AtomicInteger) getField(simulationQueryRateLimiter, "availableTimeQuota");
        assertEquals(timeQuota - 2_000, availableTimeQuota.get());
        verify(rateLimiter, times(1)).tryAcquire(timeQuota, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireCountPermitNotAcquiredStartCounting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        setField(simulationQueryRateLimiter, "queryCountRateLimiter", rateLimiter);
        setField(simulationQueryRateLimiter, "simModeFailedAcquireCountTime", 0L);
        when(rateLimiter.tryAcquire(anyInt(), anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);
        simulationQueryRateLimiter.acquirePermit();
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireTimePermitNotAcquiredStartCounting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        setField(simulationQueryRateLimiter, "queryTimeRateLimiter", rateLimiter);
        setField(simulationQueryRateLimiter, "simModeFailedQuotaAcquireTime", 0L);
        when(rateLimiter.tryAcquire(anyInt(), anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);
        simulationQueryRateLimiter.updateExecutionTime(1_000);
        simulationQueryRateLimiter.acquirePermit();
        verify(rateLimiter, times(1)).tryAcquire(timeQuota, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireCountPermitNotAcquiredStillCounting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        setField(simulationQueryRateLimiter, "queryCountRateLimiter", rateLimiter);
        setField(simulationQueryRateLimiter, "simModeFailedAcquireCountTime", 500L);
        when(rateLimiter.tryAcquire(anyInt(), anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);
        simulationQueryRateLimiter.acquirePermit();
        verify(rateLimiter, times(1)).tryAcquire(totalPermits, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(totalPermits);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireTimePermitNotAcquiredStillCounting(int totalPermits, int timeFrameMillis, int timeQuota) {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "true");
        var simulationQueryRateLimiter =
                new SimulationQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        RateLimiter rateLimiter = mock(RateLimiter.class);
        setField(simulationQueryRateLimiter, "queryTimeRateLimiter", rateLimiter);
        setField(simulationQueryRateLimiter, "simModeFailedQuotaAcquireTime", 500L);
        when(rateLimiter.tryAcquire(anyInt(), anyLong(), eq(TimeUnit.NANOSECONDS))).thenReturn(false);
        simulationQueryRateLimiter.updateExecutionTime(1_000);
        simulationQueryRateLimiter.acquirePermit();
        verify(rateLimiter, times(1)).tryAcquire(timeQuota, 1, NANOSECONDS);
        verify(rateLimiter, times(0)).acquire(timeQuota);
    }

    private static Stream<Arguments> acquirePermit() {
        return Stream.of(
                Arguments.of(10_000, 1_000, 2_000),
                Arguments.of(10_000, 5_000, 2_000),
                Arguments.of(10_000, 1_000, 2_000)
        );
    }
}
