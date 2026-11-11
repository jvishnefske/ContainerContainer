package org.artifactory.application.exception;

public class ApplicationServiceException extends RuntimeException {
    public ApplicationServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ApplicationServiceException(String message) {
        super(message);
    }
}
