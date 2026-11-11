package org.artifactory.hqc.service.fullsync;

/**
 * @author Tomer Slutsky
 */
public record FullSyncManualFileRequest(String repoKey, String targetRepo, String path) {
}
