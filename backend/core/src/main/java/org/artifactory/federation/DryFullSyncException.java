package org.artifactory.federation;

/**
 * @author Sivan Shafrir
 */
public class DryFullSyncException extends RuntimeException {
    public DryFullSyncException(String message) {
        super(message);
    }

    public DryFullSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}