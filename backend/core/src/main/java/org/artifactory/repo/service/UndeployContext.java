package org.artifactory.repo.service;

import lombok.Data;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.repo.RepoPath;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Amir Avidan
 */
@Data
public class UndeployContext {
    private BasicStatusHolder statusHolder;
    private boolean shouldProtectPathDeletion;
    private long undeployStartTime;
    private boolean skipSystemFolder;
    private boolean fullRepoContentDeletion;
    private Set<RepoPath> pathsToProtect = new HashSet<>();

    public UndeployContext(BasicStatusHolder statusHolder, boolean shouldProtectPathDeletion, long undeployStartTime,
            boolean skipSystemFolder) {
        this(statusHolder, shouldProtectPathDeletion, undeployStartTime, skipSystemFolder, false);
    }

    public UndeployContext(BasicStatusHolder statusHolder, boolean shouldProtectPathDeletion, long undeployStartTime,
            boolean skipSystemFolder, boolean fullRepoContentDeletion) {
        this.statusHolder = statusHolder;
        this.shouldProtectPathDeletion = shouldProtectPathDeletion;
        this.undeployStartTime = undeployStartTime;
        this.skipSystemFolder = skipSystemFolder;
        this.fullRepoContentDeletion = fullRepoContentDeletion;
    }

    public void addPath(RepoPath path) {
        pathsToProtect.add(path);
    }
}
