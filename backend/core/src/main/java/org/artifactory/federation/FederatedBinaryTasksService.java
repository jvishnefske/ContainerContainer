package org.artifactory.federation;

import lombok.NonNull;
import org.artifactory.federated.service.FederationService;
import org.artifactory.repo.PersistedRemoteTaskInfo;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;

import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author annakv
 */
public interface FederatedBinaryTasksService extends FederationService {

    /**
     * Gets persisted remote tasks, which failed to be processed and number of retries is maximum
     *
     * @param repoName name of the repository
     * @param limit limit of tasks in result, default is 1000
     * @param page number of batch for cases when limit is less then amount of tasks
     * @return list of persisted remote tasks
     */
    Collection<PersistedRemoteTaskInfo> getFailingTasks(@NonNull String repoName, Integer limit, Integer page);

    /**
     * Replays failed tasks by setting to 0 a failures counter
     *
     * @param repoName name of the repository, required
     * @param sha1 sha1 of a binary, optional, if not set then tasks of the repo will be replayed
     * @return number of replayed persisted remote tasks
     */
    int replayTasks(@NonNull String repoName, @Nullable String sha1);
    /**
     * Deletes failed tasks where failures counter is maximum
     *
     * @param repoName name of the repository, required
     * @param sha1 sha1 of a binary, if not set then tasks of the repo will be deleted
     * @return number of deleted persisted remote tasks
     */
    int deleteRepoFailedTasksByBinary(@NonNull String repoName, @Nullable String sha1);

    void deleteAllTasksPerRepo(@NonNull String repoName);

    BinariesTasksInfo getBinariesTasksInfo(@NonNull String repoName);
}
