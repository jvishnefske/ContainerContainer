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

package org.artifactory.storage.db.fs.session;

import com.google.common.collect.Maps;
import lombok.NonNull;
import org.artifactory.repo.RepoPath;
import org.artifactory.storage.fs.lock.FsItemLockEntry;
import org.artifactory.storage.fs.lock.LockEntryId;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.session.StorageSession;
import org.artifactory.storage.tx.SessionResource;
import org.artifactory.storage.tx.SessionResourceManager;
import org.artifactory.storage.tx.SessionResourceManagerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a thread bound storage session in a context of a transaction.
 *
 * @author Yossi Shaul
 */
public class SqlStorageSession implements StorageSession {
    private static final Logger log = LoggerFactory.getLogger(SqlStorageSession.class);

    private final UUID sessionId = UUID.randomUUID();
    private final Map<RepoPath, SessionLockEntry> locks = Maps.newLinkedHashMap();
    private final Map<RepoPath, SessionLockEntry> pendingOperations = Maps.newLinkedHashMap();
    private final SessionResourceManager sessionResourceManager = new SessionResourceManagerImpl();
    /** Last {@link #afterCompletion(boolean)} outcome; read when running {@link #afterConnectionReleased()}. */
    private boolean lastTransactionCommitted;

    @Override
    public UUID getSessionId() {
        return sessionId;
    }

    @Override
    public void save() {
        log.trace("Save called on session {}", sessionId);
        if (pendingOperations.isEmpty()) {
            log.trace("Save called on session {} with no pending operations", sessionId);
            return;
        }

        log.debug("Save called on session {} with {} locked items and {} operations",
                sessionId, locks.size(), pendingOperations.size());

        List<RepoPath> savedEntries = new ArrayList<>();
        for (Map.Entry<RepoPath, SessionLockEntry> entry : pendingOperations.entrySet()) {
            RepoPath repoPath = entry.getKey();
            SessionLockEntry pendingOperation = entry.getValue();
            if (pendingOperation.isWriteLockedByMe()) {
                boolean saved = pendingOperation.save();
                if (saved) {
                    savedEntries.add(repoPath);
                } else {
                    log.debug("pending operation for {} was not saved, keeping it in the map", repoPath);
                }
            }
        }
        savedEntries.forEach(pendingOperations::remove);
    }

    @Override
    @Nonnull
    public FsItemLockEntry writeLock(LockEntryId lockEntryId) {
        log.trace("Acquiring write lock on {} in session {}", lockEntryId, sessionId);
        SessionLockEntry sessionLockEntry = getOrCreateSessionLockEntry(lockEntryId);
        sessionLockEntry.acquireWriteLock();
        return sessionLockEntry;
    }

    @Override
    public boolean removeLockEntry(RepoPath repoPath) {
        SessionLockEntry lockEntry = locks.remove(repoPath);
        // also remove any pending operations if exists
        pendingOperations.remove(repoPath);
        if (lockEntry == null) {
            log.debug("Removing lock entry {} in {} but not locked by me!", repoPath, sessionId);
            return false;
        } else {
            log.trace("Removed lock entry {} in {}", repoPath, sessionId);
            lockEntry.unlock();
            return true;
        }
    }

    @Override
    @Nullable
    public SessionLockEntry getLockEntry(RepoPath repoPath) {
        return locks.get(repoPath);
    }

    private SessionLockEntry getOrCreateSessionLockEntry(LockEntryId lockEntryId) {
        RepoPath repoPath = lockEntryId.getRepoPath();
        SessionLockEntry sessionLockEntry = getLockEntry(repoPath);
        if (sessionLockEntry == null) {
            log.trace("Creating new SLE for {} in {}", repoPath, sessionId);
            sessionLockEntry = new SessionLockEntry(lockEntryId);
            locks.put(repoPath, sessionLockEntry);
            pendingOperations.put(repoPath, sessionLockEntry);
        } else {
            log.trace("Reusing existing SLE for {} in {}", repoPath, sessionId);
        }
        return sessionLockEntry;
    }

    @Override
    public <T extends SessionResource> T getOrCreateResource(Class<T> resourceClass) {
        return getSessionResourceManager().getOrCreateResource(resourceClass);
    }

    @Override
    public void beforeCommit() {
        log.trace("before commit called on session {}", sessionId);
        save();
        sessionResourceManager.beforeCommit();
    }

    @Override
    public void afterCompletion(boolean success) {
        log.trace("After completion called on session {}. Commit success: {}", sessionId, success);
        lastTransactionCommitted = success;
    }

    @Override
    public void afterConnectionReleased() {
        log.trace("afterConnectionReleased called on session {}. Commit success: {}", sessionId,
                lastTransactionCommitted);
        sessionResourceManager.afterConnectionReleased(lastTransactionCommitted);
    }

    @Override
    public void releaseResources() {
        log.trace("Release resources called on session {}", sessionId);
        try {
            Collection<SessionLockEntry> lockEntries = locks.values();
            for (SessionLockEntry lockEntry : lockEntries) {
                lockEntry.unlock();
            }
        } finally {
            locks.clear();
            pendingOperations.clear();
        }
    }

    SessionResourceManager getSessionResourceManager() {
        return sessionResourceManager;
    }

    @Override
    public void addPendingOperation(@NonNull RepoPath repoPath) {
        SessionLockEntry sessionLockEntry = locks.get(repoPath);
        if (sessionLockEntry != null) {
            pendingOperations.put(repoPath, sessionLockEntry);
        }
    }
}