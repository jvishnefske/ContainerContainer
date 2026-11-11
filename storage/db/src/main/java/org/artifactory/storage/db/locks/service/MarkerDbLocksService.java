package org.artifactory.storage.db.locks.service;

/**
 * Used by the HA tasks to check for special marker lock existence by prefix.
 */
public interface MarkerDbLocksService extends DbLocksService {

    /**
     * Check if one or more locks which contain a prefix exist, excluding the provided lock key
     *
     * @param category - the lock category
     * @param lockPrefix - the prefix for which to check lock existence
     * @param excluded   - the excluded lock key
     * @param fuzzyExclusion - flag to setting fuzzy exclusion. If set to true, exclusion predicate filter shall use
     * {@link String#contains(CharSequence)}, otherwise {@link String#equals(Object)}
     * @return true if at least one lock with prefix exists, false otherwise
     */
    boolean isLockedByPrefixExcluding(String category, String lockPrefix, String excluded, boolean fuzzyExclusion);
}
