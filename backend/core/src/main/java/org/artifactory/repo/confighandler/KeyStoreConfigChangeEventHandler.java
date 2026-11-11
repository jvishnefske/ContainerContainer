package org.artifactory.repo.confighandler;

import org.artifactory.repo.RepoPath;
import org.artifactory.repo.virtual.VirtualRepo;
import org.artifactory.sapi.common.Lock;

/**
 * @author Mor Merhav
 */
public interface KeyStoreConfigChangeEventHandler {
    @Lock
    void undeployRepoInTransaction(VirtualRepo virtualRepo, RepoPath repoPath);
}
