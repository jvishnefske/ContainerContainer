package org.artifactory.federated.model;

/**
 * @author Tamar Rosen
 */
public record SingleMemberResponse(String memberUrl, RequestTunnelingStatus status, String clientResponse) {
}

