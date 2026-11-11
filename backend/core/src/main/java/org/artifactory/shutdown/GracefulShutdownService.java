package org.artifactory.shutdown;

/**
 * @author igoru
 */
public interface GracefulShutdownService {

    Result waitProcessesCompletion();

    enum Result {
        COMPLETED,
        TIMEOUT,
        ERROR
    }
}
