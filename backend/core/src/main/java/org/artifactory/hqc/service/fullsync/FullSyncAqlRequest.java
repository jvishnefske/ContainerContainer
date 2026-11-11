package org.artifactory.hqc.service.fullsync;

/**
 * @author Tomer Slutsky
 */
public record FullSyncAqlRequest(String localKey, String remoteUrl, String query) {
}
