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

package org.artifactory.repo.service.flexible.listeners;

import ch.qos.logback.classic.Level;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.properties.PropertiesImpl;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;

import java.util.List;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;

/**
 * @author gidis
 */
@Slf4j
public class StorageMoveCopyListener implements MoveCopyListeners {
    
    private StorageInterceptors storageInterceptors;

    public StorageMoveCopyListener(StorageInterceptors storageInterceptors) {
        this.storageInterceptors = storageInterceptors;
    }

    @Override
    public void notifyAfterMoveCopyFiles(@NonNull List<MoveCopyItemInfo> items,
            @NonNull MoveCopyContext context,
            @NonNull MoveMultiStatusHolder status) {

        if (!context.isDryRun()) {
            for (MoveCopyItemInfo itemInfo : items) {
                if (itemInfo.getMutableTargetItem().isFile()) {
                    handleFile(context, status, itemInfo);
                } else {
                    handleDir(context, status, itemInfo);
                }
            }
        }
    }

    private void handleDir(@NonNull MoveCopyContext context, @NonNull MoveMultiStatusHolder status,
            @NonNull MoveCopyItemInfo itemInfo) {

        if (shouldRemoveSourceFolder((VfsFolder) itemInfo.getSourceItem(), context, status)) {
            if (context.isCopy()) {
                storageInterceptors.afterCopy(itemInfo.getSourceItem(), itemInfo.getMutableTargetItem(), status,
                        new PropertiesImpl(), new InterceptorMoveCopyContext());
            } else {
                storageInterceptors.afterMove(itemInfo.getSourceItem(), itemInfo.getMutableTargetItem(), status,
                        new PropertiesImpl(), new InterceptorMoveCopyContext());
            }
        }
    }

    private void handleFile(@NonNull MoveCopyContext context, @NonNull MoveMultiStatusHolder status,
            @NonNull MoveCopyItemInfo moveCopyItemInfo) {
        final ItemInfo itemInfo = moveCopyItemInfo.getTargetItem().getInfo();
        if (itemInfo.isFolder()) {
            final String errorMessage = "Target item '" + moveCopyItemInfo.getTargetRepoPath() +
                    "' was expected to be a file but is actually a folder.";
            status.errorWithDifferentLogLevel(errorMessage, SC_BAD_REQUEST, null, log, Level.WARN);
        } else {
            final InterceptorMoveCopyContext ctx = new InterceptorMoveCopyContext((FileInfo) itemInfo);
            if (context.isCopy()) {
                storageInterceptors.afterCopy(moveCopyItemInfo.getSourceItem(), moveCopyItemInfo.getMutableTargetItem(),
                        status, new PropertiesImpl(), ctx);
            } else {
                storageInterceptors.afterMove(moveCopyItemInfo.getSourceItem(), moveCopyItemInfo.getMutableTargetItem(),
                        status, new PropertiesImpl(), ctx);
            }
        }
    }

    /**
     * If not in a dry run, If not pruning empty folders (if true it will happen at a later stage),
     * If not copying (no source removal when copying), If not on the root item (a repo),
     * If not containing any children and folders or artifacts were moved.
     */
    protected boolean shouldRemoveSourceFolder(VfsFolder sourceFolder, MoveCopyContext context,
            MoveMultiStatusHolder status) {
        return !context.isDryRun() && !context.isCopy() && !sourceFolder.getRepoPath().isRoot() &&
                !sourceFolder.hasChildren() &&
                (status.getMovedFoldersCount() != 0 || status.getMovedArtifactsCount() != 0);
    }

    @Override
    public void notifyBeforeMoveCopyFiles(
            @NonNull List<MoveCopyItemInfo> items,
            @NonNull MoveCopyContext context,
            @NonNull MoveMultiStatusHolder status) {

        BulkCopyMoveContext bulkContext = new BulkCopyMoveContext(status);
        for (MoveCopyItemInfo item : items) {
            if (isInterested(item, context)) {
                bulkContext.getSourceItems().add(item.getSourceItem());
                bulkContext.getTargetRepoPaths().add(item.getTargetRepoPath());
            }
        }
        storageInterceptors.beforeCopyFiles(bulkContext);
    }

    @Override
    public boolean isInterested(MoveCopyItemInfo itemInfo, MoveCopyContext context) {
        return true;
    }
}
