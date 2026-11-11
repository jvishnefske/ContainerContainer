package org.artifactory.schedule;

public class FullExecutorQueueException extends RuntimeException {

    public FullExecutorQueueException(String message) {
        super(message);
    }
}
