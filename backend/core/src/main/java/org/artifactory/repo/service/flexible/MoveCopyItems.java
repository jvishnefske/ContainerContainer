package org.artifactory.repo.service.flexible;

import org.artifactory.fs.ItemInfo;
import org.artifactory.fs.MutableItemInfo;
import org.artifactory.sapi.fs.VfsItem;
import org.artifactory.storage.fs.MutableVfsItem;

public class MoveCopyItems {
    private final VfsItem<ItemInfo> sourceItem;
    private VfsItem<ItemInfo> originalTargetItem;
    private MutableVfsItem<MutableItemInfo> targetItem;

    public MoveCopyItems(VfsItem<ItemInfo> sourceItem, VfsItem<ItemInfo> originalTargetItem,
            MutableVfsItem<MutableItemInfo> targetItem) {
        this.originalTargetItem = originalTargetItem;
        this.sourceItem = sourceItem;
        this.targetItem = targetItem;
    }

    public void setOriginalTargetItem(VfsItem<ItemInfo> originalTargetItem) {
        this.originalTargetItem = originalTargetItem;
    }

    public void setTargetItem(MutableVfsItem<MutableItemInfo> targetItem) {
        this.targetItem = targetItem;
    }

    public VfsItem<ItemInfo> getSourceItem() {
        return sourceItem;
    }

    public VfsItem<ItemInfo> getOriginalTargetItem() {
        return originalTargetItem;
    }

    public MutableVfsItem<MutableItemInfo> getTargetItem() {
        return targetItem;
    }

}
