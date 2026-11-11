package org.artifactory.addon.yum;

import org.artifactory.fs.FileInfo;

/**
 * @author Priyanshu Gupta
 */
public record RpmDeletionMetadata(FileInfo itemInfo, String name, String architecture, String version,
        String release, String epoch) {

    public boolean hasPackageCoordinates() {
        return name != null && !name.isBlank();
    }
}
