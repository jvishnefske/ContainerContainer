package org.artifactory.application.exception;

public class NotifyExistsPackageException extends ApplicationServiceException {
    public NotifyExistsPackageException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotifyExistsPackageException(String message) {
        super(message);
    }
}
