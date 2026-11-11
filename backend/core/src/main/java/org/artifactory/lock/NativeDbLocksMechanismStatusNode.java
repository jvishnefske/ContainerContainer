package org.artifactory.lock;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON DTO for HA fan-out to {@code GET /api/system/locks/type/local}.
 */
@Data
@NoArgsConstructor
public class NativeDbLocksMechanismStatusNode {

    private String nodeId;
    private String status;

    public NativeDbLocksMechanismStatusNode(String nodeId, NativeDbLocksMechanismStatus status) {
        this.nodeId = nodeId;
        this.status = status.name();
    }
}
