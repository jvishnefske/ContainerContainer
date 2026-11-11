package org.artifactory.file;

import jakarta.annotation.Nonnull;
import org.artifactory.util.ZipUtils;

import java.io.File;

/**
 * As described in RTFACT-24719, deleting file without pathname validation is the way to DoS.
 * All {@link File} objects should be sanitized with current wrapper before deletion.
 */
public class ArtifactoryValidatedFile extends File {
    public ArtifactoryValidatedFile(@Nonnull String pathname) {
        super(ZipUtils.validateEntryName(pathname));
    }

    public ArtifactoryValidatedFile(String parent, @Nonnull String child) {
        super(parent, ZipUtils.validateEntryName(child));
    }

    public ArtifactoryValidatedFile(File parent, @Nonnull String child) {
        super(parent, ZipUtils.validateEntryName(child));
    }
}
