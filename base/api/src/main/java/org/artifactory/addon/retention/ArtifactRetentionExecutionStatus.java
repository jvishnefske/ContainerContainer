package org.artifactory.addon.retention;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.annotation.Nullable;

/**
 * @author Omri Ziv
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactRetentionExecutionStatus {

    @Nullable
    private String migrationIdentifier;
    private ArtifactRetentionPolicyState state;
    private Long completedNumberOfArtifacts;
    private Long failedNumberOfArtifacts;
    private Long estimatedTotalNumberOfArtifacts;
    private Long completedNumberOfPackages;
    private Long failedNumberOfPackages;
    private Long estimatedTotalNumberOfPackages;
    private Long startTime;
    private Long finishTime;
    private Long successByteSize;
    private Long failureByteSize;
    private String triggeredBy;
    private boolean packageRetention;
}
