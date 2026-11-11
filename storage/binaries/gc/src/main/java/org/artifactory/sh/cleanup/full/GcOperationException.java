package org.artifactory.sh.cleanup.full;

/**
 * An exception to indicate a generic error in Garbage Collector's operation.
 *
 * @author Oleksandr Legenkiy
 */
public class GcOperationException extends RuntimeException {

    public GcOperationException(String msg, Throwable e) {
        super(msg, e);
    }
}
