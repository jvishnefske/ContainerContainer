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
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.config.ConfigurationException;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.repo.system.SystemRepos;
import org.artifactory.repo.system.suppliers.BuildInfoRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_DELETE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.system.SystemRepos.BUILDINFO_REPO_KEY;

/**
 * @author Fred Simon
 */
@Component
@Slf4j
public class BuildInfoRepoConfigInterceptor implements RepoConfigChangeInterceptor {
    private static final String ERR_DEL_BUILD_REPO = "Deletion of last BuildInfo repository is not allowed";
    private RepositoryConfigService repositoryConfigService;
    private CentralConfigService centralConfigService;

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    /**
     * Asserting the following rules about BuildInfo repository:
     * 1. No more than 1 local default BuildInfo Repo
     * 2. No remote or virtual BuildInfo Repo
     * 3. BuildInfo repository must exist. In case it doesn't exist, we add it with default name
     * {@link SystemRepos#BUILDINFO_REPO_KEY}
     */
    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        List<RepoConfigChange> localAndFederatedRepoChanges =
                Stream.of(repoConfigChanges.getOrDefault(LOCAL, List.of()),
                                repoConfigChanges.getOrDefault(FEDERATED, List.of()))
                        .flatMap(Collection::stream)
                        .toList();
        assertNoMoreThanOneDefaultBuildInfo(localAndFederatedRepoChanges);
        assertNoBuildInfoInRemoteVirtual(repoConfigChanges);
        addBuildInfoRepoIfNeeded(repoConfigChanges, localAndFederatedRepoChanges);
    }

    private void assertNoMoreThanOneDefaultBuildInfo(List<RepoConfigChange> localAndFederatedRepoChanges) {
        boolean buildInfoRepoExists = localAndFederatedRepoChanges.stream()
                .filter(repoConfigChange -> REPO_CREATE == repoConfigChange.getConfigAction() ||
                        REPO_UPDATE == repoConfigChange.getConfigAction())
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(Objects::nonNull)
                .filter(repo -> RepoType.BuildInfo == repo.getPackageType())
                .filter(repo -> repo.getKey().startsWith(BUILDINFO_REPO_KEY))
                .count() > 1;
        if (buildInfoRepoExists) {
            throw new ConfigurationException("Can't have more than one " + RepoType.BuildInfo.getType() +
                    " repository.");
        }
    }

    private void assertNoBuildInfoInRemoteVirtual(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        List<RepoConfigChange> remoteRepoConfigChanges = repoConfigChanges.getOrDefault(REMOTE, List.of());
        List<RepoConfigChange> virtualRepoConfigChanges = repoConfigChanges.getOrDefault(VIRTUAL, List.of());
        if (Stream.of(remoteRepoConfigChanges, virtualRepoConfigChanges)
                .flatMap(Collection::stream)
                .filter(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                        REPO_UPDATE.equals(repoConfigChange.getConfigAction()))
                .map(RepoConfigChange::getNewRepoConfig)
                .filter(Objects::nonNull)
                .anyMatch(repo -> RepoType.BuildInfo == repo.getPackageType())) {
            throw new ConfigurationException(
                    "Configuring a Remote/virtual " + RepoType.BuildInfo.getType() + " repository is not allowed");
        }
    }

    private void addBuildInfoRepoIfNeeded(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges,
            List<RepoConfigChange> localAndFederatedRepoChanges) {
        List<RepoConfig> existingBuildInfoRepos =
                repositoryConfigService.getRepoConfigsByPackageType(RepoType.BuildInfo);
        // build info repo does not exist in storage
        if (existingBuildInfoRepos.isEmpty()) {
            boolean isBuildInfoRepoCreated = localAndFederatedRepoChanges.stream()
                    .filter(repoConfigChange -> REPO_CREATE == repoConfigChange.getConfigAction() ||
                            REPO_UPDATE == repoConfigChange.getConfigAction())
                    .map(RepoConfigChange::getNewRepoConfig)
                    .filter(Objects::nonNull)
                    .anyMatch(repo -> RepoType.BuildInfo == repo.getPackageType());
            // build info isn't being created
            if (!isBuildInfoRepoCreated) {
                addDefaultBuildInfoRepo(repoConfigChanges, localAndFederatedRepoChanges);
            }
        } else {
            List<RepoConfigChange> buildInfoDeletions = repoConfigChanges.getOrDefault(LOCAL, List.of()).stream()
                    .filter(repoConfigChange -> REPO_DELETE.equals(repoConfigChange.getConfigAction()))
                    .filter(repo -> repo.getOldRepoConfig() != null)
                    .filter(repo -> RepoType.BuildInfo == repo.getOldRepoConfig().getPackageType())
                    .toList();
            // All build info repos are being deleted
            if (!buildInfoDeletions.isEmpty() && buildInfoDeletions.size() == existingBuildInfoRepos.size()) {
                log.warn(ERR_DEL_BUILD_REPO);
                Optional<RepoConfigChange> optionalRepoConfigChange = buildInfoDeletions.stream()
                        .filter(repoConfigChange -> repoConfigChange.getOldRepoConfig() != null)
                        .filter(repoConfigChange -> repoConfigChange.getOldRepoConfig().getKey()
                                .equals(BUILDINFO_REPO_KEY)).findAny();
                optionalRepoConfigChange.ifPresent(
                        repoConfigChange -> updateOldDefaultBuildInfoRepo(repoConfigChanges, repoConfigChange));
            }
        }
    }

    private void addDefaultBuildInfoRepo(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges,
            List<RepoConfigChange> localAndFederatedRepoChanges) {
        String repoKey = BUILDINFO_REPO_KEY;
        List<RepoConfigChange> localRepoChanges = repoConfigChanges.getOrDefault(LOCAL, new ArrayList<>());

        // 5 fallbacks allowed for saving the repo
        for (int i = 0; i <= 5; i++) {
            repoKey = (i > 0) ? BUILDINFO_REPO_KEY + "-" + i : BUILDINFO_REPO_KEY;

            if (repositoryConfigService.existsByRepoKey(repoKey) ||
                    isRepoConfigChangesContainsRepo(localAndFederatedRepoChanges, repoKey)) {
                log.warn("'{}' is already taken as repo name for {} repo", repoKey, RepoType.BuildInfo.getType());
            } else {
                LocalRepoConfig buildInfoRepo = getDefaultBuildInfoRepo(centralConfigService.getDescriptor(), repoKey);
                localRepoChanges.add(new RepoConfigChange(repoKey, REPO_CREATE, buildInfoRepo, null));
                repoConfigChanges.put(LOCAL, localRepoChanges);
                log.info("Added {} repository under {}", RepoType.BuildInfo.getType(), repoKey);
                break;
            }
        }
        assertBuildInfoCreated(localRepoChanges, repoKey);
    }

    @Nonnull
    private LocalRepoConfig getDefaultBuildInfoRepo(CentralConfigDescriptor descriptor, String repoKey) {
        return new BuildInfoRepo().buildRepoConfig(descriptor, repoKey);
    }

    private void updateOldDefaultBuildInfoRepo(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges,
            RepoConfigChange deleteOldDefaultBuildInfoRepoChange) {
        String oldBuildInfoKey = deleteOldDefaultBuildInfoRepoChange.getRepoKey();
        LocalRepoConfig defaultBuildInfoRepo =
                getDefaultBuildInfoRepo(centralConfigService.getDescriptor(), oldBuildInfoKey);
        List<RepoConfigChange> localRepoChanges = repoConfigChanges.getOrDefault(LOCAL, new ArrayList<>());
        localRepoChanges.remove(deleteOldDefaultBuildInfoRepoChange);
        localRepoChanges.add(new RepoConfigChange(oldBuildInfoKey, REPO_UPDATE, defaultBuildInfoRepo,
                deleteOldDefaultBuildInfoRepoChange.getOldRepoConfig()));
        repoConfigChanges.put(LOCAL, localRepoChanges);
    }

    private boolean isRepoConfigChangesContainsRepo(List<RepoConfigChange> localRepoChanges, String repoKey) {
        return localRepoChanges.stream()
                .filter(c -> !Objects.equals(c.getConfigAction(), REPO_DELETE))
                .anyMatch(c -> Objects.equals(c.getRepoKey(), repoKey));
    }

    private void assertBuildInfoCreated(List<RepoConfigChange> localRepoChanges, String repoKey) {
        if (localRepoChanges.stream().noneMatch(repoConfigChange -> repoKey.equals(repoConfigChange.getRepoKey()) &&
                repoConfigChange.getNewRepoConfig() != null &&
                RepoType.BuildInfo == repoConfigChange.getNewRepoConfig().getPackageType())) {
            throw new ConfigurationException("Can't save BuildInfo repository, all fallbacks names are taken.");
        }
    }
}
