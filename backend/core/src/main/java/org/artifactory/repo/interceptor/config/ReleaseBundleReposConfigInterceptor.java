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

import org.artifactory.addon.AddonsManager;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.state.ArtifactoryServerState;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.artifactory.util.UnsupportedByLicenseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.interceptor.config.SupportBundleReposConfigInterceptor.getRepoConfigChangesByAction;
import static org.artifactory.repo.system.SystemRepos.RELEASE_BUNDLE_REPO_KEY;

/**
 * @author Fred Simon
 */
@Component
public class ReleaseBundleReposConfigInterceptor implements RepoConfigChangeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ReleaseBundleReposConfigInterceptor.class);
    private static final String ERR_RESERVED_REPO_NAME = "%s is reserved repo name.";
    private final AddonsManager addonsManager;
    private ArtifactoryServersCommonService artifactoryServers;

    @Autowired
    public ReleaseBundleReposConfigInterceptor(AddonsManager addonsManager) {
        this.addonsManager = addonsManager;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        validateReleaseBundleReposOnlyOnEntPlus(mutableRepoConfigChanges);
        handleReleaseBundleRepos(mutableRepoConfigChanges);
    }

    /**
     * Validate that the new descriptor doesn't contain repository named 'release-bundles' which is not from
     * {@link RepoType#ReleaseBundles} type.
     */
    private void handleReleaseBundleRepos(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        List<RepoConfigChange> repoConfigCreateChanges = getRepoConfigChangesByAction(repoConfigChanges, REPO_CREATE);
        List<RepoConfigChange> repoConfigUpdateChanges = getRepoConfigChangesByAction(repoConfigChanges, REPO_UPDATE);
        if (isForbiddenReleaseBundleReposExist(repoConfigCreateChanges)) {
            throw new BadRequestException(format(ERR_RESERVED_REPO_NAME, RELEASE_BUNDLE_REPO_KEY));
        }
        if (isForbiddenReleaseBundleReposExist(repoConfigUpdateChanges)) {
            log.warn("{} is internal repo name, please change any repo with the same key",
                    RELEASE_BUNDLE_REPO_KEY);
        }
    }

    /**
     * Release-Bundles repository is allowed only on artifactory with Enterprise Plus license.
     */
    private void validateReleaseBundleReposOnlyOnEntPlus(
            Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges) {
        if (!addonsManager.isEntitled(EntitlementType.RELEASE_BUNDLES_WRITE_MIX)) {
            boolean releaseBundleRepoWasCreated =
                    repoConfigChanges.getOrDefault(RepoDetailsType.RELEASE_BUNDLE, List.of()).stream()
                            .anyMatch(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()));
            if (releaseBundleRepoWasCreated) {
                ArtifactoryServerState serverState = artifactoryServers.getCurrentMember().getServerState();
                if (serverState == ArtifactoryServerState.STARTING ||
                        serverState == ArtifactoryServerState.CONVERTING) {
                    log.debug(
                            "Allowing descriptor change of Release Bundles repositories since artifactory is starting");
                } else {
                    throw new UnsupportedByLicenseException(
                            "Release Bundles repositories are only supported with Artifactory Enterprise Plus license");
                }
            }
            boolean releaseBundleRepoWasUpdated =
                    repoConfigChanges.getOrDefault(RepoDetailsType.RELEASE_BUNDLE, List.of()).stream()
                            .anyMatch(repoConfigChange -> REPO_UPDATE.equals(repoConfigChange.getConfigAction()));
            if (releaseBundleRepoWasUpdated) {
                log.warn(
                        "Release Bundles repositories found, but are only supported with Artifactory Enterprise Plus " +
                                "license");
            }
        }
    }

    @Autowired
    public void setArtifactoryServers(
            ArtifactoryServersCommonService artifactoryServers) {
        this.artifactoryServers = artifactoryServers;
    }

    private boolean isForbiddenReleaseBundleReposExist(List<RepoConfigChange> repoConfigChanges) {
        return repoConfigChanges.stream()
                .anyMatch(repoConfigChange -> RELEASE_BUNDLE_REPO_KEY.equals(repoConfigChange.getRepoKey()));
    }
}
