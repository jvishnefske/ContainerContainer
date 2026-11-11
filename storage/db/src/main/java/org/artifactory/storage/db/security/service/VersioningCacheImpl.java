/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.db.security.service;

import lombok.extern.slf4j.Slf4j;
import org.jfrog.common.concurrency.ConflictGuard;
import org.jfrog.common.concurrency.JVMLockWrapper;
import org.jfrog.common.concurrency.JvmConflictGuard;
import org.jfrog.common.concurrency.MonitoringReentrantLock;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Gidi Shabat
 */
@Slf4j
public class VersioningCacheImpl<T extends BasicCacheModel> implements VersionCache<T> {

    private final ConflictGuard lock;
    private final long timeout;
    private final Callable<T> cacheLoader;
    final String name;
    // promoted on each DB change (permission change/add/delete)2
    private final AtomicInteger dbVersion = new AtomicInteger(1);
    private volatile int version = 0; // promoted each time we load the cache from DB
    protected volatile T cache;

    public VersioningCacheImpl(long timeout, Callable<T> cacheLoader, String name) {
        this(timeout, cacheLoader, name, new JvmConflictGuard(new JVMLockWrapper(new MonitoringReentrantLock())));
    }

    public VersioningCacheImpl(long timeout, Callable<T> cacheLoader, String name, ConflictGuard conflictGuard) {
        this.timeout = timeout;
        this.cacheLoader = cacheLoader;
        this.name = name;
        this.lock = conflictGuard;
    }

    /**
     * Call this method each permission update/change/delete in DB.
     */
    public int promoteVersion() {
        return dbVersion.incrementAndGet();
    }

    /**
     * Returns cache.
     */
    public T get() {
        T currentCache = cache;
        if (hasNewVersion()) {
            // Need to update cache (new version in dbVersion).
            // Try to acquire lock
            log.debug("[{}] Attempting to acquire lock for cache reload", name);
            boolean lockAcquired = tryToWaitForLock();
            if (!lockAcquired) {
                // Timeout occurred : Return the current cache without waiting to thew new cache which is being
                //reloaded.
                log.debug("[{}] Lock timeout - returning stale cache (dirty read)", name);
                return cache;
            } else {
                try {
                    log.debug("[{}] Lock acquired successfully", name);
                    currentCache = cache;
                    log.debug("[{}] Current cache state: {}", name, currentCache);
                    // Double check after cacheLoader synchronization.
                    if (hasNewVersion()) {
                        log.debug("[{}] Reloading cache: dbVersion={}, currentVersion={}", name, dbVersion.get(),
                                version);
                        // The map will be valid for version the current sDbVersion.
                        int startingVersion = dbVersion.get();
                        try {
                            currentCache = cacheLoader.call();
                        } catch (Exception e) {
                            throw new VersioningCacheException("Failed to reload cache.", e);
                        }
                        T oldCache = cache;
                        cache = currentCache;
                        if (oldCache != null) {
                            oldCache.destroy();
                        }
                        log.debug("[{}] Cache reload completed: {}", name, currentCache);
                        currentCache.setVersion(startingVersion);
                        version = startingVersion;
                    } else {
                        log.debug("[{}] Skipping reload - cache already updated by another thread (dbVersion={}, "
                                + "currentVersion={})", name, dbVersion.get(), version);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        return currentCache;
    }

    @Override
    public void destroy() {

    }

    @Override
    public boolean hasNewVersion() {
        return dbVersion.get() > version;
    }

    private boolean tryToWaitForLock() {
        boolean acquireLock = false;
        try {
            // For first load, use minimum 60s timeout to prevent busy-wait when configured timeout is 0 or very small
            // For subsequent loads, respect configured timeout to allow immediate dirty reads
            long effectiveTimeout = timeout;
            if (cache == null) {
                effectiveTimeout = Math.max(timeout, 60000);
                log.info("[{}] First cache load - using effective timeout {}ms (configured: {}ms)", name,
                        effectiveTimeout, timeout);
            }
            acquireLock = lock.tryToLock(effectiveTimeout, TimeUnit.MILLISECONDS);
            if (!acquireLock && cache == null) {
                log.debug("[{}] Lock acquisition failed on first load - retrying", name);
                acquireLock = tryToWaitForLock();
            }
        } catch (InterruptedException e) {
            if (cache == null) {
                log.debug("[{}] Thread interrupted during first load - retrying", name);
                acquireLock = tryToWaitForLock();
            }
        }
        return acquireLock;
    }
}