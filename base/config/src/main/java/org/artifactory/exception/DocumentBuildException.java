package org.artifactory.exception;

/**
 * @author Volodymyr Shulga
 */
public class DocumentBuildException extends RuntimeException {
    public DocumentBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
