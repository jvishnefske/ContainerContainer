package org.artifactory.descriptor.retention.search.criteria;

/**
 * @author Oleksandr Legenkiy
 */
public interface ArtifactRetentionIncludeExcludeCriteria {
    ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor getInclude();

    ArtifactRetentionPolicyPathsInclusionCriteriaDescriptor getExclude();
}
