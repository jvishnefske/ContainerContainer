package org.artifactory.hqc.service.fullsync;

import org.artifactory.repo.RepoPath;

import java.io.File;

/**
 * @author Tomer Slutsky
 */
public interface FullSyncFileService {
    FullSyncRecord saveFileToSystemRepo(File file, long lastEventId, boolean addTimestamp);

    FullSyncRecord saveFileToSystemRepo(File file);

    void undeployFromRepo(String fileName);

    void deployToRepo(File file, RepoPath repoPath);

    String getFullSyncFileSuffix();
}
