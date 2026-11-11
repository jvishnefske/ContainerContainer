package org.artifactory.repo;

import lombok.Builder;
import lombok.Getter;


/**
 * Represents the response received from updating the priority of a repo federation
 * RTFS (Artifactory federation Service)
 * Contains the message.
 * Author: Tomer Ben Kereth
 */
@Getter
@Builder
public class FederationPriorityUpdateRtfsResponse {
    private String message;
}