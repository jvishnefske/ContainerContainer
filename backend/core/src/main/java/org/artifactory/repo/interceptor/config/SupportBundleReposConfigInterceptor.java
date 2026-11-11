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

package org.artifactory.repo.interceptor.config;

import jakarta.annotation.Nonnull;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.exception.http.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.system.SystemRepos.SUPPORT_BUNDLE_REPO_KEY;

/**
 * @author Fred Simon
 */
@Component
public class SupportBundleReposConfigInterceptor implements RepoConfigChangeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SupportBundleReposConfigInterceptor.class);
    private static final String ERR_RESERVED_REPO_NAME = "%s is reserved repo name.";

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        List<RepoConfigChange> repoConfigCreateChanges =
                getRepoConfigChangesByAction(mutableRepoConfigChanges, REPO_CREATE);
        List<RepoConfigChange> repoConfigUpdateChanges = getRepoConfigChangesByAction(mutableRepoConfigChanges,
                REPO_UPDATE);
        if (isForbiddenSupportBundleReposExist(repoConfigCreateChanges)) {
            throw new BadRequestException(format(ERR_RESERVED_REPO_NAME, SUPPORT_BUNDLE_REPO_KEY));
        }
        if (isForbiddenSupportBundleReposExist(repoConfigUpdateChanges)) {
            log.warn("{} is internal repo name, please change any repo with the same key",
                    SUPPORT_BUNDLE_REPO_KEY);
        }
    }

    @Nonnull
    static List<RepoConfigChange> getRepoConfigChangesByAction(
            Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges,
            RepoConfigChange.ConfigAction configAction) {
        List<RepoConfigChange> localRepoChanges = repoConfigChanges.getOrDefault(RepoDetailsType.LOCAL, List.of());
        List<RepoConfigChange> federatedRepoChanges =
                repoConfigChanges.getOrDefault(RepoDetailsType.FEDERATED, List.of());
        List<RepoConfigChange> remoteRepoChanges = repoConfigChanges.getOrDefault(RepoDetailsType.REMOTE, List.of());
        List<RepoConfigChange> virtualRepoChanges = repoConfigChanges.getOrDefault(RepoDetailsType.VIRTUAL, List.of());
        return Stream.of(localRepoChanges, federatedRepoChanges, remoteRepoChanges, virtualRepoChanges)
                .flatMap(Collection::stream)
                .filter(repoConfigChange -> configAction.equals(repoConfigChange.getConfigAction()))
                .collect(Collectors.toList());
    }

    private boolean isForbiddenSupportBundleReposExist(List<RepoConfigChange> repoConfigChanges) {
        return repoConfigChanges.stream()
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(repo -> SUPPORT_BUNDLE_REPO_KEY.equals(repo.getKey()))
                .anyMatch(repo -> !RepoType.Support.equals(repo.getPackageType()));
    }
}
