package org.artifactory.storage.db.locks.service;

import jakarta.annotation.Nonnull;
import org.artifactory.storage.db.dao.locks.LockInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * This DB locks service represents a faster, more optimized service which is encouraged to be used wherever possible.
 * @author Mor Merhav
 */
public interface NativeDbLocksService extends DbLocksService, ReleaseLocksDbLockService {

    String getSupportedVersion();

    @Override
    default void forceRemoveLocks(@Nonnull Set<LockInfo> locks, boolean removeCachedOnly) {
        // Not relevant for Native locks
    }

    @Override
    default void forceUnlockAllStuck() {
        // Not relevant for Native locks
    }

    @Override
    default int lockingMapSize(String category) {
        return 0;
    }

    @Override
    default Set<String> lockingMapKeySet(String category) {
        return Set.of();
    }

    @Override
    default void releaseAllLocksByOwnerExcludingKeys(String owner, Set<String> excludedLockKeys) {
        // Not relevant for Native locks
    }

    @Override
    default void releaseOrphanLocksByCategoryExcludingServers(Set<String> excludedServerIds, String category) {
        // Not relevant for Native locks
    }

    @Override
    default void releaseOrphanLocksExcludingServersAndCategories(Set<String> excludedServerIds,
            Set<String> excludedCategories) {
        // Not relevant for Native locks
    }

    /**
     * Returns a snapshot of advisory locks currently held by this JVM, sourced from the native lock
     * provider ({@code NativeDbLocksProvider#getAllLocksCopy()}). Implemented only by
     * {@code PostgresDbLocksService}; all other implementations return an empty collection.
     * <p>
     * The native provider is the authoritative source, so this snapshot is always consistent with
     * what PostgreSQL actually holds — including locks acquired before a {@code @Reloadable} reinit
     * and locks that have been expired by the background cleanup job.
     */
    default Collection<LockEntry> getHeldLocks() {
        return Collections.emptyList();
    }
}
