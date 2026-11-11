package org.artifactory.storage.db.fs.model;

import org.artifactory.api.repo.exception.RepoRejectException;
import org.artifactory.fs.FileInfo;
import org.artifactory.fs.FolderInfo;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.MutableVfsItem;
import org.artifactory.storage.fs.lock.FsItemLockEntry;
import org.artifactory.storage.fs.lock.FsItemsVault;
import org.artifactory.storage.fs.repo.StoringRepo;

import jakarta.annotation.Nullable;

public class InMemoryMutableItemProvider extends DbFsItemProvider {
    private final VfsItem<?> vfsItem;
    private final StoringRepo storingRepo;
    private final Properties originalProperties;
    private final ItemInfo originalItemInfo;

    InMemoryMutableItemProvider(StoringRepo storingRepo, VfsItem<?> vfsItem, FsItemsVault fileVault,
            FsItemsVault folderVault, ItemInfo originalItemInfo, Properties originalProperties) {
        super(storingRepo, vfsItem.getRepoPath(), fileVault, folderVault);
        this.originalProperties = originalProperties;
        this.originalItemInfo = originalItemInfo;
        if (!storingRepo.getKey().equals(vfsItem.getRepoPath().getRepoKey())) {
            throw new IllegalArgumentException(
                    "Attempt to lock '" + vfsItem.getRepoPath() + "' with repository '" + storingRepo.getKey());
        }

        this.storingRepo = storingRepo;
        this.vfsItem = vfsItem;
    }

    @Nullable
    @Override
    protected MutableVfsItem<?> fetchMutableVfsItem(RepoPath repoPath, FsItemLockEntry lockEntry) {
        try {
            createAncestors();
        } catch (RepoRejectException e) {
            throw new IllegalArgumentException("Could not create file system item " + e.getMessage(), e);
        }
        MutableVfsItem<?> mutableItem;
        // item exists in db -> create a mutable version
        if (vfsItem.isFolder()) {
            mutableItem = new DbMutableFolder(storingRepo, vfsItem.getId(), (FolderInfo) vfsItem.getInfo(),
                    originalItemInfo, originalProperties, vfsItem.getProperties());
        } else {
            mutableItem = new DbMutableFile(storingRepo, vfsItem.getId(), (FileInfo) vfsItem.getInfo(),
                    originalItemInfo, originalProperties, vfsItem.getProperties());
        }

        // set the mutable item on the lock entry
        lockEntry.setWriteFsItem(mutableItem);

        return mutableItem;
    }

}
