package org.artifactory.storage.db.locks.service;

/**
 * @author shaharz
 */
public class UnsupportedReentrantLockException extends IllegalStateException {
    public UnsupportedReentrantLockException() {
        super("Reentrant lock is not supported");
    }
}
