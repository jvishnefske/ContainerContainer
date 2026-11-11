package org.artifactory.repo.interceptor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.storage.StorageInterceptorAdapter;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.unifiedevent.UnifiedEventService;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * An interceptor that executes Workers scripts
 *
 * @author Dvir Twina
 */
@Slf4j
@Component
public class UnifiedEventsInterceptor extends StorageInterceptorAdapter {

    @Autowired
    private AddonsManager addonsManager;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private UnifiedEventService unifiedEventService;

    @Override
    public void afterCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            InterceptorCreateContext ctx) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Running Workers addon for AfterCreate event on ItemInfo '{}'", fsItem.getInfo().getRepoPath());
            unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.AFTER_CREATE, fsItem, null, null);
        }
    }

    @Override
    public void beforeCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Running Workers addon for BeforeCreate event on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.BEFORE_CREATE, fsItem, null, null);
        }
    }

    @Override
    public void beforeCopyFiles(@NonNull BulkCopyMoveContext context) {
        for (VfsItem<? extends ItemInfo> item : context.getSourceItems()) {
            RepoPath targetRepoPath = context.getTargetRepoPaths().get(context.getSourceItems().indexOf(item));
            Properties properties = item.getProperties();
            this.beforeCopyFileHelper(item, targetRepoPath, properties);
        }
    }

    @Override
    public void afterMove(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        ItemInfo source = sourceItem.getInfo();
        RepoPath targetPath = targetItem != null ? targetItem.getInfo().getRepoPath() : null;
        if (isContainingRepoLocal(sourceItem) && isContainingRepoLocal(targetItem)) {
            log.debug("Running Workers addon for AfterMove event for source item '{}' and target item '{}'",
                    source.getRepoPath(), targetPath);
            unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.AFTER_MOVE, sourceItem, null, targetPath);
        } else {
            log.debug("Source '{}' and/or Target '{}' is not of Local, Cache or Federated repo. " +
                            "Not running Workers addon for AfterMove event.",
                    source.getRepoPath(), targetPath);
        }
    }

    @Override
    public void beforeMove(@NonNull VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder, Properties properties) {
        /*
         * For beforeMove worker event, only source repository is required to be configured
         * and worker will be triggered only when source repository is configured.
         */

        ItemInfo source = sourceItem.getInfo();
        log.debug("Running workers AddOn for BeforeMove event for source item '{}' " +
                "and target item '{}'", source.getRepoPath(), targetRepoPath);
        unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.BEFORE_MOVE, sourceItem, null, targetRepoPath);
    }

    @Override
    public void beforeMoveFiles(@NonNull BulkCopyMoveContext ctx) {
        for (VfsItem<? extends ItemInfo> item : ctx.getSourceItems()) {
            RepoPath targetRepoPath = ctx.getTargetRepoPaths().get(ctx.getSourceItems().indexOf(item));
            MutableStatusHolder statusHolder = ctx.getStatusHolder();
            Properties properties = item.getProperties();
            this.beforeMove(item, targetRepoPath, statusHolder, properties);
        }
    }

    @Override
    public void beforePropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Dispatching unified event for BeforePropertyCreate on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendPropertyEvent(SupportedUnifiedEvents.BEFORE_PROPERTY_CREATE,
                    fsItem, key, Arrays.asList(values));
        }
    }

    @Override
    public void beforeDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Running Workers addon for BeforeDelete event on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.BEFORE_DELETE, fsItem, ctx, null);
        }
    }

    @Override
    public void beforePropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder,
            String key) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Dispatching unified event for BeforePropertyDelete on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendPropertyEvent(SupportedUnifiedEvents.BEFORE_PROPERTY_DELETE,
                    fsItem, key, null);
        }
    }

    @Override
    public void afterCopy(VfsItem<? extends ItemInfo> sourceItem, VfsItem<? extends ItemInfo> targetItem,
            MutableStatusHolder statusHolder, Properties properties, InterceptorMoveCopyContext ctx) {
        ItemInfo source = sourceItem.getInfo();
        RepoPath targetPath = null;
        if (targetItem != null) {
            targetPath = targetItem.getInfo().getRepoPath();
        }
        if (isContainingRepoLocal(sourceItem) && isContainingRepoLocal(targetItem)) {
            log.debug("Running Workers addon for AfterCopy event for source item '{}' and target item '{}'",
                    source.getRepoPath(), targetPath);
            unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.AFTER_COPY, sourceItem, null, targetPath);
        } else {
            log.debug("Source '{}' and/or Target '{}' should be of type Local/Cache/Federated. " +
                            "Not running Workers addon for AfterCopy event.",
                    source.getRepoPath(), targetPath);
        }
    }

    private void beforeCopyFileHelper(@NonNull VfsItem<? extends ItemInfo> sourceItem, RepoPath targetRepoPath,
            Properties properties) {
        /*
         * For beforeCopy worker event, only source repository is required to be configured
         * and worker will be triggered only when source repository is configured.
         */
        ItemInfo sourceItemInfo = sourceItem.getInfo();
        log.debug("Running workers AddOn for BeforeCopy event for source item '{}' " +
                "and target item '{}'", sourceItemInfo.getRepoPath(), targetRepoPath);

        unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.BEFORE_COPY, sourceItem, null, targetRepoPath);
    }

    @Override
    public void afterPropertyDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String name) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Dispatching unified event for AfterPropertyDelete event on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendPropertyEvent(SupportedUnifiedEvents.AFTER_PROPERTY_DELETE, fsItem, name,
                    null);
        }
    }

    @Override
    public void afterDelete(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Running Workers addon for AfterDelete event on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendStorageEvent(SupportedUnifiedEvents.AFTER_DELETE, fsItem, ctx, null);
        }
    }

    @Override
    public void afterPropertyCreate(VfsItem<? extends ItemInfo> fsItem, MutableStatusHolder statusHolder, String key,
            String... values) {
        if (isContainingRepoLocal(fsItem)) {
            log.debug("Dispatching unified event for AfterPropertyCreate event on ItemInfo '{}'", fsItem.getInfo());
            unifiedEventService.sendPropertyEvent(SupportedUnifiedEvents.AFTER_PROPERTY_CREATE, fsItem, key,
                    Arrays.asList(values));
        }
    }

    private boolean isContainingRepoLocal(VfsItem<? extends ItemInfo> fsItem) {
        boolean isLocal = fsItem != null &&
                repositoryService.localFederatedOrCachedRepoDescriptorByKey(fsItem.getRepoKey()) != null;

        if (fsItem == null) {
            log.debug("ItemInfo is null. Not running Workers addon.");
        } else if (!isLocal) {
            log.debug("ItemInfo '{}' is not of Local, Cache or Federated repo. " +
                    "Not running Workers addon.", fsItem.getRepoPath());
        }
        return isLocal;
    }
}
