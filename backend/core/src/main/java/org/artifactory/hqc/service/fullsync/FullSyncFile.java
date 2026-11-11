package org.artifactory.hqc.service.fullsync;

import jakarta.annotation.Nonnull;

import java.io.File;

/**
 * @author Shahar Palmor
 */
public class FullSyncFile extends File {
    public FullSyncFile(File dir, @Nonnull String pathname) {
        super(dir, pathname);
    }

    @Override
    public String toString() {
        return getName();
    }
}
