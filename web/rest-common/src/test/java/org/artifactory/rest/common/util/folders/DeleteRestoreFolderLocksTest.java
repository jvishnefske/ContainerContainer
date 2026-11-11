package org.artifactory.rest.common.util.folders;

import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.jfrog.common.concurrency.ConflictsGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

/**
 * @author Hussein Alshqerat
 */
class DeleteRestoreFolderLocksTest {
    @Mock
    private AddonsManager addonsManager;
    @Mock
    private HaAddon haAddon;
    @Mock
    private ConflictsGuard conflictsGuard;
    private DeleteRestoreFolderLocks folderLocks;
    private AutoCloseable autoCloseable;

    @BeforeEach
    void setUp() {
        autoCloseable = openMocks(this);
        when(addonsManager.addonByType(HaAddon.class)).thenReturn(haAddon);
        when(haAddon.getConflictsGuard("delete_restore_folder")).thenReturn(conflictsGuard);
        folderLocks = new DeleteRestoreFolderLocks(addonsManager);
    }

    @AfterEach
    void cleanup() throws Exception {
        autoCloseable.close();
    }

    @Test
    void testAcquireLockSuccess() throws InterruptedException {
        String pathToLock = "some/folder";
        when(conflictsGuard.tryToLock(pathToLock, 0L, TimeUnit.SECONDS)).thenReturn(true);
        assertTrue(folderLocks.acquireLock(pathToLock));
        verify(conflictsGuard, times(1)).tryToLock(pathToLock, 0L, TimeUnit.SECONDS);
        verify(haAddon, times(1)).getConflictsGuard("delete_restore_folder");
    }

    @Test
    void testAcquireLockFailureDueToActiveProcesses() throws InterruptedException {
        String pathToLock = "some/folder";
        when(conflictsGuard.isLocked("some/folder/child")).thenReturn(true);
        when(conflictsGuard.keySet()).thenReturn(Set.of("some/folder/child"));
        assertFalse(folderLocks.acquireLock(pathToLock));
        verify(conflictsGuard, never()).tryToLock(pathToLock, 0L, TimeUnit.SECONDS);
        verify(conflictsGuard, times(1)).keySet();
        verify(addonsManager, times(1)).addonByType(HaAddon.class);
        verify(haAddon, times(1)).getConflictsGuard("delete_restore_folder");
    }

    @Test
    void testAcquireLockInterruptedException() throws InterruptedException {
        String pathToLock = "some/folder";
        doThrow(new InterruptedException()).when(conflictsGuard).tryToLock(pathToLock, 0L, TimeUnit.SECONDS);
        assertFalse(folderLocks.acquireLock(pathToLock));
        verify(conflictsGuard, times(1)).tryToLock(pathToLock, 0L, TimeUnit.SECONDS);
        verify(addonsManager, times(1)).addonByType(HaAddon.class);
        verify(haAddon, times(1)).getConflictsGuard("delete_restore_folder");
    }

    @Test
    void testUnLock() throws InterruptedException {
        String pathToUnlock = "some/folder";
        folderLocks.unlock(pathToUnlock);
        verify(conflictsGuard, times(0)).tryToLock(pathToUnlock, 0L, TimeUnit.SECONDS);
        verify(addonsManager, times(1)).addonByType(HaAddon.class);
        verify(haAddon, times(1)).getConflictsGuard("delete_restore_folder");
    }
}