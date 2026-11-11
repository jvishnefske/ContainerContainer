package org.artifactory.federation;

import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.GridTopology;
import org.artifactory.repo.federated.FederatedMember;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of GridTopologyService for OSS.
 * Provides fallback behavior when Pro addon is not available.
 *
 * @author Tomer Ben Kereth
 */
@Service
public class DefaultGridTopologyServiceImpl implements GridTopologyService {

    @Override
    public boolean isGridSite() {
        return false;
    }

    @Override
    public String generateGridTopologyId(List<String> additionalSites) {
        throw new UnsupportedOperationException("Grid topologies are only available in Pro");
    }

    @Override
    public List<FederatedMember> getFederatedMembers(FederatedRepoConfig federatedRepoConfig) {
        throw new UnsupportedOperationException("Grid topologies are only available in Pro");
    }

    @Override
    public void invalidateGridTopology(String gridTopologyId) {
        // Default implementation does nothing (OSS)
    }

    @Override
    public void invalidateAllGridTopologies() {
        // Default implementation does nothing (OSS)
    }

    @Override
    public GridTopology getGridTopology(List<String> remoteSites, String localBaseUrl) {
        throw new UnsupportedOperationException("Grid topologies are only available in Pro");
    }

    @Override
    public GridTopology getGridTopologyById(String gridTopologyId) {
        throw new UnsupportedOperationException("Grid topologies are only available in Pro");
    }

    @Override
    public void updateGridTopologiesOnBaseUrlChange(String oldBaseUrl, String newBaseUrl) {
        // Default implementation does nothing (OSS)
    }

    @Override
    public void handleExternalGridTopologyChange(GridTopology gridTopology,
            GridTopologyChangeType changeType,
            String gridTopologyId) {
        // Default implementation does nothing (OSS)
    }
}
