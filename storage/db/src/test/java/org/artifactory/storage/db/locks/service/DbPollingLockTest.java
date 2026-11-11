package org.artifactory.storage.db.locks.service;

import org.artifactory.common.home.ArtifactoryHome;
import org.artifactory.concurrent.NonStandardLockCategory;
import org.artifactory.storage.db.dao.locks.LockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DbPollingLockTest {

    private static final String LOCK_KEY = "test-lock-key";

    @Mock
    private JFrogTableBasedDbLocksService dbLocksService;
    @Mock
    private ArtifactoryHome artifactoryHome;
    private DbPollingLock dbPollingLock;

    @BeforeEach
    void setUp() {
        dbPollingLock = new DbPollingLock(LOCK_KEY, dbLocksService, artifactoryHome);
    }

    @Test
    void testAcquireLockBlocking_WhenHaConfigured_ShouldAcquireLock() throws TimeoutException {
        String serverId = "test-server-id";
        when(artifactoryHome.isHaConfigured()).thenReturn(true);
        when(artifactoryHome.getServerId()).thenReturn(serverId);

        boolean lockAcquired = dbPollingLock.acquire();

        assertTrue(lockAcquired);
        verify(dbLocksService).acquireLock(
                anyString(),
                eq(LOCK_KEY),
                eq(serverId),
                eq(1L),
                eq(TimeUnit.HOURS)
        );
    }

    @Test
    void testAcquireLock_WhenHaNotConfigured_ShouldReturnTrue() throws TimeoutException {
        when(artifactoryHome.isHaConfigured()).thenReturn(false);

        boolean lockAcquired = dbPollingLock.acquire();

        assertTrue(lockAcquired);
        verify(dbLocksService, never()).acquireLock(anyString(), anyString(), anyString(), anyLong(), any());
    }

    @Test
    void testAcquireLock_WhenTimeoutException_ShouldReturnFalse() throws TimeoutException {
        String serverId = "test-server-id";
        when(artifactoryHome.isHaConfigured()).thenReturn(true);
        when(artifactoryHome.getServerId()).thenReturn(serverId);
        doThrow(new TimeoutException()).when(dbLocksService)
                .acquireLock(anyString(), anyString(), anyString(), anyLong(), any());

        boolean lockAcquired = dbPollingLock.acquire();

        assertFalse(lockAcquired);
        verify(dbLocksService).acquireLock(
                anyString(),
                eq(LOCK_KEY),
                eq(serverId),
                eq(1L),
                eq(TimeUnit.HOURS)
        );
    }

    @Test
    void testReleaseAsyncDbConversionLock_lockedByMe_forceUnlock() {
        String serverId = "test-server-id";
        when(artifactoryHome.getServerId()).thenReturn(serverId);
        givenLockedBy(serverId);

        dbPollingLock.release();

        verify(dbLocksService).forceUnlock(NonStandardLockCategory.CLUSTER_SINGLETON.getName(), LOCK_KEY);
    }

    @Test
    void testReleaseAsyncDbConversionLock_lockedByOtherNode_doesNotForceUnlock() {
        when(artifactoryHome.getServerId()).thenReturn("test-server-id");
        givenLockedBy("other-server-id");

        dbPollingLock.release();

        verify(dbLocksService, never()).forceUnlock(anyString(), anyString());
    }

    // java
    @Test
    void testReleaseStale_whenMarkerExists_shouldForceUnlockAndDeleteMarker() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("db-polling-lock-marker", ".lock");
        java.io.File marker = tmp.toFile();
        try {
            when(artifactoryHome.isHaConfigured()).thenReturn(true);
            when(artifactoryHome.getReleaseInitLockMarker()).thenReturn(marker);

            dbPollingLock.releaseStale();

            verify(dbLocksService).forceUnlock(NonStandardLockCategory.CLUSTER_SINGLETON.getName(), LOCK_KEY);
            assertFalse(marker.exists());
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    @Test
    void testReleaseStale_whenMarkerDoesNotExist_shouldDelegateToRelease() {
        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("db-polling-lock-marker", ".lock");
            java.io.File marker = tmp.toFile();
            // delete the file so it does not exist
            java.nio.file.Files.delete(tmp);

            when(artifactoryHome.isHaConfigured()).thenReturn(true);
            when(artifactoryHome.getReleaseInitLockMarker()).thenReturn(marker);

            String serverId = "test-server-id";
            when(artifactoryHome.getServerId()).thenReturn(serverId);
            LockInfo lockInfo = mock(LockInfo.class);
            when(lockInfo.getOwner()).thenReturn(serverId);
            when(dbLocksService.getLockInfoFromDbByCategoryAndLockKey(
                    NonStandardLockCategory.CLUSTER_SINGLETON.getName(), LOCK_KEY))
                    .thenReturn(Optional.of(lockInfo));

            dbPollingLock.releaseStale();

            verify(dbLocksService).forceUnlock(NonStandardLockCategory.CLUSTER_SINGLETON.getName(), LOCK_KEY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    void testReleaseStale_markerDeletedEvenIfForceUnlockThrows() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("db-polling-lock-marker", ".lock");
        java.io.File marker = tmp.toFile();
        try {
            when(artifactoryHome.isHaConfigured()).thenReturn(true);
            when(artifactoryHome.getReleaseInitLockMarker()).thenReturn(marker);

            doThrow(new RuntimeException("force-unlock-failed"))
                    .when(dbLocksService).forceUnlock(NonStandardLockCategory.CLUSTER_SINGLETON.getName(), LOCK_KEY);

            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> dbPollingLock.releaseStale());

            assertFalse(marker.exists());
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    private void givenLockedBy(String serverId) {
        LockInfo lockInfo = mock(LockInfo.class);
        when(lockInfo.getOwner()).thenReturn(serverId);
        when(dbLocksService
                .getLockInfoFromDbByCategoryAndLockKey(NonStandardLockCategory.CLUSTER_SINGLETON.getName(),
                        LOCK_KEY)).thenReturn(Optional.of(lockInfo));
    }
}