package org.artifactory.storage;

/**
 * @author Inbar Tal
 */
public class CacheUnAvailableException extends RuntimeException {
    private static final String UNAVAILABLE_MESSAGE = "Cache is being calculated for the first time," +
            " hence is not available at the moment";

    public CacheUnAvailableException() {
        super(UNAVAILABLE_MESSAGE);
    }
}
