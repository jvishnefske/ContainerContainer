package org.artifactory.util;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.compress.archivers.ArchiveEntry;

import java.util.Date;

/**
 * Represents an entry in a NAR (Nix Archive) file.
 * <p>
 * NAR entries can be:
 * <ul>
 *   <li>Regular files (with optional executable flag)</li>
 *   <li>Directories</li>
 *   <li>Symbolic links (with a target path)</li>
 * </ul>
 *
 * @author Natalie Berni
 */
@Getter
@ToString(exclude = "lastModified")
public class NarArchiveEntry implements ArchiveEntry {

    private final String name;
    private final long size;
    private final boolean directory;
    private final boolean executable;
    private final String symlinkTarget;
    private final Date lastModified;

    /**
     * Creates a new NAR archive entry.
     *
     * @param name          the path/name of the entry
     * @param size          the size in bytes (0 for directories and symlinks)
     * @param directory     true if this entry is a directory
     * @param executable    true if this file should be executable
     * @param symlinkTarget the target path if this is a symlink, null otherwise
     */
    public NarArchiveEntry(String name, long size, boolean directory, boolean executable, String symlinkTarget) {
        this.name = name;
        this.size = size;
        this.directory = directory;
        this.executable = executable;
        this.symlinkTarget = symlinkTarget;
        this.lastModified = new Date();
    }

    @Override
    public Date getLastModifiedDate() {
        return lastModified;
    }

    /**
     * Returns true if this entry is a symbolic link.
     *
     * @return true if symlink, false otherwise
     */
    public boolean isSymbolicLink() {
        return symlinkTarget != null;
    }
}
