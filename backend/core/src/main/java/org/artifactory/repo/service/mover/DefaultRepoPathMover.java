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

package org.artifactory.repo.service.mover;

import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;

/**
 * The ordinary repo-path move/copy implementation
 *
 * @author Noam Y. Tenne
 */
class DefaultRepoPathMover extends BaseRepoPathMover {

    DefaultRepoPathMover(MoveMultiStatusHolder status, MoverConfig moverConfig) {
        super(status, moverConfig);
    }

    @Override
    protected void beforeOperationOnFolder(VfsItem sourceItem, RepoPath targetRepoPath) {
        storageInterceptors.beforeMoveFiles(new BulkCopyMoveContext(sourceItem, targetRepoPath, status));
    }

    @Override
    public void beforeOperationOnFile(VfsItem sourceItem, RepoPath targetRepoPath) {
        storageInterceptors.beforeMoveFiles(new BulkCopyMoveContext(sourceItem, targetRepoPath, status));
    }

    @Override
    protected void afterOperationOnFolder(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp,
            VfsFolder targetFolder) {
        afterMoveFolder(sourceItem, targetFolder, properties);
    }

    @Override
    public void operationOnFile(VfsFile sourceItem, RepoRepoPath<StoringRepo> targetRrp,
            InterceptorMoveCopyContext ctx) {
        moveFile(sourceItem, targetRrp, properties, ctx);
    }

    @Override
    public VfsItem getSourceItem(VfsItem sourceItem) {
        return getLocalRepo(sourceItem.getRepoKey()).getMutableFsItem(sourceItem.getRepoPath());
    }

    /**
     * return local repo
     *
     * @param repoKey - repo key
     */
    private LocalRepo getLocalRepo(String repoKey) {
        LocalRepo targetLocalRepo = repositoryService.localFederatedOrCachedRepositoryByKey(repoKey);
        if (targetLocalRepo == null) {
            throw new IllegalArgumentException("Local repository " + repoKey +
                    " not found (repository is not local or doesn't exist)");
        }
        return targetLocalRepo;
    }
}