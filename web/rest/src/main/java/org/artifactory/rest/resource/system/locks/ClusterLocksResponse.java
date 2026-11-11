package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Cluster-wide response for {@code GET /api/system/locks}.
 */
public class ClusterLocksResponse {

    @JsonProperty("meta")
    private LocksResponseMeta meta;

    @JsonProperty("summary")
    private LocksResponseSummary summary;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("warnings")
    private List<LocksResponseWarning> warnings;

    @JsonProperty("nodes")
    private List<NodeLocksResponse> nodes;

    public ClusterLocksResponse() {
    }

    public ClusterLocksResponse(LocksResponseMeta meta, LocksResponseSummary summary,
            List<LocksResponseWarning> warnings, List<NodeLocksResponse> nodes) {
        this.meta = meta;
        this.summary = summary;
        this.warnings = warnings;
        this.nodes = nodes;
    }

    public LocksResponseMeta getMeta() {
        return meta;
    }

    public LocksResponseSummary getSummary() {
        return summary;
    }

    public List<LocksResponseWarning> getWarnings() {
        return warnings;
    }

    public List<NodeLocksResponse> getNodes() {
        return nodes;
    }
}
