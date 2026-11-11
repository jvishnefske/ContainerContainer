package org.artifactory.federated.service;

import org.artifactory.federated.exception.RequestTunnelingException;
import org.artifactory.federated.model.CrossMemberRequest;
import org.artifactory.federated.model.CrossMemberResponse;

/**
 * Service for tunneling requests throughout the federation
 *
 * @author Tamar Rosen
 */
public interface RequestTunnelingService extends FederationService {
    String INTERNAL_SCOPE_TUNNEL_REQUEST = "internal:federated-repo/tunnel-request:x";
    String REF_PATH_REPO_TEMPLATE = "{repoKey}";
    CrossMemberResponse tunnelRequest(CrossMemberRequest request) throws RequestTunnelingException;
}
