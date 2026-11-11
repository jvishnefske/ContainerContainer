package org.artifactory.rest.common.resource;

/**
 * Indicates some problems while waiting for the request processing to complete.
 *
 * @author Igor Usenko
 */
public class ExecutionWaitException extends RuntimeException {

    public ExecutionWaitException(Throwable cause) {
        super(cause);
    }
}
