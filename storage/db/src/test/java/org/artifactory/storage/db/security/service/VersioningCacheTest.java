package org.artifactory.storage.db.security.service;

import org.artifactory.test.TestUtils;
import org.jfrog.common.concurrency.ConflictGuard;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Shay Bagants
 */
class VersioningCacheTest {

    @Test
    void testVersionCache() {
        AtomicInteger timesCallableCalled = new AtomicInteger(0);
        Callable<BasicCacheModel> callable = getCacheCallable(timesCallableCalled);
        VersionCache<BasicCacheModel> versioningCacheImpl = new VersioningCacheImpl<>(20000, callable, "cool-cache");
        // call the cache twice, ensure it never reached the callable more than once
        versioningCacheImpl.get();
        BasicCacheModel cache = versioningCacheImpl.get();
        assertEquals(1, cache.getVersion());
        // validate callable called only once
        assertEquals(1, timesCallableCalled.get());

        versioningCacheImpl.promoteVersion();
        // call the cache twice, ensure it never reached the callable more than once
        versioningCacheImpl.get();
        cache = versioningCacheImpl.get();
        assertEquals(2, cache.getVersion());
        // validate callable called only 2 times
        assertEquals(2, timesCallableCalled.get());

        // promote the cache 10 more times
        TestUtils.repeat(10, versioningCacheImpl::promoteVersion);
        // call the cache twice, ensure it never reached the callable more than once
        versioningCacheImpl.get();
        cache = versioningCacheImpl.get();
        assertEquals(12, cache.getVersion());
        // validate callable called 3 times
        assertEquals(3, timesCallableCalled.get());
    }

    @Test
    void testVersionCacheExceptionHandling() {
        VersionCache<BasicCacheModel> versioningCacheImpl =
                new VersioningCacheImpl<>(20000, getCacheCallableThrowsError(), "cool-cache");
        VersioningCacheException exception = assertThrows(VersioningCacheException.class, versioningCacheImpl::get);
        assertEquals("Failed to reload cache.", exception.getMessage());
    }

    @Test
    void testVersionCacheWithZeroAndHighTimeoutOnFirstLoad() throws InterruptedException {
        AtomicInteger timesCallableCalled = new AtomicInteger(0);
        Callable<BasicCacheModel> slowLoader = () -> {
            timesCallableCalled.incrementAndGet();
            Thread.sleep(200);
            return new CacheModel();
        };

        // Test 1: timeout=0 (uses minimum 60s) - main bug fix
        VersionCache<BasicCacheModel> cacheWithZeroTimeout = new VersioningCacheImpl<>(0, slowLoader, "cool-cache");
        Thread thread1 = new Thread(cacheWithZeroTimeout::get);
        Thread thread2 = new Thread(cacheWithZeroTimeout::get);
        thread1.start();
        Thread.sleep(50);
        thread2.start();
        thread1.join(5000);
        thread2.join(5000);
        assertEquals(1, cacheWithZeroTimeout.get().getVersion());
        assertEquals(1, timesCallableCalled.get());

        // Test 2: timeout=120s (uses 120s, not reduced to 60s) - Math.max behavior
        timesCallableCalled.set(0);
        VersionCache<BasicCacheModel> cacheWithHighTimeout =
                new VersioningCacheImpl<>(120000, slowLoader, "cool-cache");
        BasicCacheModel loadedCacheWithHighTimeout = cacheWithHighTimeout.get();
        assertEquals(1, loadedCacheWithHighTimeout.getVersion());
        assertEquals(1, timesCallableCalled.get());
    }

    @Test
    void testVersionCacheWithZeroTimeoutAfterCacheLoaded() throws InterruptedException {
        AtomicInteger timesCallableCalled = new AtomicInteger(0);
        Callable<BasicCacheModel> slowLoader = () -> {
            timesCallableCalled.incrementAndGet();
            Thread.sleep(200);
            return new CacheModel();
        };
        VersionCache<BasicCacheModel> versioningCacheImpl = new VersioningCacheImpl<>(0, slowLoader, "cool-cache");

        versioningCacheImpl.get();
        assertEquals(1, timesCallableCalled.get());

        versioningCacheImpl.promoteVersion();

        Thread reloadThread = new Thread(versioningCacheImpl::get);
        reloadThread.start();
        Thread.sleep(50);

        // Second thread with timeout=0 should return stale cache immediately (dirty read)
        BasicCacheModel staleCache = versioningCacheImpl.get();
        assertEquals(1, staleCache.getVersion());

        reloadThread.join(5000);

        BasicCacheModel freshCache = versioningCacheImpl.get();
        assertEquals(2, freshCache.getVersion());
        assertEquals(2, timesCallableCalled.get());
    }

    @Test
    void testVersionCacheRecursionOnLockTimeout() {
        AtomicInteger timesCallableCalled = new AtomicInteger(0);
        AtomicInteger lockAttempts = new AtomicInteger(0);
        Callable<BasicCacheModel> loader = () -> {
            timesCallableCalled.incrementAndGet();
            Thread.sleep(100);
            return new CacheModel();
        };

        // Mock ConflictGuard that times out first attempt, succeeds on second (recursion)
        ConflictGuard mockGuardWithTimeout = new ConflictGuard() {
            @Override
            public boolean tryToLock(long timeout, TimeUnit unit) throws InterruptedException {
                int attempt = lockAttempts.incrementAndGet();
                if (attempt == 1) {
                    Thread.sleep(50);
                    return false;
                }
                return true;
            }

            @Override
            public void unlock() {
            }

            @Override
            public void forceUnlock() {
            }

            @Override
            public boolean isLocked() {
                return false;
            }
        };

        VersionCache<BasicCacheModel> cacheWithTimeoutAndRetry =
                new VersioningCacheImpl<>(10, loader, "cool-cache", mockGuardWithTimeout);

        BasicCacheModel loadedCacheAfterTimeout = cacheWithTimeoutAndRetry.get();

        assertNotNull(loadedCacheAfterTimeout);
        assertEquals(2, lockAttempts.get());
        assertEquals(1, timesCallableCalled.get());
    }

    @Test
    void testVersionCacheInterruptedExceptionHandling() {
        AtomicInteger timesCallableCalled = new AtomicInteger(0);
        AtomicInteger interruptCount = new AtomicInteger(0);
        Callable<BasicCacheModel> loader = () -> {
            timesCallableCalled.incrementAndGet();
            return new CacheModel();
        };

        // Scenario 1: InterruptedException when cache == null (should retry)
        ConflictGuard mockGuardForFirstLoad = new ConflictGuard() {
            private boolean firstAttempt = true;

            @Override
            public boolean tryToLock(long timeout, TimeUnit unit) throws InterruptedException {
                if (firstAttempt) {
                    firstAttempt = false;
                    interruptCount.incrementAndGet();
                    throw new InterruptedException("Interrupted during first load");
                }
                return true;
            }

            @Override
            public void unlock() {
            }

            @Override
            public void forceUnlock() {
            }

            @Override
            public boolean isLocked() {
                return false;
            }
        };

        VersionCache<BasicCacheModel> cacheWithInterruptOnFirstLoad =
                new VersioningCacheImpl<>(0, loader, "cool-cache", mockGuardForFirstLoad);
        BasicCacheModel loadedCacheAfterRetry = cacheWithInterruptOnFirstLoad.get();
        assertNotNull(loadedCacheAfterRetry);
        assertEquals(1, interruptCount.get());
        assertEquals(1, timesCallableCalled.get());

        // Scenario 2: InterruptedException when cache != null (should NOT retry, just return existing cache)
        timesCallableCalled.set(0);
        interruptCount.set(0);
        Callable<BasicCacheModel> slowLoaderForSecondScenario = () -> {
            timesCallableCalled.incrementAndGet();
            Thread.sleep(100);
            return new CacheModel();
        };

        ConflictGuard mockGuardWithInterruptAfterCacheLoaded = new ConflictGuard() {
            private int attempts = 0;

            @Override
            public boolean tryToLock(long timeout, TimeUnit unit) throws InterruptedException {
                attempts++;
                if (attempts == 1) {
                    return true;
                }
                interruptCount.incrementAndGet();
                throw new InterruptedException("Interrupted with cache loaded");
            }

            @Override
            public void unlock() {
            }

            @Override
            public void forceUnlock() {
            }

            @Override
            public boolean isLocked() {
                return false;
            }
        };

        VersionCache<BasicCacheModel> cacheWithInterruptAfterLoad =
                new VersioningCacheImpl<>(0, slowLoaderForSecondScenario, "cool-name",
                        mockGuardWithInterruptAfterCacheLoaded);
        cacheWithInterruptAfterLoad.get();
        cacheWithInterruptAfterLoad.promoteVersion();
        BasicCacheModel existingCacheReturnedAfterInterrupt = cacheWithInterruptAfterLoad.get();
        assertNotNull(existingCacheReturnedAfterInterrupt);
        assertEquals(1, interruptCount.get());
        assertEquals(1, timesCallableCalled.get());
    }

    private Callable<BasicCacheModel> getCacheCallable(AtomicInteger atomicInteger) {
        return () -> {
            atomicInteger.incrementAndGet();
            return new CacheModel();
        };
    }

    private Callable<BasicCacheModel> getCacheCallableThrowsError() {
        return () -> {
            throw new RuntimeException("Some sensitive data");
        };
    }

    public static class CacheModel implements BasicCacheModel {
        long version = 0;

        @Override
        public long getVersion() {
            return version;
        }

        @Override
        public void setVersion(long ver) {
            version = ver;
        }

        @Override
        public void destroy() {

        }
    }
}