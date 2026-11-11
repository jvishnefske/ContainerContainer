package org.artifactory.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Grid topology model representing a reusable topology structure for federated repositories.
 * Topologies define the type (Local/Mirror) and additional sites for federation that can be used
 * to create federated repositories with consistent configuration.
 *
 * @author Tomer Ben Kereth
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GridTopology {
    private String gridTopologyId;

    private TopologyType topologyType;

    @Builder.Default
    private List<String> additionalSites = Lists.newArrayList();

    @JsonIgnore
    private long creationDate;

    @JsonIgnore
    private long modificationDate;

    /**
     * Topology type enum for federated repository topologies.
     */
    public enum TopologyType {
        SELF_DEFINED,
        MIRROR
    }

    /**
     * Determines the topology type based on additional sites.
     * SELF_DEFINED if no additional sites, MIRROR otherwise.
     *
     * @param additionalSites List of additional site URLs
     * @return TopologyType.SELF_DEFINED if empty, TopologyType.MIRROR otherwise
     */
    public static TopologyType determineTopologyType(List<String> additionalSites) {
        return (additionalSites == null || additionalSites.isEmpty()) ?
                TopologyType.SELF_DEFINED : TopologyType.MIRROR;
    }
}
