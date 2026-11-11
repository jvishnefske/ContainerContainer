package org.artifactory.federated.exception;

/**
 * @author Tamar Rosen
 */
public class RequestTunnelingException extends Exception {
    public RequestTunnelingException(String message, Exception e) {
        super(message, e);
    }
    public RequestTunnelingException(String message) {
        super(message);
    }
}
