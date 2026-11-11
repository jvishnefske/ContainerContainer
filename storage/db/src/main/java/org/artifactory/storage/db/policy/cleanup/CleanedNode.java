package org.artifactory.storage.db.policy.cleanup;

import lombok.Builder;
import org.artifactory.policy.NodeMetadata;

/**
 * Record to represent an artifact, that is cleaned by a cleanup policy
 *
 * @author vovam
 */
@Builder(toBuilder = true)
public record CleanedNode(
        String policyKey,
        int policyRunId,
        String packageName,
        String packageVersion,
        boolean lead,
        String repo,
        String nodePath,
        String nodeName,
        long binLength,
        String sha1,
        Integer status,
        String message,
        NodeMetadata metadata
) {
}