package org.artifactory.util;

/**
 * @author Oleksandr Legenkiy
 */
public class RepoDoesNotExistException extends DoesNotExistException {

    public RepoDoesNotExistException(String message) {
        super(message);
    }
}
