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

package org.artifactory.repo.interceptor;

import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.plugin.PluginAction;
import org.artifactory.addon.plugin.PluginsAddon;
import org.artifactory.addon.plugin.storage.AfterCopyAction;
import org.artifactory.addon.plugin.storage.AfterCreateAction;
import org.artifactory.addon.plugin.storage.AfterDeleteAction;
import org.artifactory.addon.plugin.storage.AfterMoveAction;
import org.artifactory.addon.plugin.storage.AfterPropertyCreateAction;
import org.artifactory.addon.plugin.storage.AfterPropertyDeleteAction;
import org.artifactory.addon.plugin.storage.BeforeCopyAction;
import org.artifactory.addon.plugin.storage.BeforeCreateAction;
import org.artifactory.addon.plugin.storage.BeforeDeleteAction;
import org.artifactory.addon.plugin.storage.BeforeMoveAction;
import org.artifactory.addon.plugin.storage.BeforePropertyCreateAction;
import org.artifactory.addon.plugin.storage.BeforePropertyDeleteAction;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * An interceptor that executes plugin scripts
 *
 * @author Yoav Landman
 */
@Slf4j
public class PluginsInterceptor extends StorageInterceptorAdapter {

    @Inject
    AddonsManager addonsManager;

    @Autowired
    RepositoryService repoService;

    @Override
    public void beforeCreate(VfsItem fsItem, MutableStatusHolder statusHolder) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(BeforeCreateAction.class, null, fsItem.getInfo());
        }
    }

    @Override
    public void afterCreate(VfsItem fsItem, MutableStatusHolder statusHolder, InterceptorCreateContext ctx) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(AfterCreateAction.class, null, fsItem.getInfo());
        }
    }

    @Override
    public void beforeDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(BeforeDeleteAction.class, null, fsItem.getInfo());
        }
    }

    @Override
    public void afterDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(AfterDeleteAction.class, null, fsItem.getInfo());
        }
    }

    @Override
    public void beforeMove(VfsItem sourceItem, RepoPath targetRepoPath, MutableStatusHolder statusHolder,
            Properties properties) {
        if (isContainingRepoLocal(sourceItem)) {
            getPluginsAddon().execPluginActions(BeforeMoveAction.class, null, sourceItem.getInfo(), targetRepoPath,
                    properties);
        }
    }


    @Override
    public void afterMove(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        if (isContainingRepoLocal(sourceItem) && isContainingRepoLocal(targetItem)) {
            getPluginsAddon().execPluginActions(AfterMoveAction.class, null, sourceItem.getInfo(),
                    nullOrRepoPath(targetItem), properties);
        }
    }

    @Override
    public void beforeCopyFiles(@NonNull BulkCopyMoveContext context) {
        try {
            executePlugin(context, BeforeCopyAction.class);
        } catch (Exception e) {
            context.getStatusHolder().error(e.getMessage(), e, log);
        }
    }

    @Override
    public void beforeMoveFiles(@NonNull BulkCopyMoveContext context) {
        try {
            executePlugin(context, BeforeMoveAction.class);
        } catch (Exception e) {
            context.getStatusHolder().error(e.getMessage(), e, log);
        }
    }

    private void executePlugin(@NonNull BulkCopyMoveContext context, Class<? extends PluginAction> clazz) {
        if (getPluginsAddon().isPluginSupported(clazz)) {
            for (int i = 0; i < context.getSourceItems().size(); i++) {
                if (isContainingRepoLocal(context.getSourceItems().get(i))) {
                    getPluginsAddon().execPluginActions(clazz,
                            null,
                            context.getSourceItems().get(i).getInfo(),
                            context.getTargetRepoPaths().get(i),
                            context.getSourceItems().get(i).getProperties());
                }
            }
        }
    }

    @Override
    public void afterCopy(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        if (isContainingRepoLocal(sourceItem) && isContainingRepoLocal(targetItem)) {
            getPluginsAddon().execPluginActions(AfterCopyAction.class, null, sourceItem.getInfo(),
                    nullOrRepoPath(targetItem), properties);
        }
    }

    @Override
    public void beforePropertyCreate(VfsItem fsItem, MutableStatusHolder statusHolder, String key, String... values) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(BeforePropertyCreateAction.class, null, fsItem.getInfo(), key, values);
        }
    }

    @Override
    public void afterPropertyCreate(VfsItem fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(AfterPropertyCreateAction.class, null, fsItem.getInfo(), key, values);
        }
    }

    @Override
    public void beforePropertyDelete(VfsItem fsItem, MutableStatusHolder statusHolder, String key) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(BeforePropertyDeleteAction.class, null, fsItem.getInfo(), key);
        }
    }

    @Override
    public void afterPropertyDelete(VfsItem fsItem, MutableStatusHolder statusHolder, String key) {
        if (isContainingRepoLocal(fsItem)) {
            getPluginsAddon().execPluginActions(AfterPropertyDeleteAction.class, null, fsItem.getInfo(), key);
        }
    }

    private RepoPath nullOrRepoPath(VfsItem targetItem) {
        return targetItem != null ? targetItem.getRepoPath() : null;
    }

    private PluginsAddon getPluginsAddon() {
        return addonsManager.addonByType(PluginsAddon.class);
    }

    private boolean isContainingRepoLocal(VfsItem fsItem) {
        return fsItem != null && repoService.localFederatedOrCachedRepoDescriptorByKey(fsItem.getRepoKey()) != null;
    }
}
