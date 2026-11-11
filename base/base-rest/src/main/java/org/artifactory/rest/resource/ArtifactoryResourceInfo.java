package org.artifactory.rest.resource;

public record ArtifactoryResourceInfo(boolean isFound, boolean isExpired, boolean isBlocked) {
}
