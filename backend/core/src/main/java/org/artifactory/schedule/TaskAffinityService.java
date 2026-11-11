package org.artifactory.schedule;

import org.artifactory.storage.db.servers.model.ArtifactoryServer;

import java.util.List;
import java.util.Optional;

/**
 * @author Omri Ziv
 */
public interface TaskAffinityService {

    /**
     * @param jobName is the task name to find affinity of
     * @return a list of node names has this task affinity
     */
    List<String> getAffinityForTask(JobName jobName);

    /**
     * Get a single Affinity server {@link ArtifactoryServer} from a given list of affinity nodes
     */
    Optional<ArtifactoryServer> pickAffinityMemberByNodeNames(List<String> serverNames);
}