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

package org.artifactory.ui.rest.service.admin.configuration.repositories;

import com.google.common.collect.Lists;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederatedRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.releasebundles.ReleaseBundlesRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.repositories.util.builder.RepoConfigModelBuilder;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.RELEASE_BUNDLE_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;
import static org.artifactory.repo.system.SystemRepos.isSupportBundle;
import static org.artifactory.ui.utils.RequestUtils.isFedAttrOptIn;
import static org.artifactory.util.RepoPathUtils.isTrash;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@Component
public class GetRepositoryConfigService implements RestService<Void> {

    private ProjectsService projectsService;
    private RepositoryService repositoryService;
    private RepoConfigModelBuilder configBuilder;

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        String repoType = request.getPathParamByKey("repoType");
        String repoKey = request.getPathParamByKey("repoKey");
        boolean fedAttrOptIn = isFedAttrOptIn(request);
        if (fedAttrOptIn && FEDERATED_REPO.equals(repoType)) {
            response.error("Federated repo type cannot be used with such settings")
                    .responseCode(HttpStatus.SC_BAD_REQUEST);
            return;
        }
        if (StringUtils.isBlank(repoKey)) {
            respondWithRepoList(repoType, fedAttrOptIn, response);
        } else {
            respondWithSingleRepo(repoType, repoKey, fedAttrOptIn, response);
        }
    }

    private void respondWithRepoList(String repoType, boolean fedAttrOptIn,
            RestResponse<RepositoryConfigModel<?, ?, ?>> response) {
        List<RepositoryConfigModel<?, ?, ?>> models = getAllRepositoriesConfigs(repoType, fedAttrOptIn).stream()
                .filter(rc -> !LOCAL_REPO.equals(repoType) || !repositoryService.isInfoOrHiddenRepo(rc)
                        || rc.isFederated())
                .map(rc -> createModelFromRepoConfig(repoType, rc, fedAttrOptIn))
                .collect(Collectors.toList());
        response.iModelList(models).responseCode(HttpStatus.SC_OK);
    }

    private void respondWithSingleRepo(String repoType, String repoKey, boolean fedAttrOptIn,
            RestResponse<RepositoryConfigModel<?, ?, ?>> response) {
        if (isTrash(repoKey) || isSupportBundle(repoKey)) {
            repoNotFound(repoKey, response);
            return;
        }
        RepoConfig repoConfigByKey = repositoryService.getRepoConfigByKey(repoKey);
        if (repoConfigByKey == null || repoConfigByKey.isCache()) {
            repoNotFound(repoKey, response);
            return;
        }
        boolean shouldHideInfoRepo = !fedAttrOptIn
                && LOCAL_REPO.equalsIgnoreCase(repoType)
                && repositoryService.isInfoOrHiddenRepo(repoConfigByKey)
                && !repoConfigByKey.isFederated();
        boolean requestedFederatedAsLocal = fedAttrOptIn
                && LOCAL_REPO.equalsIgnoreCase(repoType)
                && RepoDetailsType.FEDERATED.equals(repoConfigByKey.getRepoType());
        boolean isTypeMismatch = repoType != null
                && !repoConfigByKey.getRepoType().equals(RepoDetailsType.fromString(repoType))
                && !requestedFederatedAsLocal;
        if (shouldHideInfoRepo || isTypeMismatch) {
            repoNotFound(repoKey, response);
            return;
        }
        response.iModel(createModelFromRepoConfig(repoType, repoConfigByKey, fedAttrOptIn));
    }

    private void repoNotFound(String repoKey, RestResponse<?> response) {
        response.error("Repository '" + repoKey + "' does not exist.").responseCode(HttpStatus.SC_NOT_FOUND);
    }

    private RepositoryConfigModel<?, ?, ?> createModelFromRepoConfig(
            String repoType, RepoConfig repoConfig, boolean fedAttrOptIn) {
        boolean isFedAsLocal = fedAttrOptIn && RepoDetailsType.FEDERATED.equals(repoConfig.getRepoType());
        String effectiveType = isFedAsLocal ? LOCAL_REPO : repoType;
        RepositoryConfigModel<?, ?, ?> model =
                getRepoDescriptorByType(effectiveType)
                        .fromRepoConfig(configBuilder, repoConfig);
        if (fedAttrOptIn && repoConfig.getBaseConfig().isFederated()) {
            FederationConfigModel federationModel = configBuilder.createFederationConfigFromBaseConfig(repoConfig);
            if (federationModel != null) {
                model.setFederation(federationModel);
            }
        }
        String projectKey = projectsService.getProjectKeyOfRepository(repoConfig.getKey());
        ProjectResourceEntity projectRepoEntity =
                Optional.ofNullable(projectsService.getProjectRepositoryEntity(projectKey, repoConfig.getKey()))
                        .orElseGet(() -> ProjectResourceEntity.builder().resourceName(repoConfig.getKey()).build());
        model.getGeneral().setProjectKey(projectKey);
        model.getGeneral().setEnvironments(Set.copyOf(projectRepoEntity.getEnvironments()));
        return model;
    }

    private List<? extends RepoConfig> getAllRepositoriesConfigs(String repoType, boolean fedAttrOptIn) {
        return switch (repoType) {
            case LOCAL_REPO -> fedAttrOptIn ? repositoryService.getLocalAndFederatedRepoConfigsIncludingInfoRepos() :
                    repositoryService.getLocalRepoConfigsExcludingInfoRepos();
            case REMOTE_REPO -> repositoryService.getRemoteRepoConfigs();
            case FEDERATED_REPO -> repositoryService.getAllFederatedRepoConfigsWithSystemRepo();
            case VIRTUAL_REPO -> repositoryService.getVirtualRepoConfigs();
            case RELEASE_BUNDLE_REPO -> repositoryService.getReleaseBundleRepoConfigs();
            default -> Lists.newArrayList();
        };
    }

    private RepositoryConfigModel<?, ?, ?> getRepoDescriptorByType(String repoType) {
        return switch (repoType) {
            case REMOTE_REPO -> new RemoteRepositoryConfigModel();
            case VIRTUAL_REPO -> new VirtualRepositoryConfigModel();
            case RELEASE_BUNDLE_REPO -> new ReleaseBundlesRepositoryConfigModel();
            case FEDERATED_REPO -> new FederatedRepositoryConfigModel();
            default -> new LocalRepositoryConfigModel();
        };
    }

    @Autowired
    public void setProjectsService(@NonNull ProjectsService projectsService) {
        this.projectsService = projectsService;
    }

    @Autowired
    public void setRepositoryService(@NonNull RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Autowired
    public void setConfigBuilder(@NonNull RepoConfigModelBuilder configBuilder) {
        this.configBuilder = configBuilder;
    }
}
