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

package org.artifactory.sapi.interceptor;

import lombok.NonNull;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.common.interceptor.Interceptor;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;

/**
 * @author yoav
 */
public interface StorageInterceptor extends Interceptor {

    void beforeCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder);

    void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            InterceptorCreateContext ctx);

    boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder);

    default boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder, DeleteContext ctx) {
        return canDelete(repoPath, statusHolder);
    }

    void assertDeletePathAllowed(RepoPath repoPath, MutableStatusHolder statusHolder);

    /**
     * Fires on the deploy permission flow before {@code beforeCreate} when an upload would overwrite
     * an existing artifact at the given path. Implementors override this hook to reject overwrite
     * operations specifically (as opposed to true deletes, which still route through
     * {@link #assertDeletePathAllowed}). Default is a no-op so existing implementors that do not
     * opt into overwrite blocking compile unchanged and reach {@code beforeCreate} on overwrite.
     */
    default void assertOverwriteAllowed(RepoPath repoPath, MutableStatusHolder statusHolder) {
    }

    void assertDeleteRepoAllowed(String repoKey, MutableStatusHolder statusHolder);

    void beforeDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx);

    void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx);

    void afterRepositoryContentDelete(String repoKey, MutableStatusHolder statusHolder);

    /**
     * This method performs a check to see if the item can be moved or copied.
     * If all implementations of this method do not reject, the operation is allowed.
     * This needs to run before activating the "before" interceptors to ensure this is a valid operation.
     *
     * @param sourceItem     the source item to be moved or copied
     * @param targetRepoPath the target repository path where the item will be moved or copied
     * @param statusHolder   holds the status of the operation
     */
    CopyMoveCheckResult checkCopyMove(VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder);

    /**
     * This method performs a check to see if the folder can be moved or copied.
     * If all implementations of this method do not reject, the operation is allowed.
     *
     * @param sourceItem     the source item to be moved or copied
     * @param targetRepoPath the target repository path where the item will be moved or copied
     * @param statusHolder   holds the status of the operation
     */
    default CopyMoveCheckResult checkDirCopyMove(VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder) {
        return CopyMoveCheckResult.ALLOW;
    }

    void beforeMove(VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath, MutableStatusHolder statusHolder,
            Properties properties);

    void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx);

    void beforeCopyFiles(@NonNull BulkCopyMoveContext context);

    void beforeMoveFiles(@NonNull BulkCopyMoveContext context);

    void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx);

    void beforePropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String name,
            String... values);

    void afterPropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String name,
            String... values);

    void beforePropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String name);

    void afterPropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String name);

}