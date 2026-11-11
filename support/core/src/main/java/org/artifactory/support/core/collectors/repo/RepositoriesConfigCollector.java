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

package org.artifactory.support.core.collectors.repo;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.util.CollectionUtils;
import org.jfrog.support.common.config.ConfigFilesConfiguration;
import org.jfrog.support.common.core.collectors.ConfigFilesCollector;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.artifactory.common.home.ArtifactoryHome.ARTIFACTORY_REPO_CONFIG_FILE;
import static org.artifactory.repo.RepoDetailsType.REMOTE;

/**
 * @author Tamar Rosen
 */

@Slf4j
public class RepositoriesConfigCollector extends ConfigFilesCollector {
    private final RepositoryConfigService repositoryConfigService;

    public RepositoriesConfigCollector(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    /**
     * Collects all repositories configuration
     *
     * @param configuration {@link ConfigFilesConfiguration}
     * @param tmpDir        output directory for produced content
     * @return operation result
     */
    @Override
    protected boolean doCollect(ConfigFilesConfiguration configuration, File tmpDir) {
        Collection<RepoConfig> allRepositoryConfigs = repositoryConfigService.getAllRepoConfigs();
        scrubSensitiveData(configuration, allRepositoryConfigs);
        if (!CollectionUtils.isNullOrEmpty(allRepositoryConfigs)) {
            log.debug("Starting to write repositories configuration to {}", tmpDir.getAbsolutePath());
            repositoryConfigService.writeRepoConfigsToFile(tmpDir, allRepositoryConfigs);
            log.info("Collection of {} was successfully accomplished", getContentName());
            return true;
        }
        return false;
    }

    private void scrubSensitiveData(ConfigFilesConfiguration configuration,
            Collection<RepoConfig> allRepositoryConfigs) {
        if (!configuration.isHideUserDetails() && !configuration.isScrubUserNames() && !configuration.isScrubIPs()) {
            return;
        }
        allRepositoryConfigs.stream()
                .filter(repoConfig -> REMOTE.equals(repoConfig.getRepoType()))
                .map(RemoteRepoConfig.class::cast)
                .forEach(remoteRepoConfig -> {
                    if (configuration.isHideUserDetails()) {
                        remoteRepoConfig.getRepoTypeConfig().setPassword(null);
                    }
                    if (configuration.isScrubUserNames()) {
                        remoteRepoConfig.getRepoTypeConfig().setUsername(null);
                    }
                    if (configuration.isScrubIPs()) {
                        remoteRepoConfig.getRepoTypeConfig().setUrl(null);
                    }
                });
    }

    @Override
    protected List<File> getConfigFiles() {
        return Collections.emptyList();
    }

    @Override
    protected List<String> getScrubStrings() {
        return Collections.emptyList();
    }

    /**
     * @return The filename to be used
     */
    @Override
    protected String getFileName() {
        return ARTIFACTORY_REPO_CONFIG_FILE;
    }
}
