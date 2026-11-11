package org.artifactory.exception;

/**
 * @author Bhanu Reddy
 */
public class WorkerEventsException extends RuntimeException {

    public WorkerEventsException(String message) {
        super("Worker Event Error: " + message);
    }
}
