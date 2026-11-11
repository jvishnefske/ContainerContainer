package org.artifactory.storage.db.locks.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.artifactory.storage.db.fs.dao.BlockingLockDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.sql.SQLException;

@ExtendWith(MockitoExtension.class)
class DbBlockingLockTest {

    private static final String LOCK_KEY = "lockKey";

    @Mock
    private BlockingLockDao blockingLockDao;
    private DbBlockingLock haBlockingLock;

    @BeforeEach
    void setUp() {
        haBlockingLock = new DbBlockingLock(LOCK_KEY, blockingLockDao);
    }

    @Test
    void testAcquireLockBlocking_Success() throws Exception {
        assertDoesNotThrow(() -> haBlockingLock.acquire());
        verify(blockingLockDao).lockBlocking();
    }

    @Test
    void testAcquireLockBlocking_SQLException() throws Exception {
        doThrow(new SQLException("Lock acquisition failed")).when(blockingLockDao).lockBlocking();
        Boolean locked = assertDoesNotThrow(() -> haBlockingLock.acquire());
        assertFalse(locked);
        verify(blockingLockDao).close();
    }

    @Test
    void testRelease_NeverAcquired() throws SQLException, IOException {
        doThrow(new IllegalArgumentException("Never acquired lock"))
                .when(blockingLockDao).releaseLock();
        assertDoesNotThrow(() -> haBlockingLock.release());
        verify(blockingLockDao).releaseLock();
    }

    @Test
    void testRelease_Success() {
        assertDoesNotThrow(() -> haBlockingLock.release());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testConstructor_ValidParameters_nullOrEmptyLockKey(String lockKey) {
        assertThrows(IllegalArgumentException.class, () -> new DbBlockingLock(lockKey, blockingLockDao));
    }

    @Test
    void testConstructor_ValidParameters_nullHaBlockingLockDao() {
        assertThrows(IllegalArgumentException.class, () -> new DbBlockingLock(LOCK_KEY, (BlockingLockDao) null));
    }
}