package org.artifactory.addon.retention;

/**
 *
 * @author Omri Ziv
 */
public enum ArtifactRetentionPolicyState {
    PENDING,
    IN_PROGRESS,
    IN_PROGRESS_WITH_ERRORS,
    COMPLETE,
    FAILED,
    STOPPING,
    STOPPED_BY_USER
}
