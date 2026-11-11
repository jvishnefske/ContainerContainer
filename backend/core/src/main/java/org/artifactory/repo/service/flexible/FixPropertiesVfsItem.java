package org.artifactory.repo.service.flexible;

import jakarta.annotation.Nonnull;
import lombok.Getter;
import org.artifactory.fs.ItemInfo;
import org.artifactory.md.Properties;
import org.artifactory.repo.RepoPath;
import org.artifactory.sapi.fs.VfsItem;

public abstract class FixPropertiesVfsItem<T extends ItemInfo> implements VfsItem<T> {
    @Getter
    protected final VfsItem<?> vfsItem;
    protected final Properties properties;

    FixPropertiesVfsItem(VfsItem<T> vfsItem, Properties properties) {
        this.vfsItem = vfsItem;
        this.properties = properties;
    }

    @Override
    public long getId() {
        return vfsItem.getId();
    }

    @Override
    public String getPath() {
        return vfsItem.getPath();
    }

    @Override
    public String getName() {
        return vfsItem.getName();
    }

    @Override
    public String getRepoKey() {
        return vfsItem.getRepoKey();
    }

    @Override
    public RepoPath getRepoPath() {
        return vfsItem.getRepoPath();
    }

    @Override
    public boolean isFile() {
        return vfsItem.isFile();
    }

    @Override
    public boolean isFolder() {
        return vfsItem.isFolder();
    }

    @Override
    public long getCreated() {
        return vfsItem.getCreated();
    }

    @Override
    @Nonnull
    public Properties getProperties() {
        return properties;
    }
}
