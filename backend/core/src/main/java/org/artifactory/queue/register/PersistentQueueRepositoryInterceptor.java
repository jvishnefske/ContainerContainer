package org.artifactory.queue.register;

import lombok.AllArgsConstructor;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;

/**
 * This interceptor calls the register and deRegister methods whenever a repository is added or removed.
 *
 * @author Avishay Halpren
 */
@Component
@AllArgsConstructor
public class PersistentQueueRepositoryInterceptor implements RepoConfigChangeInterceptor {

    private final PersistentQueueRegistrationService queueRegistrationService;

    @Override
    public void onAfterSave(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        List<RepoConfigChange> localRepoConfigChanges = repoConfigChanges.getOrDefault(LOCAL, List.of());
        List<RepoConfigChange> federatedRepoConfigChanges = repoConfigChanges.getOrDefault(FEDERATED, List.of());
        List<RepoConfigChange> remoteRepoConfigChanges = repoConfigChanges.getOrDefault(REMOTE, List.of());
        List<RepoConfigChange> virtualRepoConfigChanges = repoConfigChanges.getOrDefault(VIRTUAL, List.of());

        // repo was removed -> deregister
        Stream.of(localRepoConfigChanges, federatedRepoConfigChanges, remoteRepoConfigChanges, virtualRepoConfigChanges)
                .flatMap(Collection::stream)
                .filter(repoConfigChange -> REPO_DELETE.equals(repoConfigChange.getConfigAction()))
                .map(RepoConfigChange::getRepoKey)
                .forEach(queueRegistrationService::deRegisterRepository);

        // repo was added -> register
        Stream.of(localRepoConfigChanges, federatedRepoConfigChanges, remoteRepoConfigChanges, virtualRepoConfigChanges)
                .flatMap(Collection::stream)
                .filter(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()))
                .map(RepoConfigChange::getRepoKey)
                .forEach(queueRegistrationService::registerRepository);
    }
}
