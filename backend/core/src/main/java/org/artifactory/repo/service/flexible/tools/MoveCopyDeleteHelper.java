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

package org.artifactory.repo.service.flexible.tools;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsItem;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The class execute multiple items move/copy task.
 * Note In  case of move folder the source folder will not be deleted, the folder deletion should be executed
 * only after full copy of the entire folder.
 *
 * @author Eli Skoran
 */
@Slf4j
public class MoveCopyDeleteHelper {

    /**
     * Delete source files if move and collect source folders
     */
    static void deleteFilesIfMove(@NonNull List<MoveCopyItemInfo> itemsInfo, @NonNull MoveCopyContext context,
            @NonNull MoveMultiStatusHolder status) {
        List<MoveCopyItemInfo> filesToCopy = new ArrayList<>();
        for (MoveCopyItemInfo element : itemsInfo) {
            RepoRepoPath<LocalRepo<?, ?>> targetRrp = element.getTargetRrp();
            VfsItem<?> sourceItem = element.getSourceItem();
            VfsItem<?> targetItem = element.getTargetItem();
            if (!context.isDryRun()) {
                if (sourceItem.isFolder()) {
                    updateLogsAndStatus(targetItem, targetRrp, status);
                    List<MoveCopyItemInfo> foldersToDelete = context.getFoldersToDelete();
                    if (!context.isCopy()) {
                        // moved folder should be deleted
                        foldersToDelete.add(element);
                    }
                } else {
                    filesToCopy.add(element);
                }
            }
        }
        if (!context.isCopy()) {
            deleteFiles(filesToCopy);
        }
        if (!status.hasErrors()) {
            status.artifactsMoved((int) itemsInfo
                    .stream()
                    .filter(moveItem -> moveItem.getSourceItem().isFile())
                    .count());
        }
    }

    private static void deleteFiles(@NonNull List<MoveCopyItemInfo> itemsToDelete) {
        itemsToDelete.forEach(value -> {
            VfsItem<ItemInfo> sourceItem = value.getSourceItem();
            RepoRepoPath<LocalRepo<?, ?>> sourceRrp = value.getSourceRrp();
            MutableVfsItem<?> mutableFsItem = sourceRrp.getRepo().getMutableFsItem(sourceItem, sourceItem.getInfo(),
                    sourceItem.getProperties());
            if (mutableFsItem != null) {
                RepoPath sourceRepoPath = value.getSourceRepoPath();
                DeleteContext ctx = new DeleteContext(sourceRepoPath).triggeredByMove();
                mutableFsItem.delete(ctx);
            }
        });
    }

    private static void updateLogsAndStatus(@Nullable VfsItem<?> targetItem,
            @NonNull RepoRepoPath<LocalRepo<?, ?>> targetRrp,
            @NonNull MoveMultiStatusHolder status) {

        LocalRepo<?, ?> targetRepo = targetRrp.getRepo();
        RepoPath targetRepoPath =
                InternalRepoPathFactory.create(targetRepo.getKey(), targetRrp.getRepoPath().getPath());

        if (targetItem == null) {
            log.debug("Creating target folder {}", targetRepoPath);
        } else {
            log.debug("Target folder {} already exist", targetRepoPath);
        }
        status.folderMoved();
        // copy relevant metadata from source to target
        log.debug("Copying folder metadata to {}", targetRepoPath);
    }
}
