package org.artifactory.metadata.service.interceptor;

import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.artifactory.descriptor.repo.RepoType.Jem;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;

@Component
public class JemRepoConfigChangeValidator implements RepoConfigChangeValidator {

    @Override
    public void validate(RepoConfigChanges repoConfigChanges) {
        validateChanges(repoConfigChanges.filterBy(REPO_CREATE), "create");
        validateChanges(repoConfigChanges.filterBy(REPO_UPDATE), "update");
    }

    private void validateChanges(List<RepoConfigChange> changes, String action) {
        if (containsFederatedJemRepo(changes)) {
            throw new ConfigurationException(
                    "JEM repositories cannot be federated. Action attempted: " + action,
                    SC_BAD_REQUEST
            );
        }
    }

    private boolean containsFederatedJemRepo(List<RepoConfigChange> changes) {
        return changes.stream()
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(Objects::nonNull)
                .distinct()
                .anyMatch(this::isFederatedJemRepo);
    }

    private boolean isFederatedJemRepo(RepoConfig repo) {
        return Jem == repo.getPackageType() && FEDERATED == repo.getRepoType();
    }
}