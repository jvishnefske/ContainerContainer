package org.artifactory.policy.cleanup.builds;

import lombok.Builder;

/**
 * @author Chaitanya Vootla
 */
@Builder(toBuilder = true)
public record BuildCleanupPolicyConfig(
        boolean skipTrashcan,
        boolean deleteArtifacts,
        Integer durationInMinutes,
        BuildCleanupPolicySearchCriteria searchCriteria) implements BuildPolicyConfig {
}