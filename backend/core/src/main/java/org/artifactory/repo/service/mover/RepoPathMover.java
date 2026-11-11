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

import jakarta.annotation.PostConstruct;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.LayoutsCoreAddon;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.Request;
import org.artifactory.api.security.SecurityService;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.Repo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.validators.StorageQuotaMoveCopyValidator;
import org.artifactory.sapi.fs.VfsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Moves a folder repo path from one local repository to another non-cache local repository. Only files and folders that
 * are accepted by the target repository will be moved. This class is stateless.
 *
 * @author Yossi Shaul
 */
@Component
public abstract class RepoPathMover {
    private static final Logger log = LoggerFactory.getLogger(RepoPathMover.class);

    private AddonsManager addonsManager;
    private InternalRepositoryService repositoryService;
    private ProjectsService projectsService;
    private StorageQuotaMoveCopyValidator storageQuotaMoveCopyValidator;

    @Request(aggregateEventsByTimeWindow = true)
    public abstract void executeOperation(MoveMultiStatusHolder status, MoverConfig moverConfig);

    protected abstract void handleMoveOrCopy(MoveMultiStatusHolder status, MoverConfig moverConfig,
            VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp);


    @Request(aggregateEventsByTimeWindow = true)
    public void moveOrCopy(MoveMultiStatusHolder status, MoverConfig moverConfig) {
        boolean isDryRun = moverConfig.isDryRun();
        RepoPath fromRepoPath = moverConfig.getFromRepoPath();

        // don't output to the logger if executing in dry run
        status.setActivateLogging(!isDryRun);

        StoringRepo sourceRepo = getStoringRepo(fromRepoPath.getRepoKey());
        VfsItem sourceItem = sourceRepo.getImmutableFsItem(fromRepoPath);
        if (sourceItem == null) {
            status.warn("Could not find item at " + fromRepoPath, HttpStatus.SC_BAD_REQUEST, log);
            return;
        }
        RepoPath targetLocalRepoPath = moverConfig.getTargetLocalRepoPath();

        if (handleSamePath(status, fromRepoPath, targetLocalRepoPath)) {
            return;
        }

        RepoRepoPath<StoringRepo> targetRrp = repositoryService.getRepoRepoPath(targetLocalRepoPath);

        throwExceptionIfTargetRepoIsForbidden(moverConfig, targetLocalRepoPath, targetRrp);
        // check for preconditions for Projects storage quota to save redundant DB calls
        if (projectStorageQuotaDenied(status, moverConfig)) {
            return;
        }

        LayoutsCoreAddon layoutsCoreAddon = addonsManager.addonByType(LayoutsCoreAddon.class);
        if (canCrossLayouts(moverConfig, sourceRepo, targetRrp, layoutsCoreAddon)) {
            layoutsCoreAddon.performCrossLayoutMoveOrCopy(status, moverConfig,
                    sourceRepo, targetRrp.getRepo(), sourceItem);
        } else {
            handleMoveOrCopy(status, moverConfig, sourceItem, targetRrp);
        }
    }

    private boolean projectStorageQuotaDenied(MoveMultiStatusHolder status, MoverConfig moverConfig) {
        MoveCopyItemInfo moveCopyItemInfo = new MoveCopyItemInfo(
                moverConfig.getFromRepoPath(), moverConfig.getTargetLocalRepoPath(), null, null, null, null, null);
        return !storageQuotaMoveCopyValidator.validate(moveCopyItemInfo, status, null);
    }

    private boolean handleSamePath(MoveMultiStatusHolder status, RepoPath fromRepoPath, RepoPath targetLocalRepoPath) {
        if (fromRepoPath.equals(targetLocalRepoPath)) {
            status.error(
                    String.format("Skipping %s %s: Destination and source are the same", operationType(), fromRepoPath),
                    HttpStatus.SC_BAD_REQUEST, log);
            return true;
        }
        return false;
    }

    private void throwExceptionIfTargetRepoIsForbidden(MoverConfig moverConfig, RepoPath targetLocalRepoPath,
            RepoRepoPath<StoringRepo> targetRrp) {
        String currentUsername = ContextHelper.get().beanForType(SecurityService.class).currentUsername();
        if (!SecurityService.USER_SYSTEM.equals(currentUsername)) {
            if (repositoryService.isRemoteRepoExist(targetLocalRepoPath.getRepoKey())) {
                throw new IllegalArgumentException(String.format("Target repository %s is a remote repository. " +
                                "%s to remote repositories is not allowed.",
                        targetLocalRepoPath.getRepoKey(), operationType()));
            }
            if (repositoryService.isVirtualRepoExist(targetLocalRepoPath.getRepoKey())) {
                throw new IllegalArgumentException(String.format("Target repository %s is a virtual repository. " +
                                "%s to virtual repositories is not allowed.",
                        targetLocalRepoPath.getRepoKey(), operationType()));
            }
        }
        if (targetRrp.getRepo().isCache() && !moverConfig.isToCache()) {
            throw new IllegalArgumentException(String.format("Target repository %s is a cache repository. " +
                    "%s to cache repositories is not allowed.", targetLocalRepoPath.getRepoKey(), operationType()));
        }
    }

    private boolean canCrossLayouts(MoverConfig moverConfig, StoringRepo sourceRepo,
            RepoRepoPath<StoringRepo> targetRrp,
            LayoutsCoreAddon layoutsCoreAddon) {
        return !moverConfig.isSuppressLayouts() &&
                layoutsCoreAddon.canCrossLayouts(sourceRepo.getDescriptor().getRepoLayout(),
                        targetRrp.getRepo().getDescriptor().getRepoLayout());
    }

    protected abstract String operationType();


    protected StoringRepo getStoringRepo(String repoKey) {
        Repo targetRepo = repositoryService.storingRepositoryByKey(repoKey);
        if (targetRepo instanceof StoringRepo) {
            return (StoringRepo) targetRepo;
        }
        throw new IllegalArgumentException("repository " + repoKey +
                " not found (repository doesn't store artifacts or doesn't exist)");
    }

    protected LocalRepo getLocalRepo(String repoKey) {
        LocalRepo targetLocalRepo = repositoryService.localFederatedOrCachedRepositoryByKey(repoKey);
        if (targetLocalRepo == null) {
            throw new IllegalArgumentException("Local repository " + repoKey +
                    " not found (repository is not local or doesn't exist)");
        }
        return targetLocalRepo;
    }

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setRepositoryService(InternalRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @PostConstruct
    public void initStorageQuotaValidator() {
        storageQuotaMoveCopyValidator = new StorageQuotaMoveCopyValidator(repositoryService, projectsService);
    }
}
