package org.artifactory.storage.db.fs.model;

import lombok.NonNull;
import org.artifactory.api.repo.exception.FileExpectedException;
import org.artifactory.api.repo.exception.FolderExpectedException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.fs.VfsItemNotFoundException;
import org.artifactory.storage.fs.lock.FsItemLockEntry;
import org.artifactory.storage.fs.lock.LockingHelper;
import org.artifactory.storage.fs.repo.StoringRepo;
import org.artifactory.storage.fs.session.StorageSessionHolder;
import org.artifactory.storage.fs.tree.VfsImmutableProvider;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryImmutableItemProvider implements VfsImmutableProvider {

    private final StoringRepo storingRepo;
    private final ItemInfo itemInfo;

    public InMemoryImmutableItemProvider(StoringRepo storingRepo, ItemInfo itemInfo) {
        this.storingRepo = storingRepo;
        this.itemInfo = itemInfo;
        if (!storingRepo.getKey().equals(itemInfo.getRepoKey())) {
            throw new IllegalArgumentException(
                    "Attempt to lock '" + itemInfo.getRepoPath() + "' with repository '" + storingRepo.getKey());
        }
    }

    @Override
    @Nullable
    public VfsItem<ItemInfo> getImmutableFsItem() {
        return getImmutableFsItemInternal();
    }

    @Override
    @Nullable
    public VfsFolder<FolderInfo> getImmutableFolder() {
        VfsItem<ItemInfo> immutableFsFolder = getImmutableFsItemInternal();
        if (immutableFsFolder != null && immutableFsFolder.isFile()) {
            throw new FolderExpectedException(getRepoPath());
        }
        return (VfsFolder<FolderInfo>) (VfsItem<?>) immutableFsFolder;
    }

    @Override
    @NonNull
    public Map<RepoPath, Optional<VfsItem<ItemInfo>>> getImmutableFsItems(@NonNull List<RepoPath> repoPaths) {
        throw new UnsupportedOperationException("Unsupported method getImmutableFsItems");
    }

    @Override
    @Nullable
    public VfsFile<FileInfo> getImmutableFile() {
        VfsItem<ItemInfo> immutableFsFile = getImmutableFsItemInternal();
        if (immutableFsFile != null && immutableFsFile.isFolder()) {
            throw new FileExpectedException(getRepoPath());
        }
        return (VfsFile<FileInfo>) (VfsItem<?>) immutableFsFile;
    }

    @Nullable
    private VfsItem<ItemInfo> fetchVfsItem() {
        try {
            if (itemInfo.isFolder()) {
                VfsItem<?> dbFsFolder = new DbFsFolder(storingRepo, itemInfo.getId(), (FolderInfo) itemInfo);
                return (VfsItem<ItemInfo>) dbFsFolder;
            } else {
                VfsItem<?> dbFsFile = new DbFsFile(storingRepo, itemInfo.getId(), (FileInfo) itemInfo);
                return (VfsItem<ItemInfo>) dbFsFile;
            }
        } catch (VfsItemNotFoundException e) {
            return null;
        } catch (StorageException e) {
            throw new StorageException(e);
        }
    }

    public RepoPath getRepoPath() {
        return itemInfo.getRepoPath();
    }

    /**
     * Returns an read locked immutable VFS item or null if the item with this repo path doesn't exist.
     * If the item already write locked by the current thread, no read lock is acquired.
     *
     * @return Read locked VFS item or null if not found
     */
    private <T extends VfsItem<ItemInfo>> T getImmutableFsItemInternal() {
        if (StorageSessionHolder.getSession() != null) {
            // try first the mutable item - it might contain changes done by current thread in a previous method
            FsItemLockEntry sessionLockEntry = LockingHelper.getLockEntry(itemInfo.getRepoPath());
            if (sessionLockEntry != null) {
                if (sessionLockEntry.getMutableFsItem() != null) {
                    return (T) sessionLockEntry.getMutableFsItem();
                }
            }
        }

        // get the requested fs item from the storage
        return (T) fetchVfsItem();
    }
}
