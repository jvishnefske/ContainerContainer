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

package org.artifactory.repo.service.mover;

import ch.qos.logback.classic.Level;
import com.google.common.collect.Maps;
import org.apache.http.HttpStatus;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.replication.ReplicationAddon;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.descriptor.repo.TrashRepoDescriptor;
import org.artifactory.factory.InfoFactoryHolder;
import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.InternalRepoPathFactory;
import org.artifactory.repo.LocalRepo;
import org.artifactory.repo.ReleaseBundlesRepo;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.cleanup.FolderPruningService;
import org.artifactory.repo.db.DbLocalRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.InternalRepositoryService;
import org.artifactory.repo.service.flexible.tools.MoveCopyHelper;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.spring.InternalContextHelper;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsFolder;
import org.artifactory.storage.fs.lock.LockingHelper;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.repo.ReleaseBundlesRepo.RELEASE_BUNDLE_REPO_COPY_MOVE_ERROR_MSG;
import static org.artifactory.repo.RepoPath.PATH_SEPARATOR;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.REJECT;

/**
 * The abstract repo path mover implementation
 *
 * @author Noam Y. Tenne
 */
public abstract class BaseRepoPathMover {
    private static final Logger log = LoggerFactory.getLogger(BaseRepoPathMover.class);
    protected final boolean copy;
    protected final boolean dryRun;
    protected final boolean failFast;
    protected final boolean unixStyleBehavior;
    protected final Properties properties;
    protected boolean overrideProperties;
    protected final MoveMultiStatusHolder status;
    protected final ReplicationAddon replicationAddon;
    private final boolean pruneEmptyFolders;
    protected AuthorizationService authorizationService;
    protected SecurityService securityService;
    protected StorageInterceptors storageInterceptors;
    protected ArtifactoryContext artifactoryContext;
    protected InternalRepositoryService repositoryService;

    protected BaseRepoPathMover(MoveMultiStatusHolder status, MoverConfig moverConfig) {
        this.status = status;
        artifactoryContext = ContextHelper.get();
        authorizationService = artifactoryContext.getAuthorizationService();
        securityService = artifactoryContext.beanForType(SecurityService.class);
        repositoryService = artifactoryContext.beanForType(InternalRepositoryService.class);
        storageInterceptors = artifactoryContext.beanForType(StorageInterceptors.class);
        AddonsManager addonsManager = artifactoryContext.beanForType(AddonsManager.class);
        replicationAddon = addonsManager.addonByType(ReplicationAddon.class);
        copy = moverConfig.isCopy();
        dryRun = moverConfig.isDryRun();
        failFast = moverConfig.isFailFast();
        unixStyleBehavior = moverConfig.isUnixStyleBehavior();
        pruneEmptyFolders = moverConfig.isPruneEmptyFolders();
        properties = initProperties(moverConfig);
        overrideProperties = moverConfig.isOverrideProperties();

        // don't output to the logger if executing in dry run
        this.status.setActivateLogging(!dryRun);
    }

    protected Properties initProperties(MoverConfig moverConfig) {
        Properties properties = moverConfig.getProperties();
        if (properties == null) {
            properties = (Properties) InfoFactoryHolder.get().createProperties();
        }
        return properties;
    }

    protected MoveMultiStatusHolder moveOrCopy(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp) {
        targetRrp = adjustTargetPathIfTargetExists(sourceItem, targetRrp);
        moveCopyRecursive(sourceItem, targetRrp);
        postMoveOperations(sourceItem, targetRrp);
        return status;
    }

    /**
     * move / copy in multi tx mode
     *
     * @param sourceItem source item to be copy or move
     * @param targetRrp  - target repo
     * @return - move/copy status holder
     */
    protected MoveMultiStatusHolder moveOrCopyMultiTx(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp) {
        targetRrp = adjustTargetPathIfTargetExists(sourceItem, targetRrp);
        Map<String, String> folderMoverMap = Maps.newHashMap();
        moveCopyRecursiveMultiTx(sourceItem, targetRrp, folderMoverMap);
        postMoveOperations(sourceItem, targetRrp);
        return status;
    }

    private void postMoveOperations(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp) {
        if (!dryRun) {
            clearEmptySourceDirs(sourceItem);
        }
    }

    /**
     * if the target is a directory and it exists we move/copy the source UNDER the target directory instead of
     * replacing it - this is the default unix filesystem behavior.
     * see {@link MoverConfig}
     */
    private RepoRepoPath<StoringRepo> adjustTargetPathIfTargetExists(VfsItem sourceItem,
            RepoRepoPath<StoringRepo> targetRrp) {
        if (unixStyleBehavior) {
            VfsItem targetFsItem = targetRrp.getRepo().getImmutableFsItem(targetRrp.getRepoPath());
            if (targetFsItem != null && targetFsItem.isFolder()) {
                String adjustedPath = targetRrp.getRepoPath().getPath() + PATH_SEPARATOR + sourceItem.getName();
                targetRrp = new RepoRepoPath<>(targetRrp.getRepo(),
                        InternalRepoPathFactory.create(targetRrp.getRepoPath().getRepoKey(), adjustedPath, true));
            }
        }
        return targetRrp;
    }

    /**
     * move / copy recursive procedure in atomic tx
     *
     * @param source    - source of copy move
     * @param targetRrp - target path
     */
    protected void moveCopyRecursive(VfsItem source, RepoRepoPath<StoringRepo> targetRrp) {
        if (errorsOrWarningsOccurredAndFailFast()) {
            return;
        }
        if (source.isFolder()) {
            handleDir((VfsFolder) source, targetRrp);
        } else {
            // the source is a file
            handleFile(source, targetRrp);
        }
    }

    /**
     * move / copy recursive procedure in multi tx
     *
     * @param source    - source of copy move
     * @param targetRrp - target path
     */
    protected void moveCopyRecursiveMultiTx(VfsItem source, RepoRepoPath<StoringRepo> targetRrp,
            Map<String, String> folderMoverMap) {
        if (errorsOrWarningsOccurredAndFailFast()) {
            return;
        }
        if (source.isFolder()) {
            handleDirMultiTx((VfsFolder) source, targetRrp, folderMoverMap);
        } else {
            // the source is a file
            handleFileMultiTx(source, targetRrp);
        }
    }

    /**
     * This method copies the source folder to the target folder including the folder metadata, excluding children
     *
     * @param sourceFolder src
     * @param targetRrp    dest
     * @return the new folder dest created
     */
    public MutableVfsFolder shallowCopyDirectory(VfsFolder sourceFolder, RepoRepoPath<StoringRepo> targetRrp) {
        assertNotDryRun();
        StoringRepo targetRepo = targetRrp.getRepo();
        RepoPath targetRepoPath = InternalRepoPathFactory.create(targetRepo.getKey(),
                targetRrp.getRepoPath().getPath());
        MutableVfsFolder targetFolder = targetRepo.getMutableFolder(targetRepoPath);
        if (targetFolder == null) {
            log.debug("Creating target folder {}", targetRepoPath);
            targetFolder = targetRepo.createOrGetFolder(targetRepoPath);
        } else {
            log.debug("Target folder {} already exist", targetRepoPath);
        }
        status.folderMoved();
        targetFolder.fillInfo(sourceFolder.getInfo());
        // copy relevant metadata from source to target
        log.debug("Copying folder metadata to {}", targetRepoPath);
        targetFolder.setProperties((Properties) InfoFactoryHolder.get().copyProperties(sourceFolder.getProperties()));
        replicationAddon.offerLocalReplicationPropertiesChangeEvent(targetRepoPath, false);
        return targetFolder;
    }

    protected abstract void beforeOperationOnFolder(VfsItem sourceItem, RepoPath targetRepoPath);

    protected abstract void afterOperationOnFolder(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp,
            VfsFolder targetFolder);

    protected abstract void beforeOperationOnFile(VfsItem sourceItem, RepoPath targetRepoPath);

    protected abstract void operationOnFile(VfsFile sourceItem, RepoRepoPath<StoringRepo> targetRrp,
            InterceptorMoveCopyContext ctx);

    protected abstract VfsItem getSourceItem(VfsItem sourceItem);


    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    protected void handleDir(VfsFolder source, RepoRepoPath<StoringRepo> targetRrp) {
        MutableVfsFolder targetFolder = null;
        RepoPath targetRepoPath = targetRrp.getRepoPath();
        CopyMoveCheckResult result = checkCopyMove(source, targetRrp);
        switch (result) {
            case ALLOW:
                if (!dryRun) {
                    StatusEntry lastError = status.getLastError();
                    //call interceptors before move or copy operation
                    beforeOperationOnFolder(source, targetRepoPath);
                    if (status.getCancelException(lastError) != null) {
                        return;
                    }
                    targetFolder = shallowCopyDirectory(source, targetRrp);
                }
                break;
            case SKIP:
                log.debug("Skipping move/copy: {} -> {}", source.getRepoPath(), targetRrp.getRepoPath());
                return;
            case REJECT:
                if (!contains(targetRrp) ||
                        targetRrp.getRepo().getImmutableFsItem(targetRrp.getRepoPath()).isFile()) {
                    // target repo doesn't accept this path and it doesn't already contain it OR the target is a file
                    // so there is no point to continue to the children
                    status.errorWithDifferentLogLevel("Cannot create/override the path '" +
                            targetRrp.getRepoPath() + "'. Skipping this path and all its children.",
                            SC_BAD_REQUEST, null, log, Level.WARN);
                    return;
                }
                break;
        }
        List<VfsItem> children = source.getImmutableChildren();
        RepoPath originalRepoPath = targetRrp.getRepoPath();
        for (VfsItem child : children) {
            targetRrp = adjustTargetPathToChild(targetRrp, originalRepoPath, child);
            // recursive call with the child
            moveCopyRecursive(child, targetRrp);
        }
        saveSession();  // save the session before checking if the folder is empty
        //call interceptors after move or copy operation
        afterOperationOnFolder(source, targetRrp, targetFolder);
        deleteAndReplicateAfterMoveEvent(targetFolder, children.size());
    }

    /***
     * delete folder and send replication event
     *
     * @param targetFolder  - target folder
     * @param numOfChildren - folder children number
     */
    protected void deleteAndReplicateAfterMoveEvent(MutableVfsFolder targetFolder, int numOfChildren) {
        if (shouldRemoveTargetFolder(targetFolder, numOfChildren)) {
            deleteAndReplicateEvent(targetFolder); // target folder is empty remove it immediately
        }
    }

    @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
    protected void handleDirMultiTx(VfsFolder source, RepoRepoPath<StoringRepo> targetRrp,
            Map<String, String> folderMoverMap) {
        if (storageInterceptors.checkDirCopyMove(source, targetRrp.getRepoPath(), status) != ALLOW) {
            log.debug("Copy/Move to the target path {} is disallowed", targetRrp.getRepoPath());
            return;
        }
        LockableMover lockableMover = InternalContextHelper.get().beanForType(LockableMover.class);
        VfsFolderRepo vfsFolderRepo = lockableMover.moveCopyFolder(source, targetRrp, this, status);
        if (vfsFolderRepo == null || vfsFolderRepo.getVfsItem() == null) {
            return;
        }
        MutableVfsFolder targetFolder = (MutableVfsFolder) vfsFolderRepo.getVfsItem();
        targetRrp = vfsFolderRepo.getRepoRepoPath();
        if (targetFolder == null) {
            return;
        }
        RepoPath originalRepoPath = targetRrp.getRepoPath();
        // add folder repo path to folder creation map
        folderMoverMap.put(targetRrp.getRepoPath().toString(), originalRepoPath.toString());
        List<VfsItem> children = source.getImmutableChildren();
        for (VfsItem child : children) {
            if (isFolderCreatedAlready(folderMoverMap, child)) {
                targetRrp = adjustTargetPathToChild(targetRrp, originalRepoPath, child);
                // recursive call with the child
                moveCopyRecursiveMultiTx(child, targetRrp, folderMoverMap);
            }
        }
        //call interceptors after move or copy operation
        lockableMover.postFolderProcessing(source, targetFolder, targetRrp, this, properties, children.size());
    }

    /**
     * update the cached object with the child's repo path - must know if it's a folder or not for correct
     * include/exclude pattern behavior.
     */
    private RepoRepoPath<StoringRepo> adjustTargetPathToChild(RepoRepoPath<StoringRepo> currentTarget,
            RepoPath originalRepoPath, VfsItem child) {
        return new RepoRepoPath<>(currentTarget.getRepo(), InternalRepoPathFactory.create(originalRepoPath.getRepoKey(),
                originalRepoPath.getPath() + "/" + child.getName(), child.isFolder()));
    }

    /**
     * check weather folder was created already to prevent recursive folder creation
     *
     * @param folderMoverMap - hold folder created Path
     * @param child          - child to be created
     * @return - if true folder was not created already
     */
    private boolean isFolderCreatedAlready(Map<String, String> folderMoverMap, VfsItem child) {
        return folderMoverMap.get(child.getRepoPath().toString()) == null;
    }

    protected boolean validateDryRun(RepoRepoPath<StoringRepo> targetRrp) {
        if (dryRun && (!contains(targetRrp) ||
                targetRrp.getRepo().getImmutableFsItem(targetRrp.getRepoPath()).isFile())) {
            // target repo doesn't accept this path and it doesn't already contain it OR the target is a file
            // so there is no point to continue to the children
            status.errorWithDifferentLogLevel("Cannot create/override the path '" + targetRrp.getRepoPath() +
                    "'. Skipping this path and all its children.", SC_BAD_REQUEST, null, log, Level.WARN);
            return true;

        }
        return false;
    }

    /**
     * If not in a dry run, If not pruning empty folders (if true it will happen at a later stage),
     * If not copying (no source removal when copying), If not on the root item (a repo),
     * If not containing any children and folders or artifacts were moved.
     */
    protected boolean shouldRemoveSourceFolder(VfsFolder sourceFolder) {
        return !dryRun && !copy && !sourceFolder.getRepoPath().isRoot() && !sourceFolder.hasChildren()
                && !pruneEmptyFolders && (status.getMovedFoldersCount() != 0 || status.getMovedArtifactsCount() != 0);
    }

    //If not containing any children and items have been moved (children have actually been moved)
    protected boolean shouldRemoveTargetFolder(MutableVfsFolder targetFolder, int childrenSize) {
        return !dryRun && targetFolder != null && !targetFolder.getRepoPath().isRoot() && !targetFolder.hasChildren()
                && childrenSize != 0;
    }

    protected void handleFile(VfsItem source, RepoRepoPath<StoringRepo> targetRrp) {
        if (checkCopyMove(source, targetRrp) == ALLOW) {
            if (!dryRun) {
                moveFile((VfsFile) source, targetRrp, status, overrideProperties);
            } else {
                status.artifactMoved();
            }
        }
    }

    protected void handleFileMultiTx(VfsItem source, RepoRepoPath<StoringRepo> targetRrp) {
        if (checkCopyMove(source, targetRrp) == ALLOW) {
            if (!dryRun) {
                moveFileMultiTx((VfsFile) source, targetRrp);
            } else {
                status.artifactMoved();
            }
        }
    }

    protected void afterFolderCopy(VfsItem sourceItem, RepoRepoPath<StoringRepo> targetRrp, VfsFolder targetFolder,
            Properties properties) {
        RepoPath repoPath = targetRrp.getRepoPath();
        DbLocalRepo targetRepo = (DbLocalRepo) targetRrp.getRepo();
        if (!dryRun && copy && targetRepo.isPathPatternValid(repoPath, repoPath.getPath())) {
            storageInterceptors
                    .afterCopy(sourceItem, targetFolder, status, properties, new InterceptorMoveCopyContext());
        }
    }

    protected void copyFile(VfsFile sourceItem, RepoRepoPath<StoringRepo> targetRrp, Properties properties,
            InterceptorMoveCopyContext ctx) {
        MutableVfsFile targetFile = getMutableVfsTargetFile(targetRrp, targetRrp.getRepo());
        log.debug("Copying file {} to {}", sourceItem, targetFile);
        // copy file
        copyVfsFile(sourceItem, targetFile);
        // call after copy interceptors
        ctx.setOverrideProperties(overrideProperties);
        storageInterceptors.afterCopy(sourceItem, targetFile, status, properties, ctx);
    }

    protected void afterMoveFolder(VfsItem sourceItem, VfsFolder targetFolder, Properties properties) {
        if (shouldRemoveSourceFolder((VfsFolder) sourceItem)) {
            storageInterceptors
                    .afterMove(sourceItem, targetFolder, status, properties, new InterceptorMoveCopyContext());
            deleteAndReplicateEvent((VfsFolder) sourceItem);
        }
    }

    protected void moveFile(VfsFile sourceItem, RepoRepoPath<StoringRepo> targetRrp, Properties properties,
            InterceptorMoveCopyContext ctx) {
        MutableVfsFile targetFile = getMutableVfsTargetFile(targetRrp, targetRrp.getRepo());
        log.debug("Moving file from {} to {}", sourceItem, targetFile);
        moveVfsFile(sourceItem, targetFile);
        ctx.setOverrideProperties(overrideProperties);
        storageInterceptors.afterMove(sourceItem, targetFile, status, properties, ctx);
    }

    private void moveVfsFile(VfsFile sourceFile, MutableVfsFile targetFile) {
        copyVfsFile(sourceFile, targetFile);
        StoringRepo storingRepo = repositoryService.storingRepositoryByKey(sourceFile.getRepoKey());
        MutableVfsFile mutableSourceFile = null;

        if (storingRepo != null) {
            mutableSourceFile = storingRepo.getMutableFile(sourceFile.getRepoPath());
        }

        if (mutableSourceFile != null) {
            mutableSourceFile.delete(new DeleteContext(mutableSourceFile.getRepoPath()).triggeredByMove());
        } else {
            log.warn("About to delete {} but it is null", sourceFile.getRepoPath());
        }
    }

    protected CopyMoveCheckResult checkCopyMove(VfsItem source, RepoRepoPath<StoringRepo> targetRrp) { //NOSONAR
        try {
            status.setActivateLogging(false);
            return checkCopyMoveInternal(source, targetRrp);
        } finally {
            status.setActivateLogging(true);
        }
    }

    private CopyMoveCheckResult checkCopyMoveInternal(VfsItem source, RepoRepoPath<StoringRepo> targetRrp) {
        RepoPath sourceRepoPath = source.getRepoPath();

        if (!authorizationService.canRead(sourceRepoPath)) {
            status.error("User doesn't have permissions to read '" + sourceRepoPath + "'. " +
                    "Needs read permissions.", HttpStatus.SC_FORBIDDEN, log);
            return REJECT;
        }

        StoringRepo targetRepo = targetRrp.getRepo();
        RepoPath targetRepoPath = targetRrp.getRepoPath();
        String targetPath = targetRepoPath.getPath();

        // snapshot/release policy is enforced only on files since it only has a meaning on files
        if (source.isFile() && (targetRepo instanceof LocalRepo) &&
                !((LocalRepo) targetRepo).handlesReleaseSnapshot(targetPath)) {
            status.error("The repository '" + targetRepo.getKey() + "' rejected the path '" + targetPath
                    + "' due to a conflict with its snapshot/release handling policy.", SC_BAD_REQUEST, log);
            return REJECT;
        }

        if ((targetRepo instanceof LocalRepo) && !((LocalRepo) targetRepo).accepts(targetRepoPath)) {
            status.error("The repository '" + targetRepo.getKey() + "' rejected the resolution of artifact in path '" +
                    targetPath
                    + "' due to a conflict with its include/exclude patterns.", HttpStatus.SC_FORBIDDEN, log);
            return REJECT;
        }

        // permission checks
        if (!copy && !authorizationService.canDelete(sourceRepoPath)) {
            status.error("User doesn't have permissions to move '" + sourceRepoPath + "'. " +
                    "Needs delete permissions.", HttpStatus.SC_FORBIDDEN, log);
            return REJECT;
        }

        if (!targetRepoPath.isRoot() && contains(targetRrp)) {
            if (!authorizationService.canDelete(targetRepoPath)) {
                status.error("User doesn't have permissions to override '" + targetRepoPath + "'. " +
                        "Needs delete permissions.", HttpStatus.SC_UNAUTHORIZED, log);
                return REJECT;
            }

            // don't allow moving/copying folder to file
            if (source.isFolder()) {
                VfsItem targetFsItem = targetRepo.getImmutableFsItem(targetRepoPath);
                if (targetFsItem != null && targetFsItem.isFile()) {
                    status.errorWithDifferentLogLevel("Can't move folder under file '" + targetRepoPath + "'.",
                            SC_BAD_REQUEST, null, log, Level.WARN);
                    return REJECT;
                }
            }
        } else if (!authorizationService.canDeploy(targetRepoPath)) {
            status.error("User doesn't have permissions to create '" + targetRepoPath + "'. " +
                    "Needs write permissions.", HttpStatus.SC_FORBIDDEN, log);
            return REJECT;
        }

        CopyMoveCheckResult checkResult =
                storageInterceptors.checkCopyMove(source, targetRepoPath, status);
        if (checkResult != ALLOW) {
            return checkResult;
        }
        if (repositoryService.releaseBundleRepositoryByKey(source.getRepoKey()) != null ||
                targetRrp.getRepo() instanceof ReleaseBundlesRepo) {
            if (!SecurityService.USER_SYSTEM.equals(securityService.currentUsername())) {
                status.errorWithDifferentLogLevel(RELEASE_BUNDLE_REPO_COPY_MOVE_ERROR_MSG, SC_BAD_REQUEST,
                        null, log, Level.WARN);
                return REJECT;
            }
        }

        // all tests passed
        return ALLOW;
    }

    void moveFile(VfsFile sourceFile, RepoRepoPath<StoringRepo> targetRrp,
            MoveMultiStatusHolder status, boolean overrideProperties) {
        assertNotDryRun();
        StoringRepo targetRepo = targetRrp.getRepo();
        RepoPath targetRepoPath = targetRrp.getRepoPath();
        StatusEntry lastError = status.getLastError();
        // call interceptors before operation
        beforeOperationOnFile(sourceFile, targetRepoPath);
        if (status.getCancelException(lastError) != null) {
            return;
        }
        // Creating move/copy context before re-writing the target info
        InterceptorMoveCopyContext ctx = createMoveCopyContext(targetRrp);
        ctx.setOverrideProperties(overrideProperties);
        if (!(targetRepo.getDescriptor() instanceof TrashRepoDescriptor)) {
            MoveCopyHelper.overrideTargetFileIfExist(targetRrp, targetRrp.getRepo(), status, false);
        }

        // copy or move file
        operationOnFile(sourceFile, targetRrp, ctx);
        if (!(targetRepo.getDescriptor() instanceof TrashRepoDescriptor)) {
            saveSession();
            LockingHelper.removeLockEntry(sourceFile.getRepoPath());
        }
        status.artifactMoved();
    }

    InterceptorMoveCopyContext createMoveCopyContext(RepoRepoPath<StoringRepo> targetRrp) {
        MutableVfsFile targetFile = targetRrp.getRepo().getMutableFile(targetRrp.getRepoPath());
        // File might not yet exist
        FileInfo targetFileInfo = targetFile == null ? null : targetFile.getInfo();
        return new InterceptorMoveCopyContext(targetFileInfo);
    }

    protected void moveFileMultiTx(VfsFile sourceFile, RepoRepoPath<StoringRepo> targetRrp) {
        assertNotDryRun();
        LockableMover lockableMover = InternalContextHelper.get().beanForType(LockableMover.class);
        lockableMover.moveCopyFile(sourceFile, targetRrp, this, status);
        status.artifactMoved();
    }

    private MutableVfsFile getMutableVfsTargetFile(RepoRepoPath<StoringRepo> targetRrp, StoringRepo targetRepo) {
        return targetRepo.createOrGetFile(targetRrp.getRepoPath());
    }

    protected void copyVfsFile(VfsFile sourceFile, MutableVfsFile targetFile) {
        // copy the info and the properties only (stats and watches are not required)
        if (ConstantValues.copyArtifactUseNodesChecksumsEnabled.getBoolean()) {
            targetFile.setFileBinaryInfo(sourceFile.getSha1(), sourceFile.getSha2(), sourceFile.getMd5(),
                    sourceFile.length());
        } else {
            targetFile.tryUsingExistingBinary(sourceFile.getSha1(), sourceFile.getSha2(), sourceFile.getMd5(),
                    sourceFile.length());
        }
        targetFile.fillInfo(sourceFile.getInfo());
        targetFile.setProperties((Properties) InfoFactoryHolder.get().copyProperties(sourceFile.getProperties()));
    }

    protected void assertNotDryRun() {
        if (dryRun) {
            throw new IllegalStateException("Method call is not allowed in dry run");
        }
    }

    protected boolean contains(RepoRepoPath<StoringRepo> rrp) {
        return rrp.getRepo().itemExists(rrp.getRepoPath().getPath());
    }

    protected void clearEmptyDirsAndCalcMetadata(RepoRepoPath<StoringRepo> targetRrp, VfsItem sourceItem) {
        if (!dryRun) {
            clearEmptySourceDirs(sourceItem);
        }
    }

    /**
     * Marks the current folder for pruning, works only if the config set prune to true.
     */
    protected void clearEmptySourceDirs(VfsItem sourceItem) {
        RepoPath sourceRepoPath = getFolderRepoPath(sourceItem);

        // cleanup only in search results or promotion after move
        if (sourceRepoPath == null || !pruneEmptyFolders || copy) {
            return;
        }
        FolderPruningService pruningService = ContextHelper.get().beanForType(FolderPruningService.class);
        pruningService.prune(sourceRepoPath);
    }

    protected RepoPath getFolderRepoPath(VfsItem sourceItem) {
        RepoPath sourceRepoPath = sourceItem.getRepoPath();
        if (sourceItem.isFile()) {
            //If the item is a file, just calculate the parent folder
            sourceRepoPath = sourceRepoPath.getParent();
        }

        if (sourceRepoPath == null || sourceRepoPath.isRoot()) {
            // cleanup only for non root folders
            return null;
        }
        return sourceRepoPath;
    }

    protected boolean errorsOrWarningsOccurredAndFailFast() {
        return (status.hasWarnings() || status.hasErrors()) && failFast;
    }

    protected void deleteAndReplicateEvent(VfsFolder folder) {
        LocalRepo localRepo = repositoryService.localFederatedOrCachedRepositoryByKey(folder.getRepoKey());
        if (localRepo != null) {
            MutableVfsFolder mutableFolder = localRepo.getMutableFolder(folder.getRepoPath());
            if (mutableFolder != null) {
                mutableFolder.delete(new DeleteContext(mutableFolder.getRepoPath()).triggeredByMove());
                artifactoryContext.beanForType(AddonsManager.class).addonByType(ReplicationAddon.class)
                        .offerLocalReplicationDeleteEvent(folder.getRepoPath(), false);
            }
        }
    }

    protected void saveSession() {
        StorageSessionHolder.getSession().save();
    }

}
