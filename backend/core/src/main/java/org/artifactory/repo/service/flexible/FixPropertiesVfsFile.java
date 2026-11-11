package org.artifactory.repo.service.flexible;

import org.artifactory.fs.FileInfo;
import org.artifactory.md.Properties;
import org.artifactory.sapi.fs.VfsFile;
import org.artifactory.sapi.fs.VfsItem;

import java.io.InputStream;

public class FixPropertiesVfsFile extends FixPropertiesVfsItem<FileInfo> implements VfsFile<FileInfo> {

    public FixPropertiesVfsFile(VfsItem<?> vfsFile, Properties properties) {
        super((VfsItem<FileInfo>) vfsFile, properties);
    }

    @Override
    public FileInfo getInfo() {
        return ((VfsFile<?>) vfsItem).getInfo();
    }

    @Override
    public String getSha1() {
        return ((VfsFile<?>) vfsItem).getSha1();
    }

    @Override
    public String getSha2() {
        return ((VfsFile<?>) vfsItem).getSha2();
    }

    @Override
    public String getMd5() {
        return ((VfsFile<?>) vfsItem).getMd5();
    }

    @Override
    public InputStream getStream() {
        return ((VfsFile<?>) vfsItem).getStream();
    }

    @Override
    public long length() {
        return ((VfsFile<?>) vfsItem).length();
    }
}
