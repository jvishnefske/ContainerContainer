package org.artifactory.policy;

import lombok.Builder;

/**
 * @author deepaks
 */

@Builder(toBuilder = true)
public record RepoAvailabilityStatus(
        String repoName,
        boolean isAvailable
) {

}