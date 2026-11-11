package org.artifactory.storage.io;

import org.artifactory.sapi.fs.VfsFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Supplies a new InputStream upon call to {@link #getStream()}
 */
public interface InputStreamSupplier {

    /**
     * @return a new {@link InputStream}
     * @throws IOException
     */
    InputStream getStream() throws IOException;

    /**
     * @return the name associated with this {@link InputStreamSupplier}, e.g. file name
     */
    String getName();

    /**
     * @return the full path where this {@link InputStreamSupplier} was originated from, e.g. file absolute path
     */
    String getPath();

    /**
     * @return the length of each new {@link InputStream} returned by this {@link InputStreamSupplier}
     */
    long length();

    static InputStreamSupplier of(File file) {
        return new InputStreamSupplier() {
            @Override
            public InputStream getStream() throws FileNotFoundException {
                return new FileInputStream(file);
            }

            @Override
            public String getName() {
                return file.getName();
            }

            @Override
            public String getPath() {
                return file.getAbsolutePath();
            }

            @Override
            public long length() {
                return file.length();
            }
        };
    }

    static InputStreamSupplier of(VfsFile vfsFile) {
        return new InputStreamSupplier() {
            @Override
            public InputStream getStream() {
                return vfsFile.getStream();
            }

            @Override
            public String getName() {
                return vfsFile.getName();
            }

            @Override
            public String getPath() {
                return vfsFile.getRepoPath().toPath();
            }

            @Override
            public long length() {
                return vfsFile.length();
            }
        };
    }

}
