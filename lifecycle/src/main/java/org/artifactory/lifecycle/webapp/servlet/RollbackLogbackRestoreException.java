package org.artifactory.lifecycle.webapp.servlet;

public class RollbackLogbackRestoreException extends RuntimeException {

    public RollbackLogbackRestoreException(String rollbackVersion, Throwable cause) {
        super("Artifactory startup failed: unable to restore logback.xml from config history for rollback version "
                + rollbackVersion, cause);
    }
}
