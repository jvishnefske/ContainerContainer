package org.artifactory.storage.db.security.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * Similar to {@link VersioningCacheImpl}, but loading the cache asynchronously after the first time.
 *
 * @author Shay Bagants
 * @author Gidi Shabat
 */
@Slf4j
public class AsyncVersioningCacheImpl<T extends BasicCacheModel> extends VersioningCacheImpl<T> {

    private boolean active = true;
    private final Object lock = new Object();

    public AsyncVersioningCacheImpl(long timeout, Callable<T> cacheLoader, long threadWaitingTime,
            long threadWaitingOnErrorMillis, String name) {
        super(timeout, cacheLoader, name);
        log.debug("[{}] Initializing async cache with background thread", name);
        Thread thread = new Thread(() -> doJob(threadWaitingTime, threadWaitingOnErrorMillis));
        thread.start();
    }

    @Override
    public T get() {
        if (hasNewVersion()) {
            if (cache != null) {
                synchronized (lock) {
                    try {
                        log.debug("[{}] Notifying background thread to reload cache", name);
                        lock.notifyAll();
                    } catch (Exception e) {
                        log.warn("[{}] Failed to notify background thread", name, e);
                    }
                }
            } else {
                log.debug("[{}] First load - using synchronous reload", name);
                super.get();
            }
        }
        return cache;
    }

    @Override
    public int promoteVersion() {
        synchronized (lock) {
            lock.notifyAll();
        }
        return super.promoteVersion();
    }

    @Override
    public void destroy() {
        active = false;
    }

    private void doJob(long waitOnLockMillis, long waitOnLockAfterErrorMillis) {
        while (active) {
            try {
                // first cache population is done synchronously
                if (cache != null) {
                    while (hasNewVersion()) {
                        log.debug("[{}] Background thread reloading cache", name);
                        super.get();
                    }
                }
                lockAndWait(waitOnLockMillis);
            } catch (Exception e) {
                log.error("[{}] Error in background cache reload thread", name, e);
                lockAndWait(waitOnLockAfterErrorMillis);
            }
        }
    }

    private void lockAndWait(long maxMillisToWait) {
        synchronized (lock) {
            try {
                log.debug("[{}] Background thread waiting for {}ms", name, maxMillisToWait);
                lock.wait(maxMillisToWait);
            } catch (Exception e) {
                log.warn("[{}] Background thread wait interrupted", name, e);
            }
        }
    }
}
