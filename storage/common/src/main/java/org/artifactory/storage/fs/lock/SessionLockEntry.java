/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.storage.fs.lock;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.common.ConstantValues;
import org.artifactory.concurrent.LockingException;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.MutableVfsItem;
import org.jfrog.common.concurrency.JVMLockWrapper;
import org.jfrog.common.concurrency.LockWrapper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A session bound lock entry for a single {@link org.artifactory.repo.RepoPath}.
 *
 * @author Yossi Shaul
 */
@Slf4j
public class SessionLockEntry implements FsItemLockEntry {
    private final LockEntryId lockEntryId;
    private MutableVfsItem mutableItem;
    private final AtomicInteger counter = new AtomicInteger(-1);

    public SessionLockEntry(LockEntryId lockEntryId) {
        this.lockEntryId = lockEntryId;
    }

    @Override
    public MutableVfsItem getMutableFsItem() {
        return mutableItem;
    }

    @Override
    public void setWriteFsItem(MutableVfsItem mutableItem) {
        this.mutableItem = mutableItem;
    }

    @Override
    public void unlock() {
        releaseWriteLock();
    }

    @Override
    public boolean save() {
        log.trace("Saving lock entry {}", getRepoPath());
        if (!isWriteLockedByMe()) {
            String msg = "Cannot save item " + lockEntryId + " which not locked by me!";
            StringBuilder messageBuilder = new StringBuilder().append(msg);
            LockingDebugUtils.debugLocking(messageBuilder);
            throw new LockingException(msg);
        }
        if (mutableItem == null) {
            return false;
        }
        if (mutableItem.hasPendingChanges()) {
            log.debug("Saving item: {}", getRepoPath());
            return mutableItem.save();
        } else {
            log.trace("Item {} has no pending changes", getRepoPath());
            return true;
        }
    }

    public void acquireWriteLock() {
        log.trace("Acquiring WRITE lock on {}", lockEntryId);
        if (isWriteLockedByMe()) {
            // current thread already holds the write lock
            return;
        }

        acquire();
    }

    private void acquire() {
        LockWrapper lock = lockEntryId.getLock();
        try {
            if (!tryAcquireLock(lock)) {
                handleLockAcquisitionFailure(lock);
            }
        } catch (InterruptedException e) {
            throw new LockingException("Lock on " + lockEntryId + " not acquired!", e);
        }
    }

    private boolean tryAcquireLock(LockWrapper lock) throws InterruptedException {
        return lock.tryLock(ConstantValues.locksTimeoutSecs.getLong(), TimeUnit.SECONDS);
    }

    private void handleLockAcquisitionFailure(LockWrapper lock) {
        StringBuilder messageBuilder = buildLockFailureMessage(lock);
        try {
            logLockOwnerStackTrace(lock);
            if (ConstantValues.locksDebugTimeouts.getBoolean()) {
                LockingDebugUtils.debugLocking(lockEntryId, messageBuilder);
            }
        } catch (Exception e) {
            throw new LockingException(messageBuilder.toString(), e);
        }
        throw new LockingException(messageBuilder.toString());
    }

    private StringBuilder buildLockFailureMessage(LockWrapper lock) {
        return new StringBuilder()
                .append("Lock on ").append(lockEntryId)
                .append(" not acquired in ").append(ConstantValues.locksTimeoutSecs.getLong())
                .append(" seconds. Lock info: ").append(lock).append(".");
    }

    @VisibleForTesting
    void logLockOwnerStackTrace(LockWrapper lock) {
        boolean shouldLog = shouldLogLockOwnerStackTrace();
        if (shouldLog && lock instanceof JVMLockWrapper jvmLockWrapper) {
            Thread owner = jvmLockWrapper.lockOwner();
            StackTraceElement[] stackTrace = owner.getStackTrace();
            if (log.isDebugEnabled()) {
                log.debug("The lockEntryId {} requested by {} is acquired by {}", lockEntryId,
                        Thread.currentThread().getName(), owner.getName());
                for (StackTraceElement element : stackTrace) {
                    log.debug("\tat {}", element);
                }
            } else {
                log.warn("The lockEntryId {} requested by {} is acquired by {}", lockEntryId,
                        Thread.currentThread().getName(), owner.getName());
                for (StackTraceElement element : stackTrace) {
                    log.warn("\tat {}", element);
                }
            }
        }
    }

    private boolean shouldLogLockOwnerStackTrace() {
        return counter.incrementAndGet() % 1000 == 0 || log.isDebugEnabled();
    }

    private void releaseWriteLock() {
        log.trace("Releasing WRITE lock on {}", lockEntryId);
        if (isWriteLockedByMe()) {
            try {
                if (mutableItem != null) {
                    mutableItem.releaseResources();
                    if (mutableItem.hasPendingChanges()) {
                        // local modification will be discarded
                        log.warn("Mutable item '{}' has local modifications that will be discarded.", mutableItem);
                    }
                }
            } catch (Throwable e) {
                log.error("Exception while releasing resources on " + lockEntryId + " due to " + e.getMessage(), e);
            } finally {
                mutableItem = null;
                lockEntryId.getLock().unlock();
            }
        }
    }

    public boolean isWriteLockedByMe() {
        return lockEntryId.getLock().isHeldByCurrentThread();
    }

    private RepoPath getRepoPath() {
        return lockEntryId.getRepoPath();
    }

    public boolean isDeleted() {
        return isWriteLockedByMe() && mutableItem.isDeleted();
    }

}
