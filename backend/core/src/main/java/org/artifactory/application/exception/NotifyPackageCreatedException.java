package org.artifactory.application.exception;

public class NotifyPackageCreatedException extends ApplicationServiceException {
    public NotifyPackageCreatedException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotifyPackageCreatedException(String message) {
        super(message);
    }
}
