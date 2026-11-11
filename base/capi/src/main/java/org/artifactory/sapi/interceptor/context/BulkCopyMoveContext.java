package org.artifactory.sapi.interceptor.context;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.common.status.MutableStatusHolder;
import org.artifactory.fs.ItemInfo;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Hezi Cohen
 * Used for copying/moving multiple artifacts in batches
 * NOTE 1: ORDER of member lists MATTERS!
 * NOTE 2: Lists' elements CAN BE NULL!
 */
@Data
@NoArgsConstructor
public class BulkCopyMoveContext {

    private List<VfsItem<? extends ItemInfo>> sourceItems = new ArrayList<>();
    private List<RepoPath> targetRepoPaths = new ArrayList<>();
    private MutableStatusHolder statusHolder;

    public BulkCopyMoveContext(List<VfsItem<? extends ItemInfo>> sourceItems,
            List<RepoPath> targetRepoPaths,
            MutableStatusHolder statusHolder) {
        this.sourceItems.addAll(sourceItems);
        this.targetRepoPaths.addAll(targetRepoPaths);
        this.statusHolder = statusHolder;
    }

    public BulkCopyMoveContext(VfsItem<? extends ItemInfo> sourceItem,
            RepoPath targetRepoPath,
            MutableStatusHolder statusHolder) {
        this.sourceItems.add(sourceItem);
        this.targetRepoPaths.add(targetRepoPath);
        this.statusHolder = statusHolder;
    }

    public BulkCopyMoveContext(List<RepoPath> targetRepoPath, MutableStatusHolder statusHolder) {
        this.sourceItems.addAll(Collections.nCopies(targetRepoPath.size(), null));
        this.targetRepoPaths.addAll(targetRepoPath);
        this.statusHolder = statusHolder;
    }

    public BulkCopyMoveContext(MutableStatusHolder statusHolder) {
        this.statusHolder = statusHolder;
    }
}