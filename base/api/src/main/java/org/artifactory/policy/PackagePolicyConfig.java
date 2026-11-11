package org.artifactory.policy;

public interface PackagePolicyConfig extends PolicyConfig {
    boolean skipTrashcan();
    Integer durationInMinutes();
    PackagePolicySearchCriteria searchCriteria();
}
