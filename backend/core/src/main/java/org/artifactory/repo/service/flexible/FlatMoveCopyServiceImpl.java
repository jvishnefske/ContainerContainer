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

package org.artifactory.repo.service.flexible;

import ch.qos.logback.classic.Level;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.aql.AqlService;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.model.AqlSortTypeEnum;
import org.artifactory.descriptor.repo.LocalRepoDescriptor;
import org.artifactory.exception.CancelException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.cache.expirable.CacheExpiry;
import org.artifactory.repo.cleanup.FolderPruningService;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.repo.service.flexible.interfaces.FlatMoveCopyService;
import org.artifactory.repo.service.flexible.interfaces.FlatMoveCopyServiceInternal;
import org.artifactory.repo.service.flexible.listeners.MoveCopyListeners;
import org.artifactory.repo.service.flexible.listeners.ReplicationMoveCopyListener;
import org.artifactory.repo.service.flexible.listeners.StorageMoveCopyListener;
import org.artifactory.repo.service.flexible.validators.AuthorizationMoveCopyValidator;
import org.artifactory.repo.service.flexible.validators.AuthorizationMoveValidator;
import org.artifactory.repo.service.flexible.validators.BasicMoveCopyValidator;
import org.artifactory.repo.service.flexible.validators.IncludeExcludeMoveCopyValidator;
import org.artifactory.repo.service.flexible.validators.MavenMoveCopyValidator;
import org.artifactory.repo.service.flexible.validators.MoveCopyValidator;
import org.artifactory.repo.service.flexible.validators.StorageQuotaMoveCopyValidator;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.spring.ContextCreationListener;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.MutableVfsItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.List;

import static org.artifactory.common.ConstantValues.excludeLocalGeneratedFlatCopyMove;
import static org.artifactory.repo.service.flexible.tools.MoveCopyHelper.NotifyType;
import static org.artifactory.repo.service.flexible.tools.MoveCopyHelper.createSourceQuery;
import static org.artifactory.repo.service.flexible.tools.MoveCopyHelper.isError;
import static org.artifactory.repo.service.flexible.tools.MoveCopyHelper.isErrorAndFailFast;
import static org.artifactory.repo.service.flexible.tools.MoveCopyHelper.notifyMoveCopyFiles;
import static org.artifactory.repo.service.flexible.tools.MoveCopyHelper.validateDeleteAndSaveSession;

/**
 * @author gidis
 */
@Slf4j
@Service
public class FlatMoveCopyServiceImpl implements FlatMoveCopyService, FlatMoveCopyServiceInternal,
        ContextCreationListener {

    protected final InternalRepositoryService repositoryService;
    private final AqlService aqlService;
    private AddonsManager addonsManager;
    private final AuthorizationService authService;
    private ProjectsService projectsService;
    private CacheExpiry cacheExpiry;

    @Autowired
    public void setAddonsManager(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Autowired
    public void setProjectsService(ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setCacheExpiryImpl(CacheExpiry cacheExpiry) {
        this.cacheExpiry = cacheExpiry;
    }

    protected final StorageInterceptors storageInterceptors;

    protected List<MoveCopyValidator> moveCopyValidators;
    protected List<MoveCopyListeners> moveCopyListeners;

    public FlatMoveCopyServiceImpl(
            InternalRepositoryService repositoryService,
            AqlService aqlService,
            AuthorizationService authService,
            StorageInterceptors storageInterceptors) {
        this.repositoryService = repositoryService;
        this.aqlService = aqlService;
        this.authService = authService;
        this.storageInterceptors = storageInterceptors;
    }

    @Override
    public void onContextCreated() {
        init();
    }

    private void init() {
        // Register validators
        moveCopyValidators = Lists.newArrayList();
        moveCopyListeners = Lists.newArrayList();
        moveCopyValidators.add(new BasicMoveCopyValidator());
        moveCopyValidators.add(new AuthorizationMoveCopyValidator(authService));
        moveCopyValidators.add(new AuthorizationMoveValidator(authService));
        moveCopyValidators.add(new IncludeExcludeMoveCopyValidator());
        moveCopyValidators.add(new MavenMoveCopyValidator());
        moveCopyValidators.add(new StorageQuotaMoveCopyValidator(repositoryService, projectsService));

        // Register listeners
        moveCopyListeners.add(new StorageMoveCopyListener(storageInterceptors));
        ReplicationAddon replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
        moveCopyListeners.add(new ReplicationMoveCopyListener(repositoryService, replicationAddon));
    }

    /**
     * Copy all files that are under a single path to some target repo/path
     */
    @Override
    @NonNull
    public MoveMultiStatusHolder moveCopy(@NonNull MoveCopyContext context) {
        // Create multi status holder that will aggregate all the move results.
        String from = context.getSourceRepoPath().toPath();
        String to = context.getTargetKey() + "/" + context.getTargetPath();
        log.debug("Starting move copy process from:{} to:{}", from, to);
        MoveMultiStatusHolder finalStatus = new MoveMultiStatusHolder();
        try {
            long start = System.currentTimeMillis();
            // Don't output to the logger if executing in dry run
            finalStatus.setActivateLogging(!context.isDryRun());
            // Create AQL query that return all source items to move/copy sorted by dept asc
            AqlApiItem sourceItemsQuery = createSourceQuery(context, AqlSortTypeEnum.asc);
            // Execute Aql query (stream based) and run the function
            log.trace("Starting move copy execution from:{} to:{}", from, to);
            executeMoveCopyTasksInBulks(context, sourceItemsQuery, finalStatus);
            // In case of any error don't delete the source
            if (isError(finalStatus)) {
                // Print error message
                printErrorMessageIfNeeded(context, from, to, finalStatus);
                return finalStatus;
            }
            // After copy/move completion, in case of move we need to delete the folders from the source
            deleteCollectedSourceDirectoriesIfMove(context, finalStatus);
            long end = System.currentTimeMillis();
            log.debug("Move cove process from:{} to:{} ended in:{} milliseconds", from, to, (end - start));
        } catch (Exception e) {
            String msg = "Failed to execute " + (context.isCopy() ? "copy" : "move") + " out items list to move";
            log.error(msg, e);
        }
        return finalStatus;
    }

    private void printErrorMessageIfNeeded(@NonNull MoveCopyContext context, String from, String to,
            MoveMultiStatusHolder finalStatus) {
        if (isErrorAndFailFast(context.isFailFast(), finalStatus)) {
            log.warn("Move copy process from: {} to: {} failed with fail fast and status: {}", from, to,
                    finalStatus);
        } else {
            log.warn("Move copy process from: {} to: {} failed and status: {}", from, to, finalStatus);
        }
    }

    private void deleteCollectedSourceDirectoriesIfMove(@NonNull MoveCopyContext context,
            MoveMultiStatusHolder finalStatus) {
        FolderPruningService pruningService = ContextHelper.get().beanForType(FolderPruningService.class);
        List<MoveCopyItemInfo> foldersToDelete = context.getFoldersToDelete();
        if (foldersToDelete.isEmpty()) {
            if (pruningService.isRepoToBePruned(context.getSourceRepoPath().getRepoKey())) {
                pruningService.prune(context.getSourceRepoPath().getParent());
            }
        } else {
            FlatMoveCopyServiceInternal txMe = ContextHelper.get().beanForType(FlatMoveCopyServiceInternal.class);
            // Get repoRepoPath
            RepoRepoPath<LocalRepo<LocalRepoDescriptor, LocalRepoConfig>> sourceRrp =
                    repositoryService.getRepoRepoPath(context.getSourceRepoPath());
            txMe.executeDeleteRootDir(sourceRrp, context, finalStatus);
        }
    }

    /**
     * Creates bulk of move/copy tasks (query results) each bulk will be executed in single transaction
     */
    @VisibleForTesting
    void executeMoveCopyTasksInBulks(@NonNull MoveCopyContext context,
            @NonNull AqlApiItem sourceItemsQuery,
            @NonNull MoveMultiStatusHolder status) throws SQLException {
        int transactionSize = context.getTransactionSize();
        try (BulkProvider bulkProvider = new BulkProvider(aqlService, sourceItemsQuery,
                transactionSize, repositoryService, context)) {
            while (bulkProvider.hasMore()) {
                FlatMoveCopyServiceInternal txMe = ContextHelper.get().beanForType(FlatMoveCopyServiceInternal.class);
                status.merge(txMe.executeMoveCopyOnBulk(bulkProvider, context));
            }
        } catch (SQLException e) {
            status.error("Failed to query items list to move", 501, e, log);
            throw e;
        } catch (CancelException e) {
            String msg = getErrorMessage(context);
            status.errorWithDifferentLogLevel(msg, e.getErrorCode(), e, log, Level.WARN);
        } catch (Exception e) {
            String msg = getErrorMessage(context);
            log.error(msg, e);
            status.error(msg, 501, e, log);
        }
    }

    /**
     * The method execute the root directory and its children (folders and items) in our case it is only folders since
     * the files where already deleted during the move file
     */
    @Override
    public void executeDeleteRootDir(
            @NonNull RepoRepoPath<LocalRepo<LocalRepoDescriptor, LocalRepoConfig>> rrp,
            @NonNull MoveCopyContext context,
            @NonNull MoveMultiStatusHolder status) {
        MoveMultiStatusHolder statusHolder = new MoveMultiStatusHolder();
        try {
            LocalRepo<?, ?> repo = rrp.getRepo();
            MutableVfsFolder mutableFolder = repo.getMutableFolder(rrp.getRepoPath());
            if (mutableFolder != null) {
                mutableFolder.delete(new DeleteContext(mutableFolder.getRepoPath()).triggeredByMove());
            } else {
                statusHolder
                        .error(String.format("About to delete %s but folder doesn't exist ", rrp.getRepoPath()), log);
            }
            List<MoveCopyItemInfo> foldersToDelete = context.getFoldersToDelete();
            notifyMoveCopyFiles(foldersToDelete, context, status, moveCopyListeners, NotifyType.AFTER);
        } catch (Exception e) {
            statusHolder
                    .error(String.format("Failed do delete folder %s during move/copy ", rrp.getRepoPath()), e, log);
        }
    }

    /**
     * The method execute bulk move/copy inside single transaction
     */
    @Override
    public MoveMultiStatusHolder executeMoveCopyOnBulk(BulkProvider bulkProvider, MoveCopyContext context) {
        List<MoveCopyItems> items = bulkProvider.getBulk();
        log.debug("Handling MoveCopyItems bulk for source RepoPath: {} with {} items",
                context.getSourceRepoPath(), items.size());
        List<MoveCopyItemInfo> itemInfos = items.stream()
                .map(itemInfo -> new MoveCopyItemInfo(
                        itemInfo.getSourceItem().getRepoPath(),
                        itemInfo.getTargetItem().getRepoPath(),
                        itemInfo.getSourceItem(),
                        itemInfo.getOriginalTargetItem() != null ?
                                itemInfo.getOriginalTargetItem() : getVfsItemFromMutableVfsItem(itemInfo),
                        repositoryService.getRepoRepoPath(itemInfo.getSourceItem().getRepoPath()),
                        repositoryService.getRepoRepoPath(itemInfo.getTargetItem().getRepoPath()),
                        itemInfo.getTargetItem()
                ))
                .filter(itemInfo -> excludeLocalGenerated(itemInfo.getTargetItem()))
                .toList();
        return validateDeleteAndSaveSession(itemInfos, context, moveCopyListeners, moveCopyValidators,
                storageInterceptors);
    }

    private boolean excludeLocalGenerated(VfsItem<ItemInfo> targetItem) {
        if (excludeLocalGeneratedFlatCopyMove.getBoolean()) {
            RepoConfig repoConfig = repositoryService.getRepositoryConfigByKey(targetItem.getRepoKey());
            if (repoConfig == null) {
                return false;
            }
            return !cacheExpiry.isLocalGenerated(repoConfig.getPackageType(), targetItem.getRepoKey(),
                    targetItem.getPath());
        } else {
            return true;
        }
    }

    private VfsItem<ItemInfo> getVfsItemFromMutableVfsItem(MoveCopyItems itemInfo) {
        MutableVfsItem<?> vfsItem = itemInfo.getTargetItem();
        return (VfsItem<ItemInfo>) vfsItem;
    }

    private static String getErrorMessage(@NonNull MoveCopyContext context) {
        return "Failed to execute " + (context.isCopy() ? "copy" : "move") + " out items list to move." +
                " For more details, see the log";
    }
}
