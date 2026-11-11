package org.artifactory.repo;

import lombok.experimental.UtilityClass;
import org.artifactory.common.ConstantValues;
import org.artifactory.repo.replication.RemoteReplicationJob;
import org.artifactory.repo.replication.RemoteReplicationJobUnexclusive;

/**
 * @author Dvir Twina
 */
@UtilityClass
public class RemoteReplicationJobSelector {
    /**
     * Returns the class of the remote replication job to be used based on the configuration.
     * The purpose of this is to verify an issue at Bosch where replication job is preventing cleanup job from running.
     */
    public static Class<? extends RemoteReplicationJob> getRemoteReplicationJobClass() {
        return ConstantValues.artifactsCleanupJobRemoteReplicationJobMutuallyUnexclusive.getBoolean() ?
                RemoteReplicationJobUnexclusive.class : RemoteReplicationJob.class;
    }
}