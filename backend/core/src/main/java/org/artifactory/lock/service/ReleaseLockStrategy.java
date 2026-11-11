package org.artifactory.lock.service;

/**
 * @author Mor Merhav
 */
public interface ReleaseLockStrategy {

    void releaseOrphaned();

    void releaseExpired();
}
