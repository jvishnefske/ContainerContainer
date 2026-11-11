package org.artifactory.storage.db.locks.service;

/**
 * Interface for acquiring and releasing a database lock.
 *
 * @author Mor Merhav
 */
public interface DbLock {

    boolean acquire();

    void release();

    void releaseStale();

    String getLockKey();

    boolean isLockedByMe();
}