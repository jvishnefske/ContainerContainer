package org.artifactory.repo;


/**
 * Service for Artifactory federation service exception.
 *  @author Daniel Simantov
 */
public class ArtifactoryFederationServiceException extends Exception {

    public ArtifactoryFederationServiceException(String message) {
        super(message);
    }

    public ArtifactoryFederationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}