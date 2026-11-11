package org.artifactory.hqc.service.fullsync;

/**
 * @author Shahar Palmor
 */
public record FullSyncRecord(FullSyncFileRecord fullSyncFileRecord, long lastEventId) {
}
