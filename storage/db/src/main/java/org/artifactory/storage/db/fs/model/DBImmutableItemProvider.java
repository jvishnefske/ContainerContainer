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

package org.artifactory.storage.db.fs.model;

import lombok.NonNull;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.VfsItemNotFoundException;
import org.artifactory.storage.fs.lock.FsItemLockEntry;
import org.artifactory.storage.fs.lock.LockingHelper;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.service.FileService;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.storage.fs.tree.VfsImmutableProvider;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author gidis
 */
public class DBImmutableItemProvider implements VfsImmutableProvider {

    private final StoringRepo storingRepo;
    private final RepoPath repoPath;

    public DBImmutableItemProvider(StoringRepo storingRepo, RepoPath repoPath) {
        this.storingRepo = storingRepo;
        this.repoPath = repoPath;
        if (!storingRepo.getKey().equals(repoPath.getRepoKey())) {
            throw new IllegalArgumentException(
                    "Attempt to lock '" + repoPath + "' with repository '" + storingRepo.getKey());
        }
    }

    // this is used when we use the provider for multiple repoPaths
    public DBImmutableItemProvider(StoringRepo storingRepo) {
        this.storingRepo = storingRepo;
        this.repoPath = null;
    }

    @Override
    @Nullable
    public VfsItem getImmutableFsItem() {
        return getImmutableFsItemInternal();
    }

    @Override
    @Nullable
    public VfsFolder getImmutableFolder() {
        VfsItem immutableFsFolder = getImmutableFsItemInternal();
        if (immutableFsFolder != null && immutableFsFolder.isFile()) {
            throw new FolderExpectedException(getRepoPath());
        }
        return (VfsFolder) immutableFsFolder;
    }

    @Override
    @NonNull
    public Map<RepoPath, Optional<VfsItem<ItemInfo>>> getImmutableFsItems(@NonNull List<RepoPath> repoPaths) {
        Map<RepoPath, Optional<VfsItem<ItemInfo>>> map = new HashMap<>();
        List<RepoPath> missingRepoPaths = new ArrayList<>();
        if (StorageSessionHolder.getSession() != null) {
            // try first the mutable item - it might contain changes done by current thread in a previous method
            repoPaths.forEach(path -> {
                FsItemLockEntry sessionLockEntry = LockingHelper.getLockEntry(path);
                if (sessionLockEntry != null && sessionLockEntry.getMutableFsItem() != null) {
                    map.put(path, Optional.of(sessionLockEntry.getMutableFsItem()));
                } else {
                    missingRepoPaths.add(path);
                }
            });
        } else {
            missingRepoPaths.addAll(repoPaths);
        }

        Map<RepoPath, Optional<VfsItem<ItemInfo>>> fetchedItems = fetchVfsItems(missingRepoPaths);

        map.putAll(fetchedItems);
        return map;
    }

    @Override
    @Nullable
    public VfsFile getImmutableFile() {
        VfsItem immutableFsFile = getImmutableFsItemInternal();
        if (immutableFsFile != null && immutableFsFile.isFolder()) {
            throw new FileExpectedException(getRepoPath());
        }
        return (VfsFile) immutableFsFile;
    }

    @Nullable
    private VfsItem fetchVfsItem(RepoPath repoPath) {
        FileService fileService = ContextHelper.get().beanForType(FileService.class);
        VfsItem item;
        try {
            item = fileService.loadVfsItem(storingRepo, repoPath);
        } catch (VfsItemNotFoundException e) {
            item = null;
        } catch (StorageException e) {
            throw new StorageException(e);
        }
        return item;
    }

    @NonNull
    private Map<RepoPath, Optional<VfsItem<ItemInfo>>> fetchVfsItems(@NonNull List<RepoPath> repoPaths) {
        FileService fileService = ContextHelper.get().beanForType(FileService.class);
        return fileService.loadVfsItems(storingRepo, repoPaths);
    }


    public RepoPath getRepoPath() {
        return repoPath;
    }

    /**
     * Returns an read locked immutable VFS item or null if the item with this repo path doesn't exist.
     * If the item already write locked by the current thread, no read lock is acquired.
     *
     * @return Read locked VFS item or null if not found
     */
    private <T extends VfsItem> T getImmutableFsItemInternal() {
        if (StorageSessionHolder.getSession() != null) {
            // try first the mutable item - it might contain changes done by current thread in a previous method
            FsItemLockEntry sessionLockEntry = LockingHelper.getLockEntry(repoPath);
            if (sessionLockEntry != null) {
                MutableVfsItem<MutableItemInfo> mutableFsItem = sessionLockEntry.getMutableFsItem();
                if (mutableFsItem != null) {
                    return (T) mutableFsItem;
                }
            }
        }

        // get the requested fs item from the storage
        return (T) fetchVfsItem(repoPath);
    }
}
