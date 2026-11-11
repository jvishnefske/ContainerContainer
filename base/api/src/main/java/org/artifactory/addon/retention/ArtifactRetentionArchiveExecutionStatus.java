package org.artifactory.addon.retention;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 *
 * @author Omri Ziv
 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class ArtifactRetentionArchiveExecutionStatus extends ArtifactRetentionExecutionStatus {
    private String policyName;

    public ArtifactRetentionArchiveExecutionStatus(String policyName) {
        this.policyName = policyName;
    }
}
