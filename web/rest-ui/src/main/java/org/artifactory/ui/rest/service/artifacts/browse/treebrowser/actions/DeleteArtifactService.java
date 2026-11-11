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

package org.artifactory.ui.rest.service.artifacts.browse.treebrowser.actions;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.repo.cache.expirable.InternalMetadataChecker;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.rest.common.model.BaseModel;
import org.artifactory.rest.common.model.FeedbackMsg;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.common.util.folders.DeleteRestoreFolderLocks;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.action.DeleteArtifact;
import org.jfrog.client.util.PathUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.artifactory.repo.system.SystemRepos.TRASH_REPO_KEY;
import static org.artifactory.repo.webdav.WebdavStatus.SC_OK;

/**
 * @author Hussein Alshqerat
 */
@Slf4j
@Component
public class DeleteArtifactService implements RestService {

    public static final String TRASHCAN_CANNOT_BE_OVERRIDEN_MSG = "The trash can contains items with the same path.\n" +
            "Overridden items cannot be restored.";
    private final InternalRepositoryService internalRepositoryService;
    private final SecurityService securityService;
    private final DeleteRestoreFolderLocks deleteRestoreFolderLocks;

    @Autowired
    public DeleteArtifactService(InternalRepositoryService internalRepositoryService, SecurityService securityService,
            AddonsManager addonsManager) {
        this.internalRepositoryService = internalRepositoryService;
        this.securityService = securityService;
        this.deleteRestoreFolderLocks = new DeleteRestoreFolderLocks(addonsManager);
    }

    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        DeleteArtifact deleteArtifactModel = (DeleteArtifact) request.getImodel();
        boolean force = deleteArtifactModel.isForce();
        String repoKey = deleteArtifactModel.getRepoKey();
        String path = deleteArtifactModel.getPath();
        RepoPath blockedPath = blockedInternalMetadataPath(repoKey, path);
        if (blockedPath != null) {
            denyInternalMetadataDelete(response, blockedPath);
            return;
        }
        ItemInfo candidateItemInfoForDeletion = getItemInfo(repoKey, path);
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        if (candidateItemInfoForDeletion != null) {
            statusHolder = (BasicStatusHolder) (candidateItemInfoForDeletion.isFolder() ?
                    deleteFolderContent(candidateItemInfoForDeletion, force) :
                    deleteArtifact(candidateItemInfoForDeletion, force));
        } else {
            statusHolder.error("item not found.", SC_BAD_REQUEST, log);
        }
        updateResponseData(response, statusHolder);
    }

    /**
     * Deleting {@code .jfrog/} internal metadata is restricted to admins, mirroring the WebDAV/REST delete
     * handler. Guarded by a feature flag for safe rollback.
     * <p>
     * The path is normalized before the check so that variants which still resolve under {@code .jfrog/}
     * (a leading slash, repeated slashes) cannot slip past it.
     *
     * @return the blocked path, or null when the delete is allowed to proceed
     */
    @Nullable
    private RepoPath blockedInternalMetadataPath(String repoKey, String path) {
        if (!ConstantValues.deleteBlockInternalMetadata.getBoolean() || securityService.isAdmin()) {
            return null;
        }
        RepoPath repoPath = InternalRepoPathFactory.create(repoKey, PathUtils.formatPath(path));
        return InternalMetadataChecker.isInternalMetadata(repoPath.getPath()) ? repoPath : null;
    }

    /**
     * Reports the block as a 404 so the response is indistinguishable from a missing path, disclosing the reason
     * through the response body only — matching the WebDAV and REST delete handlers.
     */
    private void denyInternalMetadataDelete(RestResponse<?> response, RepoPath repoPath) {
        String message = "Internal metadata request detected for '" + repoPath + "'.";
        log.debug(message);
        response.error(message).responseCode(SC_NOT_FOUND);
    }

    @Nonnull
    private StatusHolder deleteFolderContent(@Nonnull ItemInfo candidateItemInfoForDeletion, boolean force) {
        RepoPath repoPath = candidateItemInfoForDeletion.getRepoPath();
        String path = repoPath.toPath();
        boolean locked = false;
        try {
            locked = deleteRestoreFolderLocks.acquireLock(path);
            if (!locked) {
                return updateStatusHolder(
                        "Can't delete folder: %s, there is a running delete/restore process on the same folder."
                                .formatted(path), SC_CONFLICT);
            }
            ItemInfo itemTrashInfo = getItemInfo(TRASH_REPO_KEY, path);
            if (itemTrashInfo != null) {
                return deleteTrashedItem(candidateItemInfoForDeletion, force, repoPath, path, itemTrashInfo);
            } else if (repoPath.isRoot()) {
                return deleteEntireRepositoryContent(repoPath);
            } else {
                return deleteFolderContent(repoPath);
            }
        } catch (ItemNotFoundRuntimeException e) {
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            statusHolder.error("item not found.", SC_BAD_REQUEST, log);
            return statusHolder;
        } finally {
            if (locked) {
                deleteRestoreFolderLocks.unlock(repoPath.toPath());
            }
        }
    }

    private StatusHolder deleteFolderContent(RepoPath repoPath) {
        return internalRepositoryService.undeployMultiTransaction(repoPath);
    }

    @Nonnull
    private BasicStatusHolder deleteEntireRepositoryContent(RepoPath repoPath) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        internalRepositoryService.deleteRepositoryContent(repoPath.getRepoKey(), statusHolder);
        return statusHolder;
    }

    @Nonnull
    private StatusHolder deleteTrashedItem(@Nonnull ItemInfo candidateItemInfoForDeletion, boolean force,
            @Nonnull RepoPath repoPath, @Nonnull String path, @Nonnull ItemInfo itemTrashInfo) {
        log.debug("Folder {} already exist in the trashcan.", path);
        if (force) {
            return itemTrashInfo.isFolder() ? forceDeleteFolder(repoPath, itemTrashInfo) :
                    forceDeleteArtifact(candidateItemInfoForDeletion, itemTrashInfo);
        } else {
            return updateStatusHolder(TRASHCAN_CANNOT_BE_OVERRIDEN_MSG, SC_CONFLICT);
        }
    }

    @Nonnull
    private StatusHolder deleteArtifact(@Nonnull ItemInfo candidateItemInfoForDeletion, boolean force) {
        try {
            RepoPath repoPath = candidateItemInfoForDeletion.getRepoPath();
            ItemInfo itemTrashInfo = getItemInfo(TRASH_REPO_KEY, repoPath.toPath());
            if (itemTrashInfo != null) {
                log.debug("item {} already exist in the trashcan.", repoPath.getPath());
                if (force) {
                    return itemTrashInfo.isFolder() ? forceDeleteFolder(repoPath, itemTrashInfo) :
                            forceDeleteArtifact(candidateItemInfoForDeletion, itemTrashInfo);
                } else {
                    return updateStatusHolder(TRASHCAN_CANNOT_BE_OVERRIDEN_MSG, SC_CONFLICT);
                }
            } else {
                return internalRepositoryService.undeployMultiTransaction(repoPath);
            }
        } catch (ItemNotFoundRuntimeException e) {
            BasicStatusHolder statusHolder = new BasicStatusHolder();
            statusHolder.error("item not found.", SC_BAD_REQUEST, log);
            return statusHolder;
        }
    }

    @Nonnull
    private StatusHolder forceDeleteFolder(@Nonnull RepoPath repoPath, @Nonnull ItemInfo itemTrashInfo) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        boolean acquireLockOnTrashedItem = false;
        try {
            acquireLockOnTrashedItem = deleteRestoreFolderLocks.acquireLock(itemTrashInfo.getRepoPath().toPath());
            if (!acquireLockOnTrashedItem) {
                return updateStatusHolder(
                        ("Can't override trashed item: %s, there is running delete/restore process on the same " +
                                "trashed folder.").formatted(itemTrashInfo.getRepoPath().toPath()), SC_CONFLICT);
            } else {
                if (ConstantValues.trashcanRemoveFolderOnOverwrite.getBoolean()) {
                    statusHolder =
                            (BasicStatusHolder) internalRepositoryService.undeployMultiTransaction(
                                    itemTrashInfo.getRepoPath());
                }
                if (!statusHolder.isError()) {
                    if (repoPath.isRoot()) {
                        return deleteEntireRepositoryContent(repoPath);
                    }
                    return deleteFolderContent(repoPath);
                } else {
                    return statusHolder;
                }
            }
        } finally {
            if (acquireLockOnTrashedItem) {
                deleteRestoreFolderLocks.unlock(itemTrashInfo.getRepoPath().toPath());
            }
        }
    }

    private StatusHolder forceDeleteArtifact(@Nonnull ItemInfo candidateArtifactInfoForDeletion,
            @Nonnull ItemInfo trashedItemInfo) {
        RepoPath itemPath = candidateArtifactInfoForDeletion.getRepoPath();
        RepoPath itemTrashPath = trashedItemInfo.getRepoPath();
        BasicStatusHolder statusHolder;
        statusHolder = (BasicStatusHolder) internalRepositoryService.undeployMultiTransaction(itemTrashPath);
        if (!statusHolder.isError()) {
            return internalRepositoryService.undeployMultiTransaction(
                    candidateArtifactInfoForDeletion.getRepoPath());
        }
        return internalRepositoryService.undeployMultiTransaction(itemPath);
    }

    private void updateResponseData(@Nonnull RestResponse<DeleteResponse> artifactoryResponse,
            @Nonnull StatusHolder statusHolder) {
        if (statusHolder.isError()) {
            artifactoryResponse.error(statusHolder.getLastError().getMessage());
            artifactoryResponse.responseCode(statusHolder.getLastError().getStatusCode());
        } else if (statusHolder.isWarning()) {
            updateWarningResponse(artifactoryResponse, statusHolder);
        } else {
            artifactoryResponse.responseCode(SC_OK);
            artifactoryResponse.info("Successfully deleted artifacts");
        }
    }

    @Nonnull
    private BasicStatusHolder updateStatusHolder(@Nonnull String message, int statusCode) {
        BasicStatusHolder statusHolder = new BasicStatusHolder();
        statusHolder.warn(message, statusCode, log);
        return statusHolder;
    }

    private static void updateWarningResponse(@Nonnull RestResponse<DeleteResponse> artifactoryResponse,
            @Nonnull StatusHolder statusHolder) {
        DeleteResponse deleteResponse = new DeleteResponse();
        deleteResponse.setOverride(!statusHolder.getStatusMsg().contains("running delete/restore"));
        FeedbackMsg message = new FeedbackMsg();
        message.setWarn(statusHolder.getStatusMsg());
        deleteResponse.setFeedbackMsg(message);
        artifactoryResponse.responseCode(statusHolder.getLastWarning().getStatusCode());
        artifactoryResponse.iModel(deleteResponse);
    }

    @Nullable
    private ItemInfo getItemInfo(@Nonnull String repoKey, @Nonnull String itemPath) {
        try {
            return internalRepositoryService.getItemInfo(RepoPathFactory.create(repoKey, itemPath));
        } catch (ItemNotFoundRuntimeException e) {
            log.debug("item {} not exist in the trashcan.", itemPath);
            return null;
        }
    }

    @Setter
    private static class DeleteResponse extends BaseModel {
        @SuppressWarnings("unused")
        @JsonProperty("is_override")
        boolean isOverride;
    }
}
