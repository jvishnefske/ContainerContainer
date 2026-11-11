package org.artifactory.repo.service.flexible;

import org.artifactory.fs.FolderInfo;
import org.artifactory.md.Properties;
import org.artifactory.sapi.fs.VfsFolder;
import org.artifactory.sapi.fs.VfsItem;

import java.util.List;

public class FixPropertiesVfsFolder extends FixPropertiesVfsItem<FolderInfo> implements VfsFolder<FolderInfo> {

    public FixPropertiesVfsFolder(VfsItem<?> vfsFolder, Properties properties) {
        super((VfsItem<FolderInfo>) vfsFolder, properties);
    }

    @Override
    public FolderInfo getInfo() {
        return ((VfsFolder<?>) vfsItem).getInfo();
    }

    @Override
    public boolean hasChildren() {
        return ((VfsFolder<?>) vfsItem).hasChildren();
    }

    @Override
    public List<VfsItem> getImmutableChildren() {
        return ((VfsFolder<?>) vfsItem).getImmutableChildren();
    }
}
