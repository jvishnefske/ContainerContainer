package org.artifactory.federation;

import org.artifactory.event.queue.QueueEvent;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.properties.Properties;

import java.util.List;
import java.util.Map;

/**
 * @author Shahar Barel
 */
public interface FederatedRepoPropertiesService {

    /**
     * Fetches properties for a list of node paths in a specific repository.
     *
     * @param nodePaths The list of node paths to fetch properties for
     * @param repoKey   The repository key
     * @return Properties object containing all fetched properties
     */
    Properties getProperties(List<NodePath> nodePaths, String repoKey);

    /**
     * Fetches properties in bulk for a batch of queue events, grouped by repository.
     *
     * @param events The list of queue events to fetch properties for
     * @return Map of repository key to Properties object
     */
    Map<String, Properties> getPropsByRepoKey(List<? extends QueueEvent> events);
}

