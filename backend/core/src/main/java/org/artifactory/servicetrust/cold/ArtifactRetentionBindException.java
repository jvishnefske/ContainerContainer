package org.artifactory.servicetrust.cold;

/**
 * Exception to indicate a problem during binding operation
 *
 * @author aviva
 */
public class ArtifactRetentionBindException extends IllegalStateException {

    public ArtifactRetentionBindException(String message) {
        super(message);
    }
}