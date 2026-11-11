package org.artifactory.addon.retention;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 *
 * @author Omri Ziv
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ArtifactRetentionPolicyModel extends ArtifactRetentionPolicyBaseModel {
    ArtifactRetentionExecutionStatus lastRunStatus;
    private Long nextExecutionTime;
}
