package org.artifactory.policy.cleanup.builds;

import org.artifactory.policy.PolicyConfig;

public interface BuildPolicyConfig extends PolicyConfig {
    boolean skipTrashcan();
    boolean deleteArtifacts();
    Integer durationInMinutes();
    BuildCleanupPolicySearchCriteria searchCriteria();
}
