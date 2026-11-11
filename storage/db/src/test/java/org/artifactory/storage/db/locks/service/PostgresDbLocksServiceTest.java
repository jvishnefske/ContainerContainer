package org.artifactory.storage.db.locks.service;


import org.apache.commons.lang3.StringUtils;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.storage.concurrency.NativeDbLockInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresDbLocksServiceTest extends ArtifactoryHomeBoundJUnitTest {

    @Mock
    private ArtifactoryNativeDbLocksProvider nativeDbLocksProvider;
    @InjectMocks
    private PostgresDbLocksService postgresDbLocksService;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testAcquireLockIgnoresOwnerUsesThreadId() throws TimeoutException {
        postgresDbLocksService.acquireLock("category", "key", "owner", 20L, MILLISECONDS);
        verify(nativeDbLocksProvider)
                .tryToLock("category", "key", Thread.currentThread().threadId(), 20L, MILLISECONDS);
    }

    @Test
    void testIsLocked() {
        postgresDbLocksService.isLocked("category", "key");
        verify(nativeDbLocksProvider).isLocked("category", "key", Thread.currentThread().threadId());
    }

    @Test
    void testIsLockedByMe() {
        postgresDbLocksService.isLockedByMe("category", "key");
        verify(nativeDbLocksProvider).isLockedByMe("category", "key", Thread.currentThread().threadId());
    }

    @Test
    void testUnlockIgnoresOwnerUsesThreadIdSuccess() {
        when(nativeDbLocksProvider.unlock("category", "key", Thread.currentThread().threadId())).thenReturn(true);

        boolean unlocked = postgresDbLocksService.unlock("category", "key", "owner");
        assertTrue(unlocked);

        verify(nativeDbLocksProvider).unlock("category", "key", Thread.currentThread().threadId());
    }

    @Test
    void testUnlockFails() {
        when(nativeDbLocksProvider.unlock("category", "key", Thread.currentThread().threadId())).thenThrow(
                new IllegalStateException());

        boolean unlocked = postgresDbLocksService.unlock("category", "key", "owner");

        assertFalse(unlocked);
    }

    @Test
    void testForceUnlock() {
        when(nativeDbLocksProvider.forceUnlock("category", "key")).thenReturn(true);

        boolean unlocked = postgresDbLocksService.forceUnlock("category", "key");
        assertTrue(unlocked);
    }

    @Test
    void testGetSupportedVersion() {
        assertEquals(StringUtils.EMPTY, postgresDbLocksService.getSupportedVersion());
    }

    // -------------------------------------------------------------------------
    // getHeldLocks — delegates fully to getAllLocksCopy(); no local cache
    // -------------------------------------------------------------------------

    @Test
    void testGetHeldLocksEmptyInitially() {
        when(nativeDbLocksProvider.getAllLocksCopy()).thenReturn(Set.of());
        assertTrue(postgresDbLocksService.getHeldLocks().isEmpty());
    }

    @Test
    void testGetHeldLocksReturnsFieldsFromNativeLockInfo() {
        NativeDbLockInfo info = nativeLockInfo("cat", "key1", 42L, 1000L, "worker-1");
        when(nativeDbLocksProvider.getAllLocksCopy()).thenReturn(Set.of(info));

        Collection<LockEntry> held = postgresDbLocksService.getHeldLocks();

        assertEquals(1, held.size());
        LockEntry entry = held.iterator().next();
        assertEquals("cat", entry.category());
        assertEquals("key1", entry.key());
        assertEquals(42L, entry.threadId());
        assertEquals(1000L, entry.lockedSinceMs());
        assertEquals("worker-1", entry.threadName());
    }

    @Test
    void testGetHeldLocksEmptyWhenNativeLayerEmpty() throws TimeoutException {
        postgresDbLocksService.acquireLock("cat", "key2", "owner", 20L, MILLISECONDS);
        when(nativeDbLocksProvider.getAllLocksCopy()).thenReturn(Set.of());

        assertTrue(postgresDbLocksService.getHeldLocks().isEmpty(),
                "getHeldLocks() must reflect native layer — empty when native layer is empty");
    }

    @Test
    void testGetHeldLocksReportsNullThreadName_whenNativeInfoHasNullThreadName() {
        NativeDbLockInfo info = nativeLockInfo("cat", "key3", 7L, 5000L, null);
        when(nativeDbLocksProvider.getAllLocksCopy()).thenReturn(Set.of(info));

        Collection<LockEntry> held = postgresDbLocksService.getHeldLocks();

        assertEquals(1, held.size());
        assertNull(held.iterator().next().threadName(),
                "threadName must be null when NativeDbLockInfo has no threadName");
    }

    @Test
    void testGetHeldLocksIterationDoesNotBlockWriter() throws Exception {
        NativeDbLockInfo info1 = nativeLockInfo("cat", "key5", 1L, 1000L, "t1");
        NativeDbLockInfo info2 = nativeLockInfo("cat", "key6", 2L, 1001L, "t2");
        when(nativeDbLocksProvider.getAllLocksCopy()).thenReturn(Set.of(info1, info2));

        CountDownLatch writerDone = new CountDownLatch(1);
        Thread writer = new Thread(() -> {
            try {
                postgresDbLocksService.acquireLock("cat", "key7", "owner", 20L, MILLISECONDS);
            } catch (TimeoutException e) {
                Thread.currentThread().interrupt();
            } finally {
                writerDone.countDown();
            }
        });

        Collection<LockEntry> snapshot = postgresDbLocksService.getHeldLocks();
        writer.start();
        assertTrue(writerDone.await(2, java.util.concurrent.TimeUnit.SECONDS),
                "Writer should not block while iterating held locks");
        snapshot.iterator().forEachRemaining(e -> {});
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static NativeDbLockInfo nativeLockInfo(String category, String key, long ownerId,
            long acquiredAtEpochMs, String threadName) {
        NativeDbLockInfo info = mock(NativeDbLockInfo.class);
        when(info.getCategory()).thenReturn(category);
        when(info.getKey()).thenReturn(key);
        when(info.getOwnerId()).thenReturn(ownerId);
        when(info.getAcquiredAtEpochMs()).thenReturn(acquiredAtEpochMs);
        when(info.getThreadName()).thenReturn(threadName);
        return info;
    }
}
