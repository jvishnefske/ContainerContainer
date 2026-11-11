package org.artifactory.throttling.qrl.type;

import com.google.common.util.concurrent.RateLimiter;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.artifactory.throtlling.qrl.model.QueryRateLimiterMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.artifactory.common.ConstantValues.qrlSimulationModeEnabled;
import static org.artifactory.throtlling.qrl.enums.QueryRateLimiterType.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.getField;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * @author Andrey Tashlitsky
 */
class EnabledQueryRateLimiterTest extends ArtifactoryHomeBoundJUnitTest {

    @BeforeEach
    void setUp() {
        getBound().setProperty(qrlSimulationModeEnabled.getPropertyName(), "false");
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquirePermit(int totalPermits, int timeFrameMillis, int timeQuota) {
        var enabledQueryRateLimiter = new EnabledQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        RateLimiter rateLimiter = spy((RateLimiter) getField(enabledQueryRateLimiter, "queryCountRateLimiter"));
        setField(enabledQueryRateLimiter, "queryCountRateLimiter", rateLimiter);

        enabledQueryRateLimiter.acquirePermit();
        int availablePermits = (int) getField(enabledQueryRateLimiter, "availablePermits");
        assertEquals(totalPermits - 1, availablePermits);
        verify(rateLimiter, times(1)).acquire(totalPermits);
        verify(rateLimiter, times(0)).tryAcquire(totalPermits, 1, NANOSECONDS);

        enabledQueryRateLimiter.acquirePermit();
        availablePermits = (int) getField(enabledQueryRateLimiter, "availablePermits");
        assertEquals(totalPermits - 2, availablePermits);
        verify(rateLimiter, times(1)).acquire(totalPermits);
        verify(rateLimiter, times(0)).tryAcquire(totalPermits, 1, NANOSECONDS);
    }

    @ParameterizedTest
    @MethodSource("acquirePermit")
    void testAcquireTimePermit(int totalPermits, int timeFrameMillis, int timeQuota) {
        var enabledQueryRateLimiter = new EnabledQueryRateLimiter(DEFAULT, totalPermits, timeFrameMillis, timeQuota);
        RateLimiter rateLimiter = spy((RateLimiter) getField(enabledQueryRateLimiter, "queryTimeRateLimiter"));
        setField(enabledQueryRateLimiter, "queryTimeRateLimiter", rateLimiter);
        enabledQueryRateLimiter.acquirePermit();
        enabledQueryRateLimiter.updateExecutionTime(1_000);
        AtomicInteger availableTimeQuota = (AtomicInteger) getField(enabledQueryRateLimiter, "availableTimeQuota");
        assertEquals(timeQuota - 1_000, availableTimeQuota.get());
        verify(rateLimiter, times(1)).acquire(timeQuota);
        verify(rateLimiter, times(0)).tryAcquire(timeQuota, 1_000, NANOSECONDS);

        enabledQueryRateLimiter.acquirePermit();
        enabledQueryRateLimiter.updateExecutionTime(1_000);
        availableTimeQuota = (AtomicInteger) getField(enabledQueryRateLimiter, "availableTimeQuota");
        assertEquals(timeQuota - 2_000, availableTimeQuota.get());
        verify(rateLimiter, times(1)).acquire(timeQuota);
        verify(rateLimiter, times(0)).tryAcquire(timeQuota, 1_000, NANOSECONDS);
    }

    @Test
    void testAcquirePermitWithDelay() {
        var enabledQueryRateLimiter = new EnabledQueryRateLimiter(DEFAULT, 1, 1_000, 2_000);
        RateLimiter rateLimiter = spy((RateLimiter) getField(enabledQueryRateLimiter, "queryCountRateLimiter"));
        setField(enabledQueryRateLimiter, "queryCountRateLimiter", rateLimiter);

        enabledQueryRateLimiter.acquirePermit();
        int availablePermits = (int) getField(enabledQueryRateLimiter, "availablePermits");
        assertEquals(0, availablePermits);
        verify(rateLimiter, times(1)).acquire(1);
        verify(rateLimiter, times(0)).tryAcquire(1, 1, NANOSECONDS);

        enabledQueryRateLimiter.acquirePermit();
        availablePermits = (int) getField(enabledQueryRateLimiter, "availablePermits");
        assertEquals(0, availablePermits);
        verify(rateLimiter, times(2)).acquire(1);
        verify(rateLimiter, times(0)).tryAcquire(1, 1, NANOSECONDS);
    }

    @Test
    void testAcquireTimePermitWithDelay() {
        var enabledQueryRateLimiter = new EnabledQueryRateLimiter(DEFAULT, 1, 1_000, 1_000);
        RateLimiter rateLimiter = spy((RateLimiter) getField(enabledQueryRateLimiter, "queryTimeRateLimiter"));
        setField(enabledQueryRateLimiter, "queryTimeRateLimiter", rateLimiter);

        enabledQueryRateLimiter.acquirePermit();
        enabledQueryRateLimiter.updateExecutionTime(1_000);
        AtomicInteger availableTimeQuota = (AtomicInteger) getField(enabledQueryRateLimiter, "availableTimeQuota");
        assertEquals(0, availableTimeQuota.get());
        verify(rateLimiter, times(1)).acquire(1_000);
        verify(rateLimiter, times(0)).tryAcquire(1, 1, NANOSECONDS);

        enabledQueryRateLimiter.acquirePermit();
        enabledQueryRateLimiter.updateExecutionTime(1_000);
        availableTimeQuota = (AtomicInteger) getField(enabledQueryRateLimiter, "availableTimeQuota");
        assertEquals(0, availableTimeQuota.get());
        verify(rateLimiter, times(2)).acquire(1_000);
        verify(rateLimiter, times(0)).tryAcquire(1, 1, NANOSECONDS);
    }

    @Test
    void testGetMetrics() {
        var enabledQueryRateLimiter = new EnabledQueryRateLimiter(DEFAULT, 1, 1_000, 2_000);
        QueryRateLimiterMetrics qrlMetrics = enabledQueryRateLimiter.getMetrics();
        assertEquals(0, qrlMetrics.getTotalSlowedDownMillis());
        assertEquals(0, qrlMetrics.getTotalQueries());
    }

    private static Stream<Arguments> acquirePermit() {
        return Stream.of(
                Arguments.of(10_000, 1_000, 2_000),
                Arguments.of(10_000, 5_000, 2_000),
                Arguments.of(10_000, 1_000, 2_000)
        );
    }
}
