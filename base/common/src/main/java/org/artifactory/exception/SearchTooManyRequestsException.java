package org.artifactory.exception;

public class SearchTooManyRequestsException extends RuntimeException {

    public SearchTooManyRequestsException() {
        super("Too many concurrent search requests");
    }
}
