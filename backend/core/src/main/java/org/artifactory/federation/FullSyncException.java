package org.artifactory.federation;

/**
 * @author Tomer Slutsky
 */
public class FullSyncException extends RuntimeException {

    public FullSyncException(String message) {
        super(message);
    }

    public FullSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
