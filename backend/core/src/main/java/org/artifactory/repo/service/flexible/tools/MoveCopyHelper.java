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

import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import org.artifactory.api.common.MoveMultiStatusHolder;
import org.artifactory.api.repo.exception.FatalCopyMoveException;
import org.artifactory.aql.api.domain.sensitive.AqlApiItem;
import org.artifactory.aql.api.internal.AqlBase;
import org.artifactory.aql.model.AqlItemTypeEnum;
import org.artifactory.aql.model.AqlSortTypeEnum;
import org.artifactory.common.status.StatusEntry;
import org.artifactory.exception.CancelException;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoRepoPath;
import org.artifactory.repo.StoringRepo;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.service.flexible.MoveCopyItemInfo;
import org.artifactory.repo.service.flexible.context.MoveCopyContext;
import org.artifactory.repo.service.flexible.listeners.MoveCopyListeners;
import org.artifactory.repo.service.flexible.validators.MoveCopyValidator;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.storage.fs.MutableVfsFile;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.SessionLockEntry;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.util.RepoPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createWithEmptyResults;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.created;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.createdBy;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.depth;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.itemId;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.md5Actual;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.md5Orginal;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.modified;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.modifiedBy;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.name;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.path;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.property;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repo;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.repoPathChecksum;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.sha1Actual;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.sha1Orginal;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.sha2;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.size;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.type;
import static org.artifactory.aql.api.domain.sensitive.AqlApiItem.updated;
import static org.artifactory.aql.api.internal.AqlBase.and;
import static org.artifactory.aql.api.internal.AqlBase.or;
import static org.artifactory.common.ConstantValues.flatCopyMoveCheckCopyMoveInterceptors;
import static org.artifactory.common.ConstantValues.flatCopyMoveFullRevertOnAbort;
import static org.artifactory.repo.service.flexible.tools.MoveCopyDeleteHelper.deleteFilesIfMove;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.EVIDENCE_DIR;
import static org.artifactory.util.evidence.EvidenceConfigurationUtil.isEvidenceEnabled;

/**
 * The class is simple state less helper that help to simplify the move/copy code in the move/copy service
 *
 * @author gidis
 */
public abstract class MoveCopyHelper {

    private static final Logger log = LoggerFactory.getLogger(MoveCopyHelper.class);

    /**
     * returns true if need to stop the move/copy process
     */
    public static boolean isErrorAndFailFast(boolean failFast, MoveMultiStatusHolder finalStatus) {
        return (finalStatus.hasWarnings() || finalStatus.hasErrors()) && failFast;
    }

    /**
     * returns true if need to stop the move/copy process
     */
    public static boolean isError(MoveMultiStatusHolder finalStatus) {
        return (finalStatus.hasWarnings() || finalStatus.hasErrors());
    }

    public static void notifyMoveCopyFiles(
            @NonNull List<MoveCopyItemInfo> elements,
            @NonNull MoveCopyContext context,
            @NonNull MoveMultiStatusHolder status,
            @NonNull List<MoveCopyListeners> moveCopyListeners,
            @NonNull NotifyType notifyType) {

        for (MoveCopyListeners moveCopyListener : moveCopyListeners) {
            if (notifyType == NotifyType.BEFORE) {
                moveCopyListener.notifyBeforeMoveCopyFiles(elements, context, status);
            } else if (notifyType == NotifyType.AFTER) {
                moveCopyListener.notifyAfterMoveCopyFiles(elements, context, status);
            } else {
                throw new IllegalArgumentException(
                        "Unknown notification type " + notifyType + ", supported types are: " +
                                Arrays.toString(NotifyType.values()));
            }

            if (isErrorAndFailFast(context.isFailFast(), status)) {
                break;
            }
        }
    }

    /**
     * The method validates single move/copy of file.
     * It returns:
     * True without errors/warning in status holder to copy item.
     * True with errors/warning to skip move/copy of an item  and failFast=true to  stop all processes if failFast=true
     * False without errors/warning to skip move/copy of an item if failFast=false and stop all processes if
     * failFast=true
     * False without errors to continue the move/copy
     */
    public static boolean validate(MoveCopyItemInfo element, MoveCopyContext context, MoveMultiStatusHolder status,
            List<MoveCopyValidator> moveCopyValidators) {
        for (MoveCopyValidator moveCopyValidator : moveCopyValidators) {
            if (moveCopyValidator.isInterested(element, context)) {
                boolean valid = moveCopyValidator.validate(element, status, context);
                if (!valid) {
                    return false;
                }
                if (isErrorAndFailFast(context.isFailFast(), status)) {
                    return true;
                }
            }
        }
        return true;
    }

    @NonNull
    public static MoveMultiStatusHolder validateDeleteAndSaveSession(
            @NonNull List<MoveCopyItemInfo> itemsInfos,
            @NonNull MoveCopyContext context,
            @NonNull List<MoveCopyListeners> moveCopyListeners,
            @NonNull List<MoveCopyValidator> validators,
            @NonNull StorageInterceptors storageInterceptors) {

        MoveMultiStatusHolder status = new MoveMultiStatusHolder();
        List<MoveCopyItemInfo> validItems = new ArrayList<>();
        List<MoveCopyItemInfo> invalidItems = new ArrayList<>();
        // validate all items
        for (MoveCopyItemInfo element : itemsInfos) {
            StatusEntry lastError = status.getLastError();
            boolean validItem;
            // Run validation
            try {
                validItem = validate(element, context, status, validators);
            } catch (FatalCopyMoveException e) {
                revertItemsAndSave(itemsInfos); // revert all
                return status;
            }
            // Update valid invalid lists
            if (validItem) {
                validItems.add(element);
            } else {
                invalidItems.add(element);
            }
            // In case of fail fast or cancel exception stop the process
            if (shouldStopProcess(status, context, lastError)) {
                revertOnAbort(itemsInfos, invalidItems);
                return status;
            }
        }
        if (flatCopyMoveCheckCopyMoveInterceptors.getBoolean()) {
            checkCopyMoveInterceptors(validItems, invalidItems, status, storageInterceptors);
            if (shouldStopProcess(status, context, status.getLastError())) {
                revertOnAbort(itemsInfos, invalidItems);
                return status;
            }
        }
        // Notify before move/copy
        notifyMoveCopyFiles(validItems, context, status, moveCopyListeners, NotifyType.BEFORE);
        // In case of fail fast or cancel exception stop the process revert the changes in the mutable items
        if (shouldStopProcess(status, context, null)) {
            revertOnAbort(itemsInfos, invalidItems);
            return status;
        }
        // In case of move delete only valid files and collect the valid directories
        deleteFilesIfMove(validItems, context, status);
        if (status.hasErrors()) { // if errors, revert all
            revertItems(invalidItems);
            revertItems(validItems);
        }
        // Save session for external visibility
        StorageSessionHolder.getSession().save(); //must save session after copy and before firing interceptors
        // Notify on file successful move. the move of folders in notified at a different time.
        // see org.artifactory.repo.service.flexible.FlatMoveCopyServiceImpl.executeDeleteRootDir
        List<MoveCopyItemInfo> files =
                validItems.stream().filter(item -> item.getSourceItem().isFile()).collect(Collectors.toList());
        notifyMoveCopyFiles(files, context, status, moveCopyListeners, NotifyType.AFTER);
        return status;
    }

    static void checkCopyMoveInterceptors(List<MoveCopyItemInfo> validItems,
            List<MoveCopyItemInfo> invalidItems, MoveMultiStatusHolder status,
            StorageInterceptors storageInterceptors) {
        List<MoveCopyItemInfo> itemsToRemove = validItems.stream()
                .filter(validItem -> isNotAllow(validItem, invalidItems, status, storageInterceptors))
                .toList();
        validItems.removeAll(itemsToRemove);
    }

    private static boolean isNotAllow(MoveCopyItemInfo validItem, List<MoveCopyItemInfo> invalidItems,
            MoveMultiStatusHolder status, StorageInterceptors storageInterceptors) {
        CopyMoveCheckResult result = storageInterceptors.checkCopyMove(
                validItem.getSourceItem(), validItem.getTargetRepoPath(), status);
        if (result != ALLOW) {
            invalidItems.add(validItem);
            return true;
        }
        return false;
    }

    private static void revertItems(@NonNull List<MoveCopyItemInfo> items) {
        log.warn("Reverting {} MoveCopyItems", items.size());
        items.forEach(invalidItem -> invalidItem.getMutableTargetItem().revertChanges());
    }

    private static void revertOnAbort(@NonNull List<MoveCopyItemInfo> allItems,
            @NonNull List<MoveCopyItemInfo> invalidItems) {
        if (flatCopyMoveFullRevertOnAbort.getBoolean()) {
            revertItemsAndSave(allItems);
        } else {
            revertItemsAndSave(invalidItems);
        }
    }

    private static void revertItemsAndSave(@NonNull List<MoveCopyItemInfo> items) {
        revertItems(items);
        StorageSessionHolder.getSession().save();
    }

    public static boolean shouldStopProcess(MoveMultiStatusHolder status, MoveCopyContext context,
            StatusEntry lastError) {
        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        CancelException cancelException = status.getCancelException(lastError);
        return cancelException != null || ((status.hasWarnings() || status.hasErrors()) && context.isFailFast());
    }

    /**
     * The method create AQL query that will return all sub folders and files of single root item sorted by dept
     */
    public static AqlApiItem createSourceQuery(MoveCopyContext context, AqlSortTypeEnum order) {
        return createQuery(context.getSourceRepoPath(), order);
    }

    /**
     * Create AQL query that returns the the list of existing target items.
     * We need the target items to avoid conflicts, example try to copy folder into file
     *
     * @param order result order
     * @return AQL query
     */
    private static AqlApiItem createQuery(RepoPath repoPath, AqlSortTypeEnum order) {
        RepoPath parent = repoPath.getParent();
        AqlBase.AndClause<AqlApiItem> and = and();
        AqlApiItem query = createWithEmptyResults().filter(and);
        and.append(AqlApiItem.repo().equal(repoPath.getRepoKey()));
        if (parent == null) {
            // repoPath is Root
            and.append(AqlApiItem.path().matches("*"));
        } else if (parent.isRoot()) {
            // repoPath parent is Root
            AqlBase.OrClause<AqlApiItem> or = or();
            AqlBase.AndClause<AqlApiItem> and2 = and();
            and2.append(AqlApiItem.name().equal(repoPath.getName()));
            and2.append(AqlApiItem.path().equal("."));
            or.append(and2);
            or.append(AqlApiItem.path().equal(repoPath.getName()));
            or.append(AqlApiItem.path().matches(repoPath.getName() + "/*"));
            and.append(or);
        } else {
            // All other repoPats which are not root and parent is not root
            AqlBase.OrClause<AqlApiItem> or = or();
            AqlBase.AndClause<AqlApiItem> and2 = and();
            and2.append(AqlApiItem.name().equal(repoPath.getName()));
            and2.append(AqlApiItem.path().equal(parent.getPath()));
            or.append(and2);
            or.append(AqlApiItem.path().equal(repoPath.getPath()));
            or.append(AqlApiItem.path().matches(repoPath.getPath() + "/*"));
            and.append(or);
        }
        and.append(AqlApiItem.type().equal(AqlItemTypeEnum.any.signature));
        query.include(repoPathChecksum(), sha2(), md5Orginal(), md5Actual(), sha1Orginal(), sha1Actual(), modified(),
                modifiedBy(), createdBy(), created(), updated(), size(), type(), itemId(), repo(), path(), name(),
                type(), property().key(), property().value()).addSortElement(depth()).addSortElement(itemId());
        if (isEvidenceEnabled()) {
            getExcludePathClauseForEvidenceTrash(repoPath)
                    .ifPresent(and::append);
        }

        if (AqlSortTypeEnum.asc == order) {
            query.asc();
        } else {
            query.desc();
        }
        return query;
    }

    public static void overrideTargetFileIfExist(RepoRepoPath<StoringRepo> targetRrp, StoringRepo targetRepo,
            MoveMultiStatusHolder status, boolean basicStatusHolderErrorLog) {
        MutableVfsItem<?> targetItem = targetRepo.getMutableFsItem(targetRrp.getRepoPath());
        if (targetItem != null) {
            // target repository already contains file or folder with the same name, delete it
            log.debug("File {} already exists in target repository. Overriding.", targetRrp.getRepoPath());
            targetItem.delete(new DeleteContext(targetItem.getRepoPath()).triggeredByMove());
            reloadMutable(targetItem.getRepoPath(), targetRrp);
        } else if (basicStatusHolderErrorLog) {
            status.error(String.format("Failed to create mutable item for path: %s", targetRrp.getRepoPath()), log);
        }
    }

    /**
     * When doing override of a file, MutableVfsFile needs to be 'reset' since we are saving the delete of the target
     * file during a transaction, in order to finally save the new item at the final destination safely later on.
     */
    private static void reloadMutable(RepoPath repoPath, RepoRepoPath<StoringRepo> targetRrp) {
        SessionLockEntry lockEntry = StorageSessionHolder.getSession().getLockEntry(repoPath);
        lockEntry.save();
        lockEntry.setWriteFsItem(null);
        MutableVfsFile newMutable = targetRrp.getRepo().createOrGetFile(repoPath);
        lockEntry.setWriteFsItem(newMutable);
    }

    public enum NotifyType {
        BEFORE,
        AFTER
    }

    @VisibleForTesting
    @NonNull
    static Optional<AqlBase.AndClause<AqlApiItem>> getExcludePathClauseForEvidenceTrash(RepoPath repoPath) {
        if (!RepoPathUtils.isTrash(repoPath)) {
            return Optional.empty();
        }
        String trashPath = repoPath.getPath();
        if (trashPath.endsWith("/")) {
            trashPath = trashPath.substring(0, trashPath.length() - 1);
        }
        if (trashPath.contains("/")) {
            return Optional.empty();
        }
        // restore from a repo root of the trash bin -> need to exclude the .evidence folder (the evidence
        // interceptor will handle it)
        // trashPath is exactly the repo root key (from where the trash originally came)
        String origRepoKey = trashPath;
        String excludePath = origRepoKey + "/" + EVIDENCE_DIR;
        AqlBase.AndClause<AqlApiItem> and = and(
                path().notEquals(excludePath), // exactly this path and any name
                path().notMatches(excludePath + "/*") // any path which starts with excludePath and any name
        );
        // since the nodes table keeps separately the path and the name, then in order to also exclude exactly the
        // excludePath, we need to split it to path and name
        and.append(
                or(
                        path().notEquals(origRepoKey),
                        name().notEquals(EVIDENCE_DIR)
                ));
        return Optional.of(and);
    }
}