package org.artifactory.authentication.context;

/**
 * The enum presents the status of AuthenticationContext
 *
 * AUTHENTICATION_ALLOWED - present a situation the ArtifactoryAuthenticationContextFilter got an answer the request is
 * valid to used as authenticated request.
 *
 * ANONYMOUS_ALLOWED - present a situation the ArtifactoryAuthenticationContextFilter got an answer the request is
 * valid to used as anonymous request.
 *
 * HAS_ERROR - present a situation the ArtifactoryAuthenticationContextFilter got an error back and should stop the
 * flow in AccessFilter.
 *
 * NOT_HANDLED - present a situation the ArtifactoryAuthenticationContextFilter got an answer that the request cannot
 * be handled by ArtifactoryAuthenticationContextFilter object.
 *
 * FORCE_ANONYMOUS - present a situation the ArtifactoryAuthenticationContextFilter got an answer the request is
 * forced to use as anonymous request even if the user has valid authentication.
 *
 * @author Shahafg
 */
public enum AuthenticationContextStatus {
    AUTHENTICATION_ALLOWED, ANONYMOUS_ALLOWED, HAS_ERROR, NOT_HANDLED, FORCE_ANONYMOUS
}