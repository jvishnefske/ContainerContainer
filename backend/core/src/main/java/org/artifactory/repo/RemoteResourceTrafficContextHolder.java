package org.artifactory.repo;

/**
 * Interface for resource stream handles that support traffic logging.
 * Provides access to transfer metrics needed for traffic event reporting.
 */
public interface RemoteResourceTrafficContextHolder {
    
    /**
     * @return the total number of bytes read/transferred
     */
    long getTotalBytesRead();
    
    /**
     * @return the remote IP address of the client
     */
    String getRemoteIp();
}
