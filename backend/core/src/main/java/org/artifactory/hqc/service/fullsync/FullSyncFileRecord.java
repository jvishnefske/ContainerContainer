package org.artifactory.hqc.service.fullsync;

/**
 * @author Shahar Palmor
 */
public record FullSyncFileRecord(String path, String repoKey, long timestamp) {
}
