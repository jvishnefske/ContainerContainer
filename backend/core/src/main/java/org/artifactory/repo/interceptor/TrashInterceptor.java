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

import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.support.SupportAddon;
import org.artifactory.api.properties.PropertiesService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.descriptor.property.Property;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.repo.service.trash.TrashGCHelperImpl;
import org.artifactory.repo.trash.TrashService;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.storage.db.DbService;
import org.artifactory.util.RepoPathUtils;
import org.artifactory.util.evidence.EvidenceConfigurationUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An interceptor that copies items into the global trashcan
 *
 * @author Shay Yaakov
 */
@Slf4j
public class TrashInterceptor extends StorageInterceptorAdapter {

    private final TrashService trashService;
    private final PropertiesService propertiesService;
    private final RepositoryService repositoryService;
    private final AddonsManager addonsManager;
    private final TrashGCHelperImpl trashGcHelper;


    @Autowired
    public TrashInterceptor(TrashService trashService, PropertiesService propertiesService,
            RepositoryService repositoryService, AddonsManager addonsManager, TrashGCHelperImpl trashGcHelper) {
        this.trashService = trashService;
        this.propertiesService = propertiesService;
        this.repositoryService = repositoryService;
        this.addonsManager = addonsManager;
        this.trashGcHelper = trashGcHelper;
    }

    @Override
    public void beforeDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (!ctx.isTriggeredByMove() && !ctx.isSkipTrashcan()) {
            trashService.copyToTrash(fsItem.getRepoPath());
        }
    }

    @Override
    public void afterMove(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        if (!RepoPathUtils.isTrash(sourceItem.getRepoKey())) {
            return;
        }
        if (isTrashedEvidencePath(sourceItem.getRepoPath())) {
            log.debug("Skipping explicit evidence restoration from trashcan: {}", sourceItem.getRepoPath());
            return;
        }
        Map<Property, List<String>> propertiesMap = new HashMap<>();
        propertiesMap.put(new Property(TrashService.PROP_RESTORED_TIME),
                Lists.newArrayList(String.valueOf(System.currentTimeMillis())));
        propertiesService.addPropertyRecursivelyMultiple(targetItem.getRepoPath(), null, propertiesMap, false);
    }

    @Override
    public void beforeCreate(VfsItem fsItem, MutableStatusHolder statusHolder) {
        if (ConstantValues.sendOverwritesToTrashcan.getBoolean() && !isNewFile(fsItem) &&
                !addonsManager.addonByType(SupportAddon.class)
                        .isSupportBundlesRepo(fsItem.getRepoPath().getRepoKey())) {
            trashService.copyToTrash(fsItem.getRepoPath());
        }
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (this.shouldMarkFileForGC(fsItem, ctx)) {
            if (ConstantValues.gcEventsCleanupEnabled.getBoolean()) {
                ctx.markForEventsGc(true);
            } else if (ConstantValues.gcTrashBinariesCleanupEnabled.getBoolean()) {
                trashGcHelper.markTrashFileForGC((FileInfo) fsItem.getInfo());
            }
        }
    }

    private boolean shouldMarkFileForGC(VfsItem<? extends ItemInfo> fsItem, DeleteContext ctx) {
        boolean isFile = fsItem.isFile();
        boolean triggeredByMove = ctx.isTriggeredByMove();
        boolean triggeredByGc = ctx.isTriggeredByGc();
        boolean skipCopyToTrash = trashService.shouldSkipCopyToTrash(fsItem.getRepoPath());
        boolean skipTrashcan = ctx.isSkipTrashcan();
        boolean res = isFile
                && (!triggeredByMove || skipCopyToTrash)
                && !triggeredByGc
                && (skipCopyToTrash || skipTrashcan);
        log.trace("res: {}: isFile: {} && (!triggeredByMove: {} || skipCopyToTrash: {}) && triggeredByGc: !{} && " +
                        "(skipCopyToTrash = {} || skipTrashcan: {}), {}",
                res, isFile, triggeredByMove, skipCopyToTrash, triggeredByGc, skipCopyToTrash, skipTrashcan,
                fsItem.getRepoPath());
        return res;
    }

    private boolean isNewFile(VfsItem fsItem) {
        return fsItem.getId() == DbService.NO_DB_ID;
    }

    @Override
    public void beforeCopyFiles(@NonNull BulkCopyMoveContext context) {
        beforeMoveOrCopyFiles(context);
    }

    @Override
    public void beforeMoveFiles(@NonNull BulkCopyMoveContext context) {
        beforeMoveOrCopyFiles(context);
    }

    private void beforeMoveOrCopyFiles(@NonNull BulkCopyMoveContext context) {
        if (ConstantValues.sendOverwritesToTrashcan.getBoolean()) {
            trashService.copyFilesToTrash(repositoryService.getExistingRepoPaths(context.getTargetRepoPaths()));
        }
    }

    private boolean isTrashedEvidencePath(RepoPath repoPath) {
        String path = repoPath.getPath();
        String pathWithoutRepoKey = path.substring(path.indexOf('/') + 1);
        // the EvidenceStorageInterceptor has the needed permissions to handle the restoration of the evidence
        return EvidenceConfigurationUtil.isEvidencePath(pathWithoutRepoKey);
    }
}
