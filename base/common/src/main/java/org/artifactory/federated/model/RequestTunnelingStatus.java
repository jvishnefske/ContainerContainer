package org.artifactory.federated.model;

/**
 * Represents the response status of a tunneled request to a federation member
 * This enum should be common between core and RTFS
 */
public enum RequestTunnelingStatus {
    MEMBER_HEALTHY,
    MEMBER_UNAVAILABLE,
    MEMBER_DISABLED,
    API_ERROR,
    API_NOT_SUPPORTED,
}
