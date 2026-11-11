package org.artifactory.rest.common.exception;

/**
 * Exception to indicate a problem during an retention operation execution
 *
 * @author volodymyrm
 */
public class FailedToRetrieveArchiveStorageSummaryException extends RuntimeException {

    public FailedToRetrieveArchiveStorageSummaryException(String message) {
        super("Failed to retrieve archive storage summary. Reason: " + message);
    }

    public FailedToRetrieveArchiveStorageSummaryException(String message, Exception e) {
        super("Failed to retrieve archive storage summary. Reason: " + message, e);
    }
}