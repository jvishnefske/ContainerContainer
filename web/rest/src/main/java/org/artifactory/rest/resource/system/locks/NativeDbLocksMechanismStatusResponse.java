package org.artifactory.rest.resource.system.locks;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.artifactory.lock.NativeDbLocksMechanismStatus;

/**
 * JSON representation of native DB locks mechanism status for a single node.
 */
@Getter
public class NativeDbLocksMechanismStatusResponse {

    @JsonProperty("nodeId")
    private String nodeId;

    @JsonProperty("status")
    private String status;

    public NativeDbLocksMechanismStatusResponse() {
    }

    public NativeDbLocksMechanismStatusResponse(String nodeId, NativeDbLocksMechanismStatus status) {
        this.nodeId = nodeId;
        this.status = status.name();
    }

}
