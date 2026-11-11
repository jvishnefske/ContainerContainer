package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * JSON representation of the advisory locks held by a single HA node.
 */
public class NodeLocksResponse {

    @JsonProperty("nodeId")
    private String nodeId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("locks")
    private List<LockEntryResponse> locks;

    public NodeLocksResponse() {
    }

    public NodeLocksResponse(String nodeId, String status, List<LockEntryResponse> locks) {
        this.nodeId = nodeId;
        this.status = status;
        this.locks = locks;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getStatus() {
        return status;
    }

    public List<LockEntryResponse> getLocks() {
        return locks;
    }
}
