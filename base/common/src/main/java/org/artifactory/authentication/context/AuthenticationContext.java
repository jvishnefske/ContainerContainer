package org.artifactory.authentication.context;

import lombok.Builder;
import lombok.Getter;


/**
 * This class present the context of a specific http request from the authentication side.
 *
 * @author shahafg
 */
@Getter
@Builder
public class AuthenticationContext {

    /**
     * Presents authentication's status
     */
    private AuthenticationContextStatus authenticationStatus;

    /**
     * Presents the calculated extracted basic auth from special headers and params in the request.
     */
    private String extractedAuthorizationToken;

    /**
     * Presents the cache key of the authentication object in AccessFilter.
     */
    private String cacheKey;

    /**
     * Presents the identifier of the user.
     */
    private String loginIdentifier;
}
