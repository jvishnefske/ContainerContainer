// java
package org.artifactory.storage.db.locks.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DummyHaInitLockTest {

    @Test
    void acquireReturnsTrueAndReleaseDoesNotThrow() {
        DummyHaInitLock lock = new DummyHaInitLock();
        assertTrue(lock.acquire(), "DummyHaInitLock.acquire() should return true");
        assertFalse(lock.isLocked(), "DummyHaInitLock.isLocked() should return false");
        assertDoesNotThrow(lock::release, "DummyHaInitLock.release() should not throw");
    }

    @Test
    void dummyDbLockMethods() {
        DummyHaInitLock.DummyDbLock dbLock = new DummyHaInitLock.DummyDbLock();
        assertEquals("dummy_lock", dbLock.getLockKey(), "DummyDbLock.getLockKey() should return 'dummy_lock'");
        assertTrue(dbLock.acquire(), "DummyDbLock.acquire() should return true");
        assertDoesNotThrow(dbLock::release, "DummyDbLock.release() should not throw");
        assertDoesNotThrow(dbLock::releaseStale, "DummyDbLock.releaseStale() should not throw");
    }
}