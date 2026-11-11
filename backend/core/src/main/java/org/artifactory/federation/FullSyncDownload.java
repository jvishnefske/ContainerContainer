package org.artifactory.federation;

import org.artifactory.api.request.InternalArtifactoryRequest;
import org.artifactory.api.request.JerseyArtifactoryResponse;

/**
 * @author Tomer Slutsky
 */
public record FullSyncDownload(InternalArtifactoryRequest request, JerseyArtifactoryResponse response) {
}
