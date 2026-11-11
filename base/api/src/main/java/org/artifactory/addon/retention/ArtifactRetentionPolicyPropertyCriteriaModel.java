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
public class ArtifactRetentionPolicyPropertyCriteriaModel {
    String propertyKey;
    String propertyValue;
}
