package org.artifactory.federation;

import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.GridTopology;
import org.artifactory.repo.federated.FederatedMember;

import java.util.List;

/**
 * Service interface for managing grid topologies.
 * This interface is implemented in the Pro addon.
 *
 * @author Tomer Ben Kereth
 */
public interface GridTopologyService {

    /**
     * Returns true if this Artifactory site is part of an active Grid.
     * Used to gate Grid-specific behaviors such as transparent repository name reservation.
     * The Pro implementation will determine this based on actual Grid state.
     *
     * @return true if this site is a Grid site, false otherwise
     */
    boolean isGridSite();

    /**
     * Generates a grid topology ID based on the additional sites.
     * The ID is a hash of the sorted list of additional sites, ensuring that
     * repositories with the same members share the same topology ID.
     *
     * @param additionalSites List of additional site URLs
     * @return Grid topology ID
     */
    String generateGridTopologyId(List<String> additionalSites);

    /**
     * Gets federated members for a repository configuration.
     * If gridTopologyId exists in baseConfig, gets members from grid topology.
     * Otherwise, returns stored federatedMembers from the repository configuration.
     * If grid topology is enabled and gridTopologyId is present, exceptions are thrown on failure (no fallback).
     *
     * @param federatedRepoConfig The federated repository configuration
     * @return List of federated members (from grid topology if gridTopologyId exists, otherwise from stored members)
     * @throws org.artifactory.addon.federated.exception.GridTopologyNotFoundException if grid topology is not found
     * @throws org.artifactory.addon.federated.exception.GridTopologyException if a database error occurs
     */
    List<FederatedMember> getFederatedMembers(FederatedRepoConfig federatedRepoConfig);

    /**
     * Invalidates a specific grid topology from the cache.
     *
     * @param gridTopologyId The grid topology ID to invalidate
     */
    void invalidateGridTopology(String gridTopologyId);

    /**
     * Invalidates all grid topologies in the cache.
     */
    void invalidateAllGridTopologies();

    /**
     * Creates or gets a grid topology based on remote site URLs and local base URL.
     * If a grid topology with the generated ID already exists, returns it.
     * Otherwise, creates a new grid topology.
     *
     * @param remoteSites List of remote site URLs to include in the topology (excluding local)
     * @param localBaseUrl The local base URL (e.g., "http://localhost:8081/artifactory")
     * @return The grid topology (existing or newly created)
     * @throws org.artifactory.addon.federated.exception.GridTopologyException if a database error occurs
     */
    GridTopology getGridTopology(List<String> remoteSites, String localBaseUrl);

    /**
     * Gets a grid topology by its ID.
     *
     * @param gridTopologyId The grid topology ID
     * @return The grid topology
     * @throws org.artifactory.addon.federated.exception.GridTopologyNotFoundException if topology not found
     * @throws org.artifactory.addon.federated.exception.GridTopologyException if a database error occurs
     */
    GridTopology getGridTopologyById(String gridTopologyId);

    /**
     * Updates grid topologies' additionalSites when a base URL changes.
     * This method finds all grid topologies that contain the old base URL in their additionalSites
     * and replaces it with the new base URL.
     *
     * @param oldBaseUrl The old base URL to replace
     * @param newBaseUrl The new base URL to use
     */
    void updateGridTopologiesOnBaseUrlChange(String oldBaseUrl, String newBaseUrl);

    /**
     * Handles an external grid topology change notification from the Grid service.
     * This method updates the database with the new grid topology data and invalidates the cache.
     *
     * @param gridTopology The grid topology data from Grid service (null for DELETED events)
     * @param changeType The type of change (CREATED, UPDATED, or DELETED)
     * @param gridTopologyId The grid topology ID (required for all event types)
     * @throws org.artifactory.addon.federated.exception.GridTopologyException if a database error occurs
     */
    void handleExternalGridTopologyChange(GridTopology gridTopology,
            GridTopologyChangeType changeType,
            String gridTopologyId);
}

