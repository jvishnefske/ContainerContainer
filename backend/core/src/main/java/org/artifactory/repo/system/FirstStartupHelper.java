/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.repo.system;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPath;
import org.artifactory.repo.RepoPathFactory;

import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.common.ConstantValues.defaultPipelinesRepoKey;
import static org.artifactory.repo.config.RepoConfigDefaultValues.EXAMPLE_REPO_KEY;

/**
 * A helper that provides information about Artifactory's state - is it the first time Artifactory is starting
 *
 * @author Yuval Reches
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FirstStartupHelper {

    private static final short NUM_OF_DEFAULT_REPOS = 1;
    private static final int LOCAL_REPOS_TEST_THRESHOLD = 15;

    public static boolean onlyDefaultReposExist() {
        RepositoryConfigService repositoryConfigService =
                ContextHelper.get().beanForType(RepositoryConfigService.class);
        Set<String> allLocalRepoKeys = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.LOCAL)
                .stream()
                .map(RepoConfig::getKey)
                .collect(Collectors.toSet());
        if (allLocalRepoKeys.size() >= LOCAL_REPOS_TEST_THRESHOLD) {
            // We assume that if the customer has more than 15 local repos, we have no reason to perform this
            // expensive verification.
            return false;
        }
        allLocalRepoKeys = allLocalRepoKeys.stream()
                .filter(repoKey -> !repoKey.equals(defaultPipelinesRepoKey.getString()))
                .filter(repoKey -> !SystemRepos.SystemRepoConfig.isSystemRepo(repoKey))
                .filter(repoKey -> !repoService().isExternalSystemRepo(repoKey))
                .collect(Collectors.toSet());
        RepoPath exampleRepoRoot = RepoPathFactory.create(EXAMPLE_REPO_KEY, "");

        // only example repo exist and it is empty
        return repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE).isEmpty()
                && repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED).isEmpty()
                && repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL).isEmpty()
                && allLocalRepoKeys.size() <= NUM_OF_DEFAULT_REPOS
                && allLocalRepoKeys.stream()
                .allMatch(EXAMPLE_REPO_KEY::equals)
                && !repoService().isRepoContainsFiles(exampleRepoRoot.getRepoKey());
    }

    //this is here because it has to be passed all the way from CentralConfigService and I don't want to create (yet)
    //another cycle
    private static RepositoryService repoService() {
        return ContextHelper.get().getRepositoryService();
    }

}
