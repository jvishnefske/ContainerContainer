package org.artifactory.security.access;


import org.artifactory.api.security.access.AccessRestRequest;

/**
 * @author Eli Fishman
 */
public interface AccessRestClient {

    <T> T executePostRequest(AccessRestRequest accessRestRequest,
            String requestPath,
            String authorizationValue,
            Class<T> responseType);

}
