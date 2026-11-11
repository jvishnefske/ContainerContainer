package org.artifactory.storage.db.locks.service;

import org.artifactory.storage.db.dao.locks.LockInfo;

import java.util.Optional;

/**
 * This interface is used to mark the locking mechanism which uses the underlying distributed_locks table
 */
public interface JFrogTableBasedDbLocksService extends DbLocksService {

    /**
     * Get all locks in given category with a specific lock key
     *
     * @param category - the category of the lock
     * @param lockKey  - the key of the lock
     * @return - optional wrapper of the lock info
     */
    Optional<LockInfo> getLockInfoFromDbByCategoryAndLockKey(String category, String lockKey);
}
