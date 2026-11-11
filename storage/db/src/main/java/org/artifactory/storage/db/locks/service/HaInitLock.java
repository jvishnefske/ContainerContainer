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

package org.artifactory.storage.db.locks.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.home.ArtifactoryHome;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Manages a distributed locking mechanism for Artifactory initialization in High Availability environments.
 * This class coordinates initialization tasks across cluster nodes, preventing concurrent execution
 * of critical startup processes by using database locks. It handles acquisition, release, and cleanup
 * of initialization locks, ensuring orderly startup sequence in multi-node deployments.
 *
 * @author Mor Merhav
 */
@Slf4j
public class HaInitLock {

    private final DbLock dbLock;
    private final String serverId;
    private final BooleanSupplier shouldAcquireLockToggle;
    private final PrematureLockReleaseDetector prematureLockReleaseDetector;
    @VisibleForTesting
    @Setter(AccessLevel.PACKAGE)
    private ScheduledExecutorService detectorExecutor;
    @VisibleForTesting
    static final String PREMATURE_LOCK_DETECTOR_INTERVAL_PROP_KEY =
            "artifactory.ha.init.lock.detectorInterval.ms";
    private static final int PREMATURE_LOCK_RELEASE_DETECTOR_INTERVAL_MS =
            Integer.parseInt(System.getProperty(PREMATURE_LOCK_DETECTOR_INTERVAL_PROP_KEY, "100"));
    private static final int PREMATURE_LOCK_RELEASE_GRACE_MS = PREMATURE_LOCK_RELEASE_DETECTOR_INTERVAL_MS * 3;

    HaInitLock(DbLock dbLock, String serverId, BooleanSupplier shouldAcquireLockToggle) {
        validate(dbLock);
        validate(shouldAcquireLockToggle);
        validate(serverId);
        this.dbLock = dbLock;
        this.serverId = serverId;
        this.shouldAcquireLockToggle = shouldAcquireLockToggle;
        this.prematureLockReleaseDetector = new PrematureLockReleaseDetector(dbLock);
        this.detectorExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder().setNameFormat(
                        "HaInitLock-PrematureLockReleaseDetector-" + dbLock.getLockKey()).build());
    }

    @SneakyThrows
    public void release() {
        log.info("Server id: '{}' attempting to release high-availability init lock: '{}'", serverId,
                dbLock.getLockKey());
        prematureLockReleaseDetector.markRelease();
        dbLock.release();
        detectorExecutor.shutdown();
        log.info("Server id: '{}' released high-availability init lock: '{}'", serverId, dbLock.getLockKey());
    }

    public boolean acquire() {
        if (!shouldAcquireLockToggle.getAsBoolean()) {
            return false;
        }
        acquireLock();
        if (!shouldAcquireLockToggle.getAsBoolean()) {
            // Some other node has completed the upgrade or first vanilla init, release lock immediately
            log.info(
                    "Server id: '{}' releasing init lock: '{}' to allow other nodes to continue the init process " +
                            "(upgrade already performed by other node)", serverId, dbLock.getLockKey());
            release();
            return false;
        }
        gracefulWait();
        detectorExecutor.schedule(prematureLockReleaseDetector, PREMATURE_LOCK_RELEASE_DETECTOR_INTERVAL_MS,
                MILLISECONDS);
        return true;
    }

    public boolean isLocked() {
        return dbLock.isLockedByMe();
    }

    private static void gracefulWait() {
        // wait to ensure other nodes had time to shut down in case they crashed due to prematurely
        // released lock detection
        try {
            Thread.sleep(PREMATURE_LOCK_RELEASE_GRACE_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void acquireLock() {
        log.info("Server id: '{}' attempting to acquire a high-availability init lock: '{}'", serverId,
                dbLock.getLockKey());
        dbLock.releaseStale();
        boolean acquiredLock = dbLock.acquire();
        if (!acquiredLock) {
            throw new IllegalStateException(
                    "Server id: '%s' failed to acquire high-availability init lock: '%s'".formatted(serverId,
                            dbLock.getLockKey()));
        }
        log.info("Server id: '{}' acquired high-availability init lock: '{}'", serverId, dbLock.getLockKey());
    }

    private void validate(String serverId) {
        if (StringUtils.isBlank(serverId)) {
            throw new IllegalArgumentException("Server ID cannot be null or empty");
        }
    }

    private void validate(DbLock dbLock) {
        if (dbLock == null) {
            throw new IllegalArgumentException("dbLock cannot be null");
        }
    }

    private void validate(BooleanSupplier shouldAcquireLockToggle) {
        if (shouldAcquireLockToggle == null) {
            throw new IllegalArgumentException("BooleanSupplier for shouldAcquireLockToggle cannot be null");
        }
    }

    /**
     * A detector thread that monitors for premature release of the HA init lock. If the lock is released
     * before the initialization process is complete, it indicates that another node may have started
     * initialization concurrently, which can lead to data corruption. Upon detecting such a scenario,
     * the detector logs an error and forcibly terminates the node to prevent potential issues.
     */
    @Slf4j
    static class PrematureLockReleaseDetector implements Runnable {
        private final DbLock dbLock;
        private final AtomicBoolean properlyReleased = new AtomicBoolean(false);

        PrematureLockReleaseDetector(DbLock dbLock) {
            this.dbLock = dbLock;
        }

        @Override
        public void run() {
            log.info("Starting premature lock release detector for lock: '{}'", dbLock.getLockKey());
            String serverId = ArtifactoryHome.get().getServerId();
            if (!dbLock.isLockedByMe()) {
                if (properlyReleased.get()) {
                    log.info(
                            "Premature lock release detector on server ID: '{}' for lock: '{}' ending as lock was " +
                                    "properly released", serverId, dbLock.getLockKey());
                } else {
                    log.error("""
                            HA init lock: '{}' on server ID: '{}' premature release detected!
                            Another node may have started initialization while this node is still initializing.
                            Purposely shutting down now!
                            """, serverId, dbLock.getLockKey());
                    systemExit();
                }
            }
        }

        @VisibleForTesting
        void systemExit() {
            System.exit(-1);
        }

        public void markRelease() {
            properlyReleased.set(true);
        }
    }
}