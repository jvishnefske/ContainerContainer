package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Cluster-wide native DB locks mechanism status.
 */
@Getter
public class ClusterNativeDbLocksMechanismStatusResponse {

    @JsonProperty("nodes")
    private List<NativeDbLocksMechanismStatusResponse> nodes;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("unreachableNodeIds")
    private List<String> unreachableNodeIds;

    public ClusterNativeDbLocksMechanismStatusResponse() {
    }

    public ClusterNativeDbLocksMechanismStatusResponse(List<NativeDbLocksMechanismStatusResponse> nodes,
            List<String> unreachableNodeIds) {
        this.nodes = nodes;
        this.unreachableNodeIds = unreachableNodeIds != null ? unreachableNodeIds : List.of();
    }

}
