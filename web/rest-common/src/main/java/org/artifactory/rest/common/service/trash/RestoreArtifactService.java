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

package org.artifactory.rest.common.service.trash;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.common.ConstantValues;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.rest.common.model.trash.RestoreArtifact;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.folders.DeleteRestoreFolderLocks;
import org.artifactory.exception.http.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isTrashedEvidencePath;

/**
 * Restore an artifact from the trashcan to its original repository (or to the given destination)
 *
 * @author Hussein Alshqerat
 */
@Slf4j
@Component
public class RestoreArtifactService implements RestService {

    private final TrashService trashService;
    private final InternalRepositoryService internalRepositoryService;
    private final DeleteRestoreFolderLocks deleteRestoreFolderLocks;

    @Autowired
    public RestoreArtifactService(TrashService trashService, InternalRepositoryService internalRepositoryService,
            AddonsManager addonsManager) {
        this.trashService = trashService;
        this.internalRepositoryService = internalRepositoryService;
        this.deleteRestoreFolderLocks = new DeleteRestoreFolderLocks(addonsManager);
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        RestoreArtifact restoreArtifact = (RestoreArtifact) request.getImodel();
        RepoPath targetPath = RepoPathFactory.create(restoreArtifact.getTargetRepoKey(),
                restoreArtifact.getTargetPath());
        RepoPath sourcePath = RepoPathFactory.create(restoreArtifact.getRepoKey(),
                restoreArtifact.getPath());
        MoveMultiStatusHolder status;
        if (isTrashedEvidencePath(sourcePath.getPath())) {
            status = updateErrorStatus("Disallow direct restoration of artifacts from .evidence directory.: %s."
                    .formatted(sourcePath.getPath()));
            updateResponse(request, response, status);
        } else {
            ItemInfo sourceItemInfo = internalRepositoryService.getItemInfo(sourcePath);
            ItemInfo targetItemInfo = getTargetItem(targetPath);
            int transactionSize = restoreArtifact.getTransactionSize() != null ? restoreArtifact.getTransactionSize() :
                    ConstantValues.moveCopyDefaultTransactionSize.getInt();
            status = restoreItem(sourceItemInfo, targetItemInfo, targetPath, transactionSize);
            updateResponse(request, response, status);
        }
    }

    @Nonnull
    private MoveMultiStatusHolder restoreItem(@Nonnull ItemInfo sourceItemInfo, ItemInfo targetItemInfo,
            @Nonnull RepoPath targetPath, int transactionSize) {
        if (sourceItemInfo.isFolder()) {
            return restoreFolder(sourceItemInfo, targetItemInfo, targetPath, transactionSize);
        } else {
            return restoreArtifact(sourceItemInfo, targetItemInfo, targetPath, transactionSize);
        }
    }

    @Nonnull
    private MoveMultiStatusHolder restoreFolder(@Nonnull ItemInfo sourceItemInfo, ItemInfo targetItemInfo,
            @Nonnull RepoPath targetPath, int transactionSize) {
        boolean acquireSourceLock = false;
        boolean acquireTargetLock = false;
        try {
            acquireSourceLock = deleteRestoreFolderLocks.acquireLock(sourceItemInfo.getRepoPath().toPath());
            if (!acquireSourceLock) {
                return updateStatus(
                        "Can't restore the folder: %s, there is a running delete/restore process on the same folder."
                                .formatted(sourceItemInfo.getRepoPath().toPath()));
            }
            if (targetItemInfo != null && targetItemInfo.isFolder()) {
                acquireTargetLock = deleteRestoreFolderLocks.acquireLock(targetItemInfo.getRepoPath().toPath());
                if (!acquireTargetLock) {
                    return updateStatus(
                            ("Can't restore the folder: %s, there is a running delete/restore process on the target " +
                                    "folder.")
                                    .formatted(sourceItemInfo.getRepoPath().toPath()));
                }
            }
            return trashService.restoreBulk(sourceItemInfo.getRepoPath(), targetPath.getRepoKey(),
                    targetPath.getPath(), transactionSize);
        } finally {
            if (acquireSourceLock) {
                deleteRestoreFolderLocks.unlock(sourceItemInfo.getRepoPath().toPath());
            }
            if (acquireTargetLock) {
                deleteRestoreFolderLocks.unlock(targetItemInfo.getRepoPath().toPath());
            }
        }
    }

    @Nonnull
    private MoveMultiStatusHolder restoreArtifact(@Nonnull ItemInfo sourceItemInfo, ItemInfo targetItemInfo,
            @Nonnull RepoPath targetPath, int transactionSize) {
        boolean acquireLockOnTargetFolder = false;
        try {
            if (targetItemInfo != null && targetItemInfo.isFolder()) {
                acquireLockOnTargetFolder = deleteRestoreFolderLocks.acquireLock(targetItemInfo.getRepoPath().toPath());
                if (!acquireLockOnTargetFolder) {
                    return updateStatus(
                            "Can't restore file: %s, there is a running delete/restore process on the target folder."
                                    .formatted(sourceItemInfo.getRepoPath().toPath()));
                }
            }
            return trashService.restoreBulk(sourceItemInfo.getRepoPath(), targetPath.getRepoKey(),
                    targetPath.getPath(), transactionSize);
        } finally {
            if (acquireLockOnTargetFolder) {
                deleteRestoreFolderLocks.unlock(targetItemInfo.getRepoPath().toPath());
            }
        }
    }

    @Nonnull
    private MoveMultiStatusHolder updateStatus(@Nonnull String message) {
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        statusHolder.warn(message, log);
        return statusHolder;
    }

    @Nonnull
    private MoveMultiStatusHolder updateErrorStatus(@Nonnull String message) {
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        statusHolder.error(message, log);
        return statusHolder;
    }

    @Nullable
    private ItemInfo getTargetItem(@Nonnull RepoPath targetPath) {
        try {
            return internalRepositoryService.getItemInfo(targetPath);
        } catch (ItemNotFoundRuntimeException e) {
            return null;
        }
    }

    private void updateResponse(@Nonnull ArtifactoryRestRequest<?> request, @Nonnull RestResponse<?> response,
            @Nonnull MoveMultiStatusHolder status) {
        if (request.isUiRestCall()) {
            uiResponse(status, response);
        } else {
            apiResponse(status, response);
        }
    }

    private void uiResponse(@Nonnull MoveMultiStatusHolder status, @Nonnull RestResponse response) {
        if (status.isError()) {
            response.error(status.getLastError().getMessage());
        } else if (status.hasWarnings()) {
            response.warn(status.getLastWarning().getMessage());
        } else {
            response.info("Successfully restored trash items");
        }
    }

    private void apiResponse(@Nonnull MoveMultiStatusHolder statusHolder, @Nonnull RestResponse response) {
        if (statusHolder.isError()) {
            throw new BadRequestException(statusHolder.getLastError().getMessage());
        } else if (statusHolder.hasWarnings()) {
            throw new BadRequestException(statusHolder.getLastWarning().getMessage());
        } else {
            response.iModel("Successfully restored trash items");
            response.responseCode(HttpStatus.SC_ACCEPTED);
        }
    }
}