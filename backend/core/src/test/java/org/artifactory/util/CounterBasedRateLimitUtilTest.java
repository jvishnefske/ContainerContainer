package org.artifactory.util;

import lombok.SneakyThrows;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Mor Merhav
 */
public class CounterBasedRateLimitUtilTest {

    private MockitoSession mockitoSession;
    private CounterBasedRateLimitUtil rateLimiter;

    @Mock
    private Runnable mockRunnable;
    @Mock
    private Callable<Boolean> mockCallable;

    @BeforeMethod
    public void setUp() throws Exception {
        mockitoSession = Mockito.mockitoSession()
                .strictness(Strictness.STRICT_STUBS)
                .initMocks(this)
                .startMocking();
    }

    @AfterMethod
    public void tearDown() {
        mockitoSession.finishMocking();
    }

    @Test
    public void testExecute10TimesWithRateInterval10Executed1Time() {
        long interval = 10;
        rateLimiter = new CounterBasedRateLimitUtil(interval);

        for (int i = 0; i < interval; i++) {
            rateLimiter.executeWithRateLimit(mockRunnable);
        }

        verify(mockRunnable, times(1)).run();
    }

    @Test
    public void testExecute20TimesWithRateInterval10Executed2Times() {
        long interval = 10;
        rateLimiter = new CounterBasedRateLimitUtil(interval);

        for (int i = 0; i < interval * 2; i++) {
            rateLimiter.executeWithRateLimit(mockRunnable);
        }

        verify(mockRunnable, times(2)).run();
    }

    @Test
    public void testExecute9TimesWithRateInterval10Executed1Times() {
        long interval = 10;
        rateLimiter = new CounterBasedRateLimitUtil(interval);

        for (int i = 0; i < 9; i++) {
            rateLimiter.executeWithRateLimit(mockRunnable);
        }

        verify(mockRunnable, times(1)).run();
    }

    @Test
    @SneakyThrows
    public void testCall10TimesWithRateInterval10Called1Time() {
        long interval = 10;
        rateLimiter = new CounterBasedRateLimitUtil(interval);

        for (int i = 0; i < interval; i++) {
            rateLimiter.callWithRateLimit(mockCallable, false);
        }

        verify(mockCallable, times(1)).call();
    }

    @Test
    @SneakyThrows
    public void testCall20TimesWithRateInterval10Called2Times() {
        long interval = 10;
        rateLimiter = new CounterBasedRateLimitUtil(interval);

        for (int i = 0; i < interval * 2; i++) {
            rateLimiter.callWithRateLimit(mockCallable, false);
        }

        verify(mockCallable, times(2)).call();
    }

    @Test
    @SneakyThrows
    public void testCall9TimesWithRateInterval10Called1Times() {
        long interval = 10;
        rateLimiter = new CounterBasedRateLimitUtil(interval);

        for (int i = 0; i < 9; i++) {
            rateLimiter.callWithRateLimit(mockCallable, false);
        }

        verify(mockCallable, times(1)).call();
    }
}