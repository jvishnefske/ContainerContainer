package org.artifactory.policy;

import org.artifactory.policy.cleanup.bundle.BundleCleanupPolicySearchCriteria;

public interface ReleaseBundlePolicyConfig extends PolicyConfig {
    Integer durationInMinutes();
    BundleCleanupPolicySearchCriteria searchCriteria();
}
