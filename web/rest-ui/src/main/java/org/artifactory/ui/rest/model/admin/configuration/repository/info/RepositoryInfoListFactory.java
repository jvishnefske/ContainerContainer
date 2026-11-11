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

package org.artifactory.ui.rest.model.admin.configuration.repository.info;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.descriptor.repo.VirtualRepoDescriptor;
import org.artifactory.model.FederatedRepoConfig;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.util.bundles.BundleConfigurationUtil;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.artifactory.repo.RepoDetailsType.DISTRIBUTION_REPO;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;

/**
 * @author Aviad Shikloshi
 */
@Slf4j
public class RepositoryInfoListFactory {

    public static List<RepositoryInfo> createRepositoryInfo(String repoType, boolean includeShippingOrBillingLogsRepos,
            boolean excludeReleaseBundlesV2StorageRepos,
            CentralConfigService configService,
            RepositoryService repositoryService, RepositoryConfigService repositoryConfigService,
            PackageSpecification packageSpecification) {
        List<RepositoryInfo> repoInfo;
        switch (repoType) {
            case LOCAL_REPO:
                Stream<LocalConfig> localConfigStream = repositoryConfigService
                        .getLocalRepoConfigsExcludingInfoRepos(includeShippingOrBillingLogsRepos)
                        .stream();
                if (excludeReleaseBundlesV2StorageRepos) {
                    log.trace("Excluding release bundle v2 repos from the output");
                    localConfigStream = localConfigStream.filter(repoConfig ->
                            !BundleConfigurationUtil.isReleaseBundlesV2RepoKey(repoConfig.getKey()));
                }
                repoInfo = localConfigStream
                        .map(repoConfig -> new LocalRepositoryInfo(repoConfig, configService, packageSpecification))
                        .collect(Collectors.toList());
                break;
            case FEDERATED_REPO:
                List<FederatedRepoConfig> fedRepoConfigs = repositoryConfigService.getRepoConfigsByType(
                                RepoDetailsType.FEDERATED).stream()
                        .map(repoConfig -> (FederatedRepoConfig) repoConfig)
                        .toList();
                repoInfo = fedRepoConfigs.stream()
                        .map(repoConfig -> new FederatedRepositoryInfo(repoConfig, configService.getDescriptor(),
                                packageSpecification))
                        .collect(Collectors.toList());
                break;
            case REMOTE_REPO:
                List<RemoteRepoConfig> remoteRepoConfigs = repositoryConfigService.getRepoConfigsByType(
                                RepoDetailsType.REMOTE).stream()
                        .map(repoConfig -> (RemoteRepoConfig) repoConfig)
                        .toList();
                repoInfo = remoteRepoConfigs.stream()
                        .map(repoConfig -> new RemoteRepositoryInfo(repoConfig, configService.getDescriptor(),
                                packageSpecification))
                        .collect(Collectors.toList());
                break;
            case VIRTUAL_REPO:
                List<VirtualRepoDescriptor> virtualRepoDescriptorList = repositoryService.getVirtualRepoDescriptors();
                repoInfo = virtualRepoDescriptorList.stream()
                        .map(repoDesc -> new VirtualRepositoryInfo(repoDesc, packageSpecification))
                        .collect(Collectors.toList());
                break;
            case DISTRIBUTION_REPO:
                repoInfo = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.RELEASE_BUNDLE).stream()
                        .map(ReleaseBundlesRepositoryInfo::new)
                        .collect(Collectors.toList());
                break;
            default:
                repoInfo = Lists.newArrayList();
        }
        return repoInfo;
    }

    private RepositoryInfoListFactory() {
    }

}
