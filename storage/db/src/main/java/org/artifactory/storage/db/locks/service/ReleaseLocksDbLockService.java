package org.artifactory.storage.db.locks.service;

import java.util.Set;

/**
 * Used to release expired and orphaned locks
 * @see org.artifactory.concurrent.NonStandardLockCategory
 */
public interface ReleaseLocksDbLockService extends DbLocksService {

    /**
     * Release all the locks in a specific category that are owned by a node that is stale or down
     *
     * @param excludedServerIds - Server IDs for which not to release locks
     * @param category          - Specific category of locks to release
     */
    void releaseOrphanLocksByCategoryExcludingServers(Set<String> excludedServerIds, String category);

    /**
     * Release locks that are owned by a node that is stale or down
     *
     * @param excludedServerIds  - Server IDs for which not to release locks
     * @param excludedCategories - Lock categories for which not to release locks
     */
    void releaseOrphanLocksExcludingServersAndCategories(Set<String> excludedServerIds,
            Set<String> excludedCategories);

    /**
     * Release locks that are expired by the given max lock lease time
     *
     * @param excludedCategories      - Lock categories for which not to release locks
     * @param maxLockLeaseTimeMinutes - The maximum time threshold from which a lock is considered expired (in minutes)
     */
    void releaseExpiredLocksExcludingCategories(Set<String> excludedCategories, long maxLockLeaseTimeMinutes);

    /**
     * Release locks that are expired by the given max lock lease time and category
     *
     * @param category                - Lock category for which to release locks
     * @param maxLockLeaseTimeMinutes - The maximum time threshold from which a lock is considered expired (in minutes)
     */
    void releaseExpiredLocksByCategory(String category, int maxLockLeaseTimeMinutes);
}
