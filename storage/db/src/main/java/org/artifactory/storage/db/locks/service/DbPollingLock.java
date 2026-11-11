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

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.storage.db.dao.locks.LockInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.artifactory.concurrent.NonStandardLockCategory.CLUSTER_SINGLETON;

/**
 * A polling DB lock implemented as cluster singleton entry on the DB. Entry is deleted upon lock release.
 * It is polling in the sense that the {@link JFrogTableBasedDbLocksService} will poll the DB until the lock is
 * acquired or timeout occurs.
 * <p>
 * In case the node which acquired the lock goes down before releasing it, other nodes will not be able to
 * acquire the lock. The simple solution is to delete the lock entry from the DB.
 *
 * @author Mor Merhav
 */
@Slf4j
public class DbPollingLock implements DbLock {

    @Getter
    private final String lockKey;
    private final JFrogTableBasedDbLocksService dbLocksService;
    private final ArtifactoryHome artifactoryHome;
    private static final String FOUND_NOT_OWNED_LOCK_LOG_MSG = """
            Found polling DB lock: {} in the DB, but the owner is the not the current node\
            . Found: \
            '{}', current: '{}'.\
             The lock will not be released to allow '{}' to finish the init phase.""";

    public DbPollingLock(String lockKey,
                         JFrogTableBasedDbLocksService dbLocksService, ArtifactoryHome artifactoryHome) {
        this.lockKey = lockKey;
        this.dbLocksService = dbLocksService;
        this.artifactoryHome = artifactoryHome;
    }

    public boolean acquire() {
        String serverId = artifactoryHome.getServerId();
        if (artifactoryHome.isHaConfigured()) {
            log.debug("Attempting to acquire polling DB lock: {}, with node-id {}",
                    lockKey, serverId);
            try {
                dbLocksService.acquireLock(CLUSTER_SINGLETON.getName(),
                        lockKey, serverId, 1, TimeUnit.HOURS);
                log.debug("Acquired polling DB lock: {}", lockKey);
            } catch (TimeoutException e) {
                String message = "Unable to acquire polling DB lock: " + lockKey;
                log.warn(message, e);
                return false;
            }
        }
        return true;
    }

    public void release() {
        getLockInfo().ifPresent(lockInfo -> {
            String serverId = artifactoryHome.getServerId();
            if (serverId.equals(lockInfo.getOwner())) {
                log.debug("Found polling DB lock: {} by current node: {}. Removing the lock.",
                        lockKey, lockInfo.getOwner());
                // We force unlock because there is no guarantee that the thread who acquired the lock will be the
                // one who releases it.
                dbLocksService.forceUnlock(CLUSTER_SINGLETON.getName(), lockKey);
            } else {
                log.debug(FOUND_NOT_OWNED_LOCK_LOG_MSG, lockKey, lockInfo.getOwner(), serverId, lockInfo.getOwner());
            }
        });
    }

    @Override
    public void releaseStale() {
        if (artifactoryHome.isHaConfigured()) {
            File releaseInitLockMarkerFile = artifactoryHome.getReleaseInitLockMarker();
            if (releaseInitLockMarkerFile.exists()) {
                releaseInitLockByMarker(releaseInitLockMarkerFile);
            } else {
                release();
            }
        }
    }

    @Override
    public boolean isLockedByMe() {
        return getLockInfo()
                .map(lockInfo -> lockInfo.getOwner().equals(artifactoryHome.getServerId()))
                .orElse(false);
    }

    @SneakyThrows
    private void releaseInitLockByMarker(File releaseInitLockMarkerFile) {
        String releaseInitLockMarkerFileAbsolutePath = releaseInitLockMarkerFile.getAbsolutePath();
        try {
            log.debug("Found a polling DB lock marker file in: {}",
                    releaseInitLockMarkerFileAbsolutePath);
            dbLocksService.forceUnlock(CLUSTER_SINGLETON.getName(), lockKey);
        } finally {
            Files.delete(Paths.get(releaseInitLockMarkerFileAbsolutePath));
            log.debug("Released a polling DB lock marker file: {} deleted",
                    releaseInitLockMarkerFileAbsolutePath);
        }
    }

    private Optional<LockInfo> getLockInfo() {
        return dbLocksService
                .getLockInfoFromDbByCategoryAndLockKey(CLUSTER_SINGLETON.getName(), lockKey);
    }
}