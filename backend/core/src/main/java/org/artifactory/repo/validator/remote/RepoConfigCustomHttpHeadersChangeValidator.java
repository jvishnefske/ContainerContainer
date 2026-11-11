package org.artifactory.repo.validator.remote;

import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.interceptor.RepoConfigChangeValidator;
import org.artifactory.repo.interceptor.RepoConfigChanges;
import org.artifactory.util.CustomHttpHeaderValidator;
import org.springframework.stereotype.Component;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

/**
 * Validates custom HTTP headers on remote repository create/update before persistence, using
 * {@link CustomHttpHeaderValidator}. REST and other clients populate {@link RemoteRepoConfig} first; this runs on
 * save with other {@link RepoConfigChangeValidator}s.
 */
@Component
public class RepoConfigCustomHttpHeadersChangeValidator implements RepoConfigChangeValidator {

    @Override
    public void validate(RepoConfigChanges repoConfigChanges) {
        for (RepoConfigChange change : repoConfigChanges.filterBy(REMOTE, REPO_CREATE, REPO_UPDATE)) {
            RepoConfig newRepoConfig = change.getNewRepoConfig();
            if (newRepoConfig instanceof RemoteRepoConfig remoteRepoConfig) {
                CustomHttpHeaderValidator.validate(remoteRepoConfig.getRepoTypeConfig().getCustomHttpHeaders());
            }
        }
    }
}
