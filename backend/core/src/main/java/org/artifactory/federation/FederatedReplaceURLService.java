package org.artifactory.federation;


/**
 * @author Daniel simantov
 * federatedBindURLService handle baseurl change for federated repositories
 */

public interface FederatedReplaceURLService {
    /**
     * Handle baseurl change for federated repositories
     * by updating existing remote members and copying their corresponding
     * queue of the old federated member
     * @param oldUrl the current base URL we want to replace
     * @param newUrl new Base URL
     * @param isReplaceQueues should replace queues and urls
     */
    void replaceUrlAndQueues(String oldUrl, String newUrl, boolean isReplaceQueues);
}
