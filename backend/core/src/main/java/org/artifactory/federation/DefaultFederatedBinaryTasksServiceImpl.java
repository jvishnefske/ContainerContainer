package org.artifactory.federation;

import lombok.NonNull;
import org.artifactory.repo.PersistedRemoteTaskInfo;
import org.jfrog.storage.binstore.providers.base.BinariesTasksInfo;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author annakv
 */
@Service
public class DefaultFederatedBinaryTasksServiceImpl implements FederatedBinaryTasksService {

    private static final String UNSUPPORTED_MESSAGE = "Unsupported operation: federated";

    @Override
    public Collection<PersistedRemoteTaskInfo> getFailingTasks(@NonNull String repoName, @Nullable Integer limit,
            @Nullable Integer page) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public int replayTasks(@NonNull String repoName, @Nullable String sha1) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public int deleteRepoFailedTasksByBinary(@NonNull String repoName, @Nullable String sha1) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public void deleteAllTasksPerRepo(@NonNull String repoName) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

    @Override
    public BinariesTasksInfo getBinariesTasksInfo(@NonNull String repoName) {
        throw new UnsupportedOperationException(UNSUPPORTED_MESSAGE);
    }

}
