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

package org.artifactory.ui.rest.service.admin.configuration.layouts;

import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoLayout;
import org.artifactory.model.RepoConfig;
import org.artifactory.model.common.RepoPathImpl;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.layouts.RepositoryAssociationsModel;
import org.artifactory.ui.rest.model.admin.configuration.layouts.RepositoryLayoutModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Lior Hasson
 */
@Component
public class GetLayoutInfoService implements RestService<RepositoryLayoutModel> {
    private CentralConfigService centralConfigService;
    private RepositoryConfigService repositoryConfigService;
    private SecurityService securityService;

    @Autowired
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Autowired
    public void setCentralConfigService(CentralConfigService centralConfigService) {
        this.centralConfigService = centralConfigService;
    }

    @Autowired
    public void setRepositoryConfigService(RepositoryConfigService repositoryConfigService) {
        this.repositoryConfigService = repositoryConfigService;
    }

    @Override
    public void execute(ArtifactoryRestRequest<RepositoryLayoutModel> artifactoryRequest,
            RestResponse artifactoryResponse) {
        List<RepoLayout> repoLayouts = centralConfigService.getDescriptor().getRepoLayouts();
        RepositoryLayoutModel layoutModel = getRepositoryLayoutModel(artifactoryRequest, repoLayouts);
        createAssociations(layoutModel);

        artifactoryResponse.iModel(layoutModel);
    }

    private RepositoryLayoutModel getRepositoryLayoutModel(ArtifactoryRestRequest<RepositoryLayoutModel> request,
            List<RepoLayout> repoLayouts) {
        String layoutKey = request.getPathParamByKey("layoutKey");
        Optional<RepositoryLayoutModel> layoutModel = repoLayouts.stream()
                .filter(repoLayout -> Objects.equals(repoLayout.getName(), layoutKey))
                .map(RepositoryLayoutModel::new)
                .findFirst();

        return layoutModel.get();
    }

    void createAssociations(RepositoryLayoutModel layoutModel) {
        RepositoryAssociationsModel ra = new RepositoryAssociationsModel();

        List<RepoConfig> remoteRepos = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE);
        ra.setRemoteRepositories(filterReposWithReadPermission(filterReposWithLayout(remoteRepos, layoutModel)));

        List<RepoConfig> virtualRepos = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL);
        ra.setVirtualRepositories(filterReposWithReadPermission(filterReposWithLayout(virtualRepos, layoutModel)));

        List<RepoConfig> localConfigs = repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED);
        localConfigs.addAll(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos());
        ra.setLocalRepositories(filterReposWithReadPermission(filterReposWithLayout(localConfigs, layoutModel)));

        layoutModel.setRepositoryAssociations(ra);
    }

    private List<String> filterReposWithLayout(List<RepoConfig> repoConfigs, RepoLayout layout) {
        return repoConfigs.stream()
                .filter(config -> config.getBaseConfig().getRepoLayoutRef() != null)
                .filter(config -> Objects.equals(config.getBaseConfig().getRepoLayoutRef(), layout.getName()))
                .sorted(Comparator.comparing(RepoConfig::getKey))
                .map(RepoConfig::getKey)
                .collect(Collectors.toList());
    }

    private List<String> filterReposWithReadPermission(List<String> repoKeys) {
        if (securityService.isAdmin()) {
            return repoKeys;
        }
        return repoKeys.stream()
                .filter(repoKey -> securityService.isProjectAdmin(new RepoPathImpl(repoKey, "")))
                .toList();
    }
}
