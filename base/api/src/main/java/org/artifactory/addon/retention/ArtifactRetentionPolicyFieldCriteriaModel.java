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
public class ArtifactRetentionPolicyFieldCriteriaModel<T> {
    ArtifactRetentionPolicyInclusionCriteriaModel<T> include;
    ArtifactRetentionPolicyInclusionCriteriaModel<T> exclude;
}
