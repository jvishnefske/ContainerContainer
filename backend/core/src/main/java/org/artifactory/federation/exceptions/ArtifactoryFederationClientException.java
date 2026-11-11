package org.artifactory.federation.exceptions;

/**
 * @author Tomer Slutsky
 */
public class ArtifactoryFederationClientException extends RuntimeException {
    public ArtifactoryFederationClientException(String message) {
        super(message);
    }
}
