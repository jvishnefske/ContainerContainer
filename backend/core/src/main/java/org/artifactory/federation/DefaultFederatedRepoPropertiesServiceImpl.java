package org.artifactory.federation;

import org.artifactory.event.queue.QueueEvent;
import org.jfrog.rtfs.dto.properties.NodePath;
import org.jfrog.rtfs.dto.properties.Properties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author Shahar Barel
 */
@Service
public class DefaultFederatedRepoPropertiesServiceImpl implements FederatedRepoPropertiesService {

    private static final String UNSUPPORTED_MESSAGE = "Unsupported operation: federated";

    @Override
    public Properties getProperties(List<NodePath> nodePaths, String repoKey) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public Map<String, Properties> getPropsByRepoKey(List<? extends QueueEvent> events) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }
}
