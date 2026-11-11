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

import org.apache.commons.lang3.StringUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.webstart.ArtifactWebstartAddon;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.ConfigurationException;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.repo.RepoConfigChange;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.interceptor.RepoConfigChangeInterceptor;
import org.artifactory.request.UrlVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.artifactory.addon.AddonType.WEBSTART;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_CREATE;
import static org.artifactory.repo.RepoConfigChange.ConfigAction.REPO_UPDATE;

/**
 * @author Fred Simon
 */
@Component
public class RemoteReposConfigInterceptor implements RepoConfigChangeInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RemoteReposConfigInterceptor.class);
    private final UrlVerifier urlVerifier;
    private final AddonsManager addonsManager;


    @Autowired
    public RemoteReposConfigInterceptor(UrlVerifier urlVerifier, AddonsManager addonsManager) {
        this.urlVerifier = urlVerifier;
        this.addonsManager = addonsManager;
    }

    @Override
    public void onBeforeSave(Map<RepoDetailsType, List<RepoConfigChange>> mutableRepoConfigChanges) {
        List<RepoConfigChange> remoteRepoCreateOrUpdateChanges =
                mutableRepoConfigChanges.getOrDefault(RepoDetailsType.REMOTE, List.of()).stream()
                        .filter(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                                REPO_UPDATE.equals(repoConfigChange.getConfigAction()))
                        .collect(toList());
        List<RemoteRepoConfig> newRemoteRepos = remoteRepoCreateOrUpdateChanges.stream()
                .map(repoConfigChange -> (RemoteRepoConfig) repoConfigChange.getNewRepoConfig())
                .collect(toList());

        assertNoBlacklistedRemoteRepos(remoteRepoCreateOrUpdateChanges);
        assertNoMissingUrlInRemoteRepos(newRemoteRepos);
        cleanUnknownClientCertificate(newRemoteRepos);
    }

    private void assertNoBlacklistedRemoteRepos(List<RepoConfigChange> remoteRepoCreateOrUpdateChanges) {
        List<RepoConfigChange> blacklistedRemoteRepoChanges = remoteRepoCreateOrUpdateChanges.stream()
                .filter(repoConfigChange ->
                        ((RemoteRepoConfig) repoConfigChange.getNewRepoConfig()).getRepoTypeConfig().getUrl() != null)
                .filter(repoConfigChange -> isRemoteRepoBlacklisted(
                        ((RemoteRepoConfig) repoConfigChange.getNewRepoConfig()))).collect(toList());

        Map<String, String> blacklistedUrls = blacklistedRemoteRepoChanges.stream()
                .map(repoConfigChange -> (RemoteRepoConfig) repoConfigChange.getNewRepoConfig())
                .collect(toMap(RemoteRepoConfig::getKey, repo -> repo.getRepoTypeConfig().getUrl()));
        if (!blacklistedUrls.isEmpty()) {
            log.debug("Following repositories are Blacklisted: {}", blacklistedUrls);
            Set<String> newBlacklistedUrls = blacklistedRemoteRepoChanges.stream()
                    .filter(repoConfigChange -> REPO_CREATE.equals(repoConfigChange.getConfigAction()) ||
                            !isSameUrl((RemoteRepoConfig) repoConfigChange.getNewRepoConfig(),
                                    (RemoteRepoConfig) repoConfigChange.getOldRepoConfig()))
                    .map(repoConfigChange -> ((RemoteRepoConfig) repoConfigChange.getNewRepoConfig())
                            .getRepoTypeConfig().getUrl())
                    .collect(toSet());

            if (!newBlacklistedUrls.isEmpty()) {
                log.warn("New repo config changes contains blocked URLs. blacklisted URLs: {}", newBlacklistedUrls);
                throw new IllegalArgumentException("Found a remote repository(ies) containing blacklisted URLs");
            }
        }
    }

    private boolean isRemoteRepoBlacklisted(RemoteRepoConfig remoteRepoConfig) {
        return urlVerifier.isRemoteRepoBlocked(remoteRepoConfig.getRepoTypeConfig().getUrl(),
                remoteRepoConfig.getKey());
    }

    private boolean isSameUrl(RemoteRepoConfig newRemoteRepo, RemoteRepoConfig oldRemoteRepo) {
        return newRemoteRepo.getRepoTypeConfig().getUrl().equals(oldRemoteRepo.getRepoTypeConfig().getUrl());
    }

    private void assertNoMissingUrlInRemoteRepos(List<RemoteRepoConfig> newRemoteRepos) {
        if (ConstantValues.centralConfigRemoteRepoUrlValidation.getBoolean()) {
            List<String> missingUrls = newRemoteRepos.stream()
                    .filter(remoteRepoConfig -> remoteRepoConfig.getRepoTypeConfig().getUrl() == null)
                    .map(RemoteRepoConfig::getKey)
                    .collect(toList());
            if (!missingUrls.isEmpty()) {
                log.error("New repo config changes contains a remote repository(ies) with a missing URL: {}",
                        missingUrls);
                throw new ConfigurationException("Found a remote repository with a missing URL");
            }
        }
    }

    private void cleanUnknownClientCertificate(List<RemoteRepoConfig> newRemoteRepos) {
        newRemoteRepos.forEach(repo -> {
            String clientCert = repo.getRepoTypeConfig().getClientTlsCertificate();
            if (StringUtils.isNotBlank(clientCert) && addonsManager.isAddonSupported(WEBSTART)) {
                ArtifactWebstartAddon webstartAddon = addonsManager.addonByType(ArtifactWebstartAddon.class);
                List<String> certAliases = webstartAddon.getSslCertNames();
                if (certAliases != null && certAliases.stream().noneMatch(clientCert::equalsIgnoreCase)) {
                    repo.getRepoTypeConfig().setClientTlsCertificate(null);
                }
            }
        });
    }
}
