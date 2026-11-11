package org.artifactory.federated.model;

import java.util.List;

/**
 * Represents the response aggregated from tunneled requests to all federation members
 *
 * @author Tamar Rosen
 */
public record CrossMemberResponse(int status, List<SingleMemberResponse> memberResponses) {

}
