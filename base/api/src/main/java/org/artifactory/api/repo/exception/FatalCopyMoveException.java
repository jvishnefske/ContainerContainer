package org.artifactory.api.repo.exception;


public class FatalCopyMoveException extends RuntimeException {

    /**
     * Error message constructor
     *
     * @param message Error message
     */
    public FatalCopyMoveException(String message) {
        super(message);
    }
}
