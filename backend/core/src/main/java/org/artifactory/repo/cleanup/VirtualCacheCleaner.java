package org.artifactory.repo.cleanup;

import org.artifactory.model.VirtualRepoConfig;

/**
 * @author Yoaz Menda
 */
public interface VirtualCacheCleaner {

    /**
     * performs a cleanup of the virtual cache of the given virtual repo
     *
     * @param virtualRepoConfig - virtual repo key
     * @return - number of deleted cached files
     */
    long cleanCache(VirtualRepoConfig virtualRepoConfig);

    /**
     * determines whether or not this cleaner should run for the given repo
     *
     * @param virtualRepoConfig - virtual repo to check
     */
    boolean shouldRun(VirtualRepoConfig virtualRepoConfig);
}
