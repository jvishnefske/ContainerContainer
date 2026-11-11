package org.artifactory.repo;

import lombok.Builder;

/**
 *
 * Represents the heartbeat legacy request information
 * @author Alexei Vainshtein
 */
@Builder
public class HeartbeatLegacyRequest {

    private String localKey;
    private String originRepoKey;
    private Long version;

    public String getLocalKey() {
        return localKey;
    }

    public String getOriginRepoKey() {
        return originRepoKey;
    }

    public Long getVersion() {
        return version;
    }
}
