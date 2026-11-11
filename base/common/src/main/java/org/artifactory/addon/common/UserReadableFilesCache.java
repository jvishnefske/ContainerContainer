package org.artifactory.addon.common;

import org.artifactory.repo.RepoPath;

/**
 * This cache keeps the path to files that the user can read in the given repository with the given filename.
 *
 * @author Rotem Kfir
 */
public interface UserReadableFilesCache {

    String get(String repoKey, String fileName, String username);

    void put(String fileName, RepoPath repoPath, String username);

    void remove(String repoKey, String fileName, String username);
}
