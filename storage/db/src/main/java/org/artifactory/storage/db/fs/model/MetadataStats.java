package org.artifactory.storage.db.fs.model;

import java.io.Serializable;

/**
 * Represents the metadata statistics of an artifact.
 */
public record MetadataStats(long downloadCount, long lastDownloaded, long remoteLastDownloaded, boolean force)
        implements Serializable {
}
