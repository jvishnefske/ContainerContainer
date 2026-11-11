package org.artifactory.addon.retention;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Tamar Rosen
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ArtifactRetentionCandidateCriteria {
    private ArtifactRetentionPolicyFieldCriteriaModel<ArtifactRetentionPolicyPropertyCriteriaModel> properties;
    private ArtifactRetentionPolicyFieldCriteriaModel<String> repositories;
    private ArtifactRetentionPolicyFieldCriteriaModel<String> paths;
    private ArtifactRetentionPolicyFieldCriteriaModel<String> names;
    private Long createdBefore;
    private Long downloadedBefore;
}
