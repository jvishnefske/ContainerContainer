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

import ch.qos.logback.classic.Level;
import lombok.NonNull;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.config.CentralConfigKey;
import org.artifactory.exception.CancelException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.interceptor.Interceptors;
import org.artifactory.repo.interceptor.StorageInterceptors;
import org.artifactory.repo.interceptor.TrashInterceptor;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.sapi.interceptor.CopyMoveCheckResult;
import org.artifactory.sapi.interceptor.StorageInterceptor;
import org.artifactory.sapi.interceptor.context.BulkCopyMoveContext;
import org.artifactory.sapi.interceptor.context.DeleteContext;
import org.artifactory.sapi.interceptor.context.InterceptorCreateContext;
import org.artifactory.sapi.interceptor.context.InterceptorMoveCopyContext;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.db.DbService;
import org.artifactory.util.RepoPathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.artifactory.sapi.interceptor.CopyMoveCheckResult.ALLOW;
import static software.amazon.awssdk.http.HttpStatusCode.BAD_REQUEST;

/**
 * @author yoav
 */
@Service
@Reloadable(beanClass = StorageInterceptors.class, initAfter = DbService.class,
        listenOn = CentralConfigKey.none)
public class StorageInterceptorsImpl extends Interceptors<StorageInterceptor> implements StorageInterceptors {
    private static final Logger log = LoggerFactory.getLogger(StorageInterceptorsImpl.class);

    @Override
    public void beforeCreate(VfsItem fsItem, MutableStatusHolder statusHolder) {
        try {
            if (!isTrash(fsItem.getRepoPath())) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.beforeCreate(fsItem, statusHolder);
                }
            }
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel("Before create rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        }
    }

    @Override
    public void afterCreate(VfsItem fsItem, MutableStatusHolder statusHolder, InterceptorCreateContext ctx) {
        try {
            if (!isTrash(fsItem.getRepoPath())) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.afterCreate(fsItem, statusHolder, ctx);
                }
            }
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel("After create rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        }
    }

    @Override
    public boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder) {
        return canDelete(repoPath, statusHolder, new DeleteContext(repoPath));
    }

    @Override
    public boolean canDelete(RepoPath repoPath, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (!isTrash(repoPath)) {
            for (StorageInterceptor storageInterceptor : this) {
                if (!storageInterceptor.canDelete(repoPath, statusHolder, ctx)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void assertDeletePathAllowed(RepoPath repoPath, MutableStatusHolder statusHolder) {
        if (!isTrash(repoPath)) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.assertDeletePathAllowed(repoPath, statusHolder);
            }
        }
    }

    @Override
    public void assertOverwriteAllowed(RepoPath repoPath, MutableStatusHolder statusHolder) {
        if (!isTrash(repoPath)) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.assertOverwriteAllowed(repoPath, statusHolder);
            }
        }
    }

    @Override
    public void assertDeleteRepoAllowed(String repoKey, MutableStatusHolder statusHolder) {
        if (!RepoPathUtils.isTrash((repoKey))) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.assertDeleteRepoAllowed(repoKey, statusHolder);
            }
        }
    }

    @Override
    public void afterRepositoryContentDelete(String repoKey, MutableStatusHolder statusHolder) {
        if (!RepoPathUtils.isTrash(repoKey)) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.afterRepositoryContentDelete(repoKey, statusHolder);
            }
        }
    }

    @Override
    public void beforeMoveFiles(@NonNull BulkCopyMoveContext context) {
        for (StorageInterceptor storageInterceptor : this) {
            storageInterceptor.beforeMoveFiles(context);
        }
    }

    @Override
    public void beforeDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        try {
            if (!isTrash(fsItem.getRepoPath())) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.beforeDelete(fsItem, statusHolder, ctx);
                }
            }
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel("Delete rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        } catch (IllegalArgumentException e) {
            statusHolder.warn("Delete rejected: " + e.getMessage(), BAD_REQUEST, e, log);
        } catch (ForbiddenException e) {
            // Unlike other exception types above, this must still block the delete:
            // warn() alone would only record a WARNING status entry, callers checking
            // statusHolder.isError() would not treat as rejected.
            statusHolder.warn("Delete not allowed: " + e.getMessage(), SC_FORBIDDEN, e, log);
            throw e;
        } catch (Exception e) {
            log.error("Failed in beforeDelete due to exception: ", e);
            throw e;
        }
    }

    @Override
    public void afterDelete(VfsItem fsItem, MutableStatusHolder statusHolder, DeleteContext ctx) {
        if (!isTrash(fsItem.getRepoPath())) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.afterDelete(fsItem, statusHolder, ctx);
            }
        } else {
            for (StorageInterceptor storageInterceptor : this) {
                if (storageInterceptor instanceof TrashInterceptor) {
                    storageInterceptor.afterDelete(fsItem, statusHolder, ctx);
                }
            }
        }
    }

    @Override
    public void beforePropertyCreate(VfsItem fsItem, MutableStatusHolder statusHolder, String name, String... values) {
        try {
            if (!isTrash(fsItem.getRepoPath())) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.beforePropertyCreate(fsItem, statusHolder, name, values);
                }
            }
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel("Property create rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        }
    }

    @Override
    public void afterPropertyCreate(VfsItem fsItem, MutableStatusHolder statusHolder, String name,
            String... values) {
        if (!isTrash(fsItem.getRepoPath())) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.afterPropertyCreate(fsItem, statusHolder, name, values);
            }
        }
    }

    @Override
    public void beforePropertyDelete(VfsItem fsItem, MutableStatusHolder statusHolder, String name) {
        try {
            if (!isTrash(fsItem.getRepoPath())) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.beforePropertyDelete(fsItem, statusHolder, name);
                }
            }
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel("Property delete rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        }
    }

    @Override
    public void afterPropertyDelete(VfsItem fsItem, MutableStatusHolder statusHolder, String name) {
        if (!isTrash(fsItem.getRepoPath())) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.afterPropertyDelete(fsItem, statusHolder, name);
            }
        }
    }

    @Override
    public CopyMoveCheckResult checkCopyMove(VfsItem sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder) {
        if (!isTrash(targetRepoPath)) {
            for (StorageInterceptor storageInterceptor : this) {
                CopyMoveCheckResult result =
                        storageInterceptor.checkCopyMove(sourceItem, targetRepoPath, statusHolder);
                if (result != ALLOW) {
                    return result;
                }
            }
        }
        return ALLOW;
    }

    @Override
    public CopyMoveCheckResult checkDirCopyMove(VfsItem sourceItem, RepoPath targetRepoPath,
            MutableStatusHolder statusHolder) {
        if (!isTrash(targetRepoPath)) {
            for (StorageInterceptor storageInterceptor : this) {
                CopyMoveCheckResult result =
                        storageInterceptor.checkDirCopyMove(sourceItem, targetRepoPath, statusHolder);
                if (result != ALLOW) {
                    return result;
                }
            }
        }
        return ALLOW;
    }

    @Override
    public void beforeMove(VfsItem sourceItem, RepoPath targetRepoPath, MutableStatusHolder statusHolder,
            Properties properties) {
        try {
            if (!isTrash(sourceItem.getRepoPath())) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.beforeMove(sourceItem, targetRepoPath, statusHolder, properties);
                }
            }
        } catch (CancelException e) {
            statusHolder.errorWithDifferentLogLevel("Move rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        }
    }

    @Override
    public void afterMove(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        for (StorageInterceptor storageInterceptor : this) {
            storageInterceptor.afterMove(sourceItem, targetItem, statusHolder, properties, ctx);
        }
    }

    @Override
    public void beforeCopyFiles(BulkCopyMoveContext context) {
        try {
            BulkCopyMoveContext filteredContext = new BulkCopyMoveContext(context.getStatusHolder());
            for (int i = 0; i < context.getTargetRepoPaths().size(); i++) {
                if (!isTrash(context.getTargetRepoPaths().get(i))) {
                    filteredContext.getSourceItems().add(context.getSourceItems().get(i));
                    filteredContext.getTargetRepoPaths().add(context.getTargetRepoPaths().get(i));
                }
            }
            if (!filteredContext.getSourceItems().isEmpty()) {
                for (StorageInterceptor storageInterceptor : this) {
                    storageInterceptor.beforeCopyFiles(filteredContext);
                }
            }
        } catch (CancelException e) {
            context.getStatusHolder().errorWithDifferentLogLevel("Copy rejected: " + e.getMessage(),
                    e.getErrorCode(), e, log, Level.WARN);
        }
    }

    @Override
    public void afterCopy(VfsItem sourceItem, VfsItem targetItem, MutableStatusHolder statusHolder,
            Properties properties, InterceptorMoveCopyContext ctx) {
        if (!isTrash(targetItem.getRepoPath())) {
            for (StorageInterceptor storageInterceptor : this) {
                storageInterceptor.afterCopy(sourceItem, targetItem, statusHolder, properties, ctx);
            }
        }
    }

    private boolean isTrash(RepoPath repoPath) {
        return RepoPathUtils.isTrash(repoPath);
    }
}