package org.artifactory.repo;

import lombok.experimental.UtilityClass;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.cleanup.ArtifactCleanupJob;
import org.artifactory.repo.cleanup.ArtifactCleanupJobUnexclusive;

/**
 * @author Dvir Twina
 */
@UtilityClass
public class ArtifactCleanupJobSelector {
    /**
     * Returns the class of the artifact cleanup job to be used based on the configuration.
     * The purpose of this is to verify an issue at Bosch where replication job is preventing cleanup job from running.
     */
    public static Class<? extends ArtifactCleanupJob> getArtifactCleanupJobClass() {
        return ConstantValues.artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive.getBoolean() ?
                ArtifactCleanupJobUnexclusive.class : ArtifactCleanupJob.class;
    }
}