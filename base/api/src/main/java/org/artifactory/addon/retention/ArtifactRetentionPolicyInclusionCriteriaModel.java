package org.artifactory.addon.retention;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Tamar Rosen
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class ArtifactRetentionPolicyInclusionCriteriaModel<T> {
    String operator;
    List<T> values;
}
