package org.artifactory.ui.rest.service.artifacts.search.syntax;

public class SyntaxSearchServiceException extends RuntimeException {
    public SyntaxSearchServiceException(Throwable cause) {
        super(cause);
    }

    public SyntaxSearchServiceException(String message) {
        super(message);
    }
}
