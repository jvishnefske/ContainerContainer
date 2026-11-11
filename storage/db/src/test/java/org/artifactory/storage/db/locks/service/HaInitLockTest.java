package org.artifactory.storage.db.locks.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

@Slf4j
class HaInitLockTest {

    @Mock
    private DbLock dbLock;
    @Mock
    private ScheduledExecutorService detectorExecutor;
    private HaInitLock haInitLock;

    private AutoCloseable mocks;

    private static final String SERVER_ID = "serverId";

    @BeforeAll
    static void beforeClass() {
        System.setProperty(HaInitLock.PREMATURE_LOCK_DETECTOR_INTERVAL_PROP_KEY, "0");
    }

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        haInitLock = new HaInitLock(dbLock, SERVER_ID, () -> true);
        haInitLock.setDetectorExecutor(detectorExecutor);
        doAnswer(invocation -> {
            when(dbLock.isLockedByMe()).thenReturn(false);
            return null;
        }).when(dbLock).release();
    }

    @AfterAll
    static void afterClass() {
        System.clearProperty(HaInitLock.PREMATURE_LOCK_DETECTOR_INTERVAL_PROP_KEY);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testRelease() {
        haInitLock.release();
        assertFalse(haInitLock.isLocked());
        verify(detectorExecutor).shutdown();
        verify(dbLock).release();
    }

    @Test
    void testAcquire_AcquiresLock() {
        when(dbLock.acquire()).thenReturn(true);
        when(dbLock.isLockedByMe()).thenReturn(true);
        haInitLock.acquire();
        assertTrue(haInitLock.isLocked());
        verify(dbLock).releaseStale();
        verify(dbLock).acquire();
        verify(detectorExecutor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testAcquire_NoNeedToAcquire_DoesNotAcquireLock() {
        BooleanSupplier shouldAcquireLockToggle = () -> false;
        haInitLock = new HaInitLock(dbLock, SERVER_ID, shouldAcquireLockToggle);
        haInitLock.acquire();

        assertFalse(haInitLock.isLocked());
        verify(dbLock, never()).releaseStale();
        verify(dbLock, never()).acquire();
        verify(dbLock, never()).release();
        verify(detectorExecutor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));;
    }

    @Test
    void testAcquireLock_ReleaseImmediately() {
        BooleanSupplier shouldAcquireLockToggle = Mockito.mock(BooleanSupplier.class);
        when(shouldAcquireLockToggle.getAsBoolean()).thenReturn(true, false);
        when(dbLock.acquire()).thenReturn(true);
        when(dbLock.isLockedByMe()).thenReturn(true);

        haInitLock = new HaInitLock(dbLock, SERVER_ID, shouldAcquireLockToggle);
        haInitLock.acquire();

        assertFalse(haInitLock.isLocked());
        verify(dbLock).releaseStale();
        verify(dbLock).acquire();
        verify(dbLock).release();
        verify(detectorExecutor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));;
    }

    @Test
    void testAcquireLock_FailureToAcquire() {
        when(dbLock.acquire()).thenReturn(false);
        HaInitLock initLock = new HaInitLock(dbLock, SERVER_ID, () -> true);

        assertThrows(IllegalStateException.class, initLock::acquire);

        assertFalse(initLock.isLocked());
        verify(dbLock).releaseStale();
        verify(detectorExecutor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));;
    }

    @Test
    void testConstructor_ValidatesParameters() {
        assertThrows(IllegalArgumentException.class, () -> new HaInitLock(dbLock, SERVER_ID, null));
        assertThrows(IllegalArgumentException.class,
                () -> new HaInitLock(null, SERVER_ID, () -> true));
        assertThrows(IllegalArgumentException.class, () -> new HaInitLock(dbLock, null, () -> true));
        assertThrows(IllegalArgumentException.class, () -> new HaInitLock(dbLock, "", () -> true));
    }
}