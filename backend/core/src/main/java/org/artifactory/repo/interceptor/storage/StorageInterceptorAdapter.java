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

package org.artifactory.repo.interceptor.storage;

import lombok.NonNull;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.StorageInterceptor;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;

import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;

/**
 * @author yoav
 */
public abstract class StorageInterceptorAdapter implements StorageInterceptor {

    @Override
    public void beforeCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder) {
    }

    @Override
    public void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            InterceptorCreateContext ctx) {
    }

    @Override
    public boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder) {
        return true;
    }

    @Override
    public boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder, DeleteContext ctx) {
        return canDelete(repoPath, statusHolder);
    }

    @Override
    public void assertDeletePathAllowed(RepoPath repoPath, MutableStatusHolder status) {
    }

    @Override
    public void assertOverwriteAllowed(RepoPath repoPath, MutableStatusHolder status) {
    }

    @Override
    public void assertDeleteRepoAllowed(String repoKey, MutableStatusHolder status) {
    }

    @Override
    public void beforeDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
    }

    @Override
    public void afterRepositoryContentDelete(String repoKey, MutableStatusHolder statusHolder) {
        //do nothing
    }

    @Override
    public CopyMoveCheckResult checkCopyMove(VfsItem<? extends ItemInfo> sourceItem,
            RepoPath targetRepoPath, MutableStatusHolder status) {
        return ALLOW;
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
    }

    @Override
    public void beforeCopyFiles(@NonNull BulkCopyMoveContext context) {
        //do nothing
    }

    @Override
    public void beforeMoveFiles(@NonNull BulkCopyMoveContext context) {
        //do nothing
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
    }

    @Override
    public void beforeMove(VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder, Properties properties) {
    }

    @Override
    public void beforePropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
    }

    @Override
    public void afterPropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
    }

    @Override
    public void beforePropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key) {
    }

    @Override
    public void afterPropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key) {
    }
}