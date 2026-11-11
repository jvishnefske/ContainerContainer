package org.artifactory.projects.exception;

/**
 * @author Mor Merhav
 */
public class AccessClientStateException extends RuntimeException {

    public AccessClientStateException(String message) {
        super(message);
    }
}
