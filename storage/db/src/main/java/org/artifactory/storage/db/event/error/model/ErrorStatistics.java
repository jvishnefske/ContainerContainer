package org.artifactory.storage.db.event.error.model;

/**
 * @author avishayh
 */
public interface ErrorStatistics {
    int getErrorCount();
    long getFirstErrorTime();
}
