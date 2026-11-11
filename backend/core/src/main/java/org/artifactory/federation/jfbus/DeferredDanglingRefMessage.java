package org.artifactory.federation.jfbus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JFBus payload for deferred federated virtual dangling-ref cleanup.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeferredDanglingRefMessage {
    private String virtualRepoKey;
    private String childRepoKey;
}
