package org.artifactory.addon.curation.wq;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoPath;
import org.artifactory.workqueue.WorkItem;

import jakarta.annotation.Nonnull;

/**
 * @author Andrey Tashlitsky
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class CurationCachedPackageEventWorkItem extends WorkItem {

    private RemoteRepoConfig remoteRepoConfig;
    private RepoPath repoPath;

    @Nonnull
    @Override
    public String getUniqueKey() {
        return repoPath.toPath();
    }
}
