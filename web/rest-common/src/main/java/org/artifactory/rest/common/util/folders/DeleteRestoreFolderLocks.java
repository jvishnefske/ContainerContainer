package org.artifactory.rest.common.util.folders;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.HaAddon;
import org.jfrog.common.concurrency.ConflictsGuard;

import jakarta.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@AllArgsConstructor
public class DeleteRestoreFolderLocks {

    private final AddonsManager addonsManager;

    @Nonnull
    private ConflictsGuard<String> getDeleteRestoreFoldersLockingMap() {
        HaAddon haAddon = addonsManager.addonByType(HaAddon.class);
        return haAddon.getConflictsGuard("delete_restore_folder");
    }

    public boolean acquireLock(@Nonnull String pathToLock) {
        try {
            log.debug("Attempting to acquire delete folder lock on folder '{}'", pathToLock);
            ConflictsGuard<String> deleteRestoreLocksMap = getDeleteRestoreFoldersLockingMap();
            // Check if there are any active delete/restore processes on parent or child folders
            boolean areRelatedProcessesActive = deleteRestoreLocksMap.keySet().stream()
                    .anyMatch(lockKey -> (lockKey.startsWith(pathToLock) || pathToLock.startsWith(lockKey))
                            && (deleteRestoreLocksMap.isLocked(lockKey)));

            if (areRelatedProcessesActive) {
                log.debug("Unable to acquire lock. There is an active delete/restore process on a related folder.");
                return false;
            }
            boolean lockAcquired = deleteRestoreLocksMap.tryToLock(pathToLock, 0L, TimeUnit.SECONDS);
            if (lockAcquired) {
                log.debug("Successfully acquired delete folder lock on folder '{}'", pathToLock);
            } else {
                log.debug("Unable to acquire lock on folder '{}'. Lock might be held by another process.", pathToLock);
            }
            return lockAcquired;
        } catch (InterruptedException e) {
            log.error("Failed to acquire delete folder lock on '{}'.", pathToLock, e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    public void unlock(@Nonnull String pathToUnlock) {
        getDeleteRestoreFoldersLockingMap().unlock(pathToUnlock);
    }
}