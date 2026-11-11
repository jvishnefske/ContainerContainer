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

import com.google.common.collect.Sets;
import org.artifactory.spring.ReloadableBean;
import org.artifactory.storage.db.dao.locks.LockInfo;

import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author gidis
 * @author Mor Merhav
 */
public interface DbLocksService extends ReloadableBean {

    /**
     * Try to acquire a lock on a combination of a category and a key. Throws {@link TimeoutException} if a lock
     * cannot be
     * acquired within the specified timeout.
     *
     * @param category the lock category
     * @param key      the lock key
     * @param owner    the owner node ID
     * @param timeout  the time to wait for the lock
     * @param timeUnit the time unit of the timeout argument
     * @throws TimeoutException if the waiting time elapsed before acquiring a lock on the key-category combination
     */
    void acquireLock(String category, String key, String owner, long timeout, TimeUnit timeUnit)
            throws TimeoutException;

    /**
     * Unlock locks from cache and optionally from DB as well
     *
     * @param locks            - The locks to release
     * @param removeCachedOnly - Whether to release the locks from the cache only, or from both DB and cache
     */
    void forceRemoveLocks(@Nonnull Set<LockInfo> locks, boolean removeCachedOnly);

    /**
     * Release all locks belonging to owner
     *
     * @param owner            - The owner for which all locks are to be released
     * @param excludedLockKeys - The keys which to exclude from unlock
     */
    void releaseAllLocksByOwnerExcludingKeys(String owner, Set<String> excludedLockKeys);

    default void releaseAllLocksByOwner(String owner) {
        releaseAllLocksByOwnerExcludingKeys(owner, Sets.newHashSet());
    }

    /**
     * Check if a lock in a given category with specific key is locked
     *
     * @param category - the category of the lock
     * @param key      - the key of the lock
     * @return true if the lock is currently locked, false otherwise
     */
    boolean isLocked(String category, String key);

    /**
     * Check if a lock in a given category with specific key is locked by this current Artifactory node
     *
     * @param category - the category of the lock
     * @param key      - the key of the lock
     * @return true if the lock is currently locked by this current Artifactory node, false otherwise
     */
    boolean isLockedByMe(String category, String key);

    /**
     * Release locks from cache and DB for given category, key and owner
     *
     * @param category - the category for which to release locks
     * @param key      - the key for which to release locks
     * @param owner    - the owner for which to release the locks
     * @return true if the lock was released successfully
     */
    boolean unlock(String category, String key, String owner);

    /**
     * Unlock locks from cache and DB for given category and key
     *
     * @param category - the category for which to release locks
     * @param key      - the key for which to release locks
     * @return true in case DB locks were released successfully, false otherwise.
     */
    boolean forceUnlock(String category, String key);

    /**
     * Unlock stuck locks from cache and DB
     */
    void forceUnlockAllStuck();

    int lockingMapSize(String category);

    Set<String> lockingMapKeySet(String category);
}
