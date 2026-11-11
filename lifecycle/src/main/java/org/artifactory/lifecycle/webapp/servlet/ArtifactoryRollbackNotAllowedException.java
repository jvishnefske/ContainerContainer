package org.artifactory.lifecycle.webapp.servlet;

public class ArtifactoryRollbackNotAllowedException extends RuntimeException {

    public ArtifactoryRollbackNotAllowedException(String message) {
        super(message);
    }
}
