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
import org.artifactory.addon.AddonsManager;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.entitlement.EntitlementType;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.util.AolUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

/**
 * @author Fred Simon
 */
@Component
public class RedirectConfigInterceptor implements RepoConfigChangeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RedirectConfigInterceptor.class);
    private final AddonsManager addonsManager;
    private final CentralConfigService configService;

    @Autowired
    public RedirectConfigInterceptor(AddonsManager addonsManager,
            CentralConfigService configService) {
        this.addonsManager = addonsManager;
        this.configService = configService;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        List<LocalRepoConfig> newLocalRepos =
                getNewRepoConfigsByType(mutableRepoConfigChanges, LOCAL, LocalRepoConfig.class);
        List<FederatedRepoConfig> newFederatedRepos =
                getNewRepoConfigsByType(mutableRepoConfigChanges, FEDERATED, FederatedRepoConfig.class);
        List<RemoteRepoConfig> newRemoteRepos =
                getNewRepoConfigsByType(mutableRepoConfigChanges, REMOTE, RemoteRepoConfig.class);

        handleDownloadRedirectRepos(newLocalRepos, newFederatedRepos, newRemoteRepos);
        handleCdnRedirectRepos(newLocalRepos, newFederatedRepos, newRemoteRepos);
    }

    private void handleCdnRedirectRepos(List<LocalRepoConfig> newLocalRepos,
            List<FederatedRepoConfig> newFederatedRepos, List<RemoteRepoConfig> newRemoteRepos) {
        List<LocalRepoConfig> localCdnRepoConfigs = newLocalRepos.stream()
                .filter(repo -> repo.getRepoTypeConfig().isCdnRedirect())
                .collect(Collectors.toList());
        List<FederatedRepoConfig> federatedCdnRepoConfigs = newFederatedRepos.stream()
                .filter(repo -> repo.getRepoTypeConfig().isCdnRedirect())
                .collect(Collectors.toList());
        List<RemoteRepoConfig> remoteCdnRepoConfigs = newRemoteRepos.stream()
                .filter(repo -> repo.getRepoTypeConfig().isCdnRedirect())
                .collect(Collectors.toList());
        // CDN redirect enabled
        if ((!localCdnRepoConfigs.isEmpty() || !federatedCdnRepoConfigs.isEmpty() ||
                !remoteCdnRepoConfigs.isEmpty()) && !AolUtils.isAol()) {
            // CDN redirect is only relevant in AOL
            log.warn("CDN redirect feature is only enabled on AOL!");
            if (!localCdnRepoConfigs.isEmpty()) {
                localCdnRepoConfigs.forEach(repo -> repo.getRepoTypeConfig().setCdnRedirectRepoConfig(null));
            }
            if (!federatedCdnRepoConfigs.isEmpty()) {
                federatedCdnRepoConfigs.forEach(repo -> repo.getRepoTypeConfig().setCdnRedirectRepoConfig(null));
            }
            if (!remoteCdnRepoConfigs.isEmpty()) {
                remoteCdnRepoConfigs.forEach(repo -> repo.getRepoTypeConfig().setCdnRedirectRepoConfig(null));
            }
        }
    }

    @Nonnull
    private void handleDownloadRedirectRepos(List<LocalRepoConfig> newLocalRepos,
            List<FederatedRepoConfig> newFederatedRepos, List<RemoteRepoConfig> newRemoteRepos) {
        List<LocalRepoConfig> localRedirectRepoConfigs = newLocalRepos.stream()
                .filter(repo -> repo.getRepoTypeConfig().isDownloadRedirect())
                .collect(Collectors.toList());
        List<FederatedRepoConfig> federatedRedirectRepoConfigs = newFederatedRepos.stream()
                .filter(repo -> repo.getRepoTypeConfig().isDownloadRedirect())
                .collect(Collectors.toList());
        List<RemoteRepoConfig> remoteRedirectRepoConfigs = newRemoteRepos.stream()
                .filter(repo -> repo.getRepoTypeConfig().isDownloadRedirect())
                .collect(Collectors.toList());

        // Redirect enabled
        if (configService.isContextCreated() &&
                !(addonsManager.isEntitled(EntitlementType.BINARY_PROVIDER_DOWNLOAD_REDIRECT_MIX))) {
            if (!localRedirectRepoConfigs.isEmpty() || !remoteRedirectRepoConfigs.isEmpty() ||
                    !federatedRedirectRepoConfigs.isEmpty()) {
                log.warn("Redirect feature is only enabled with Enterprise plus or Edge license!");
            }
            // set off redirect
            if (!localRedirectRepoConfigs.isEmpty()) {
                localRedirectRepoConfigs.forEach(repo -> repo.getRepoTypeConfig().setDownloadRedirectConfig(null));
            }
            if (!federatedRedirectRepoConfigs.isEmpty()) {
                federatedRedirectRepoConfigs.forEach(repo -> repo.getRepoTypeConfig().setDownloadRedirectConfig(null));
            }
            if (!remoteRedirectRepoConfigs.isEmpty()) {
                remoteRedirectRepoConfigs.forEach(repo -> repo.getRepoTypeConfig().setDownloadRedirectConfig(null));
            }
        }
    }

    @Nonnull
    private <T> List<T> getNewRepoConfigsByType(Map<RepoDetailsType, List<RepoConfigChange>> repoConfigChanges,
            RepoDetailsType type, Class<T> configType) {
        List<RepoConfigChange> repoConfigChangesOfType = repoConfigChanges.getOrDefault(type, List.of());
        return repoConfigChangesOfType.stream()
                .filter(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                        REPO_UPDATE.equals(repoConfigChange.getConfigAction()))
                .map(repoConfigChange -> configType.cast(repoConfigChange.getNewRepoConfig()))
                .collect(Collectors.toList());
    }
}
