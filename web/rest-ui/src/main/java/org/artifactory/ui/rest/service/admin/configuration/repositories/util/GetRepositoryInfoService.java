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

package org.artifactory.ui.rest.service.admin.configuration.repositories.util;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepositoryInfo;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepositoryInfoListFactory;
import org.artifactory.ui.utils.RequestUtils;
import org.jfrog.access.client.project.ProjectResourceEntity;
import org.jfrog.access.client.project.ProjectResourceStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.artifactory.repo.RepoDetailsType.FEDERATED_REPO;
import static org.artifactory.repo.RepoDetailsType.LOCAL_REPO;
import static org.artifactory.repo.RepoDetailsType.REMOTE_REPO;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL_REPO;
import static org.artifactory.ui.utils.RequestUtils.isFedAttrOptIn;

/**
 * @author Aviad Shikloshi
 */
@Slf4j
@Component
@AllArgsConstructor
@NoArgsConstructor
public class GetRepositoryInfoService implements RestService<Boolean> {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private CentralConfigService configService;
    @Autowired
    private ProjectsService projectsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PackageSpecification packageSpecification;
    @Autowired
    private RepositoryConfigService repositoryConfigService;

    @Override
    public void execute(ArtifactoryRestRequest<Boolean> request, RestResponse response) {
        String repoType = request.getPathParamByKey("repoType");
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        boolean fedAttrOptIn = isFedAttrOptIn(request);
        if (fedAttrOptIn && FEDERATED_REPO.equals(repoType)) {
            response.error("Federated repo type cannot be used in this case")
                    .responseCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }
        boolean includeShippingOrBillingLogsRepos =
                Boolean.parseBoolean(request.getQueryParamByKey("withServiceGeneratedRepos"))
                        && securityService.isAdmin();
        boolean excludeReleaseBundlesV2Repos = Boolean.TRUE.equals(request.getImodel());
        Function<RepositoryInfo, Boolean> filterProjectAdminRepositories =
                createFilterProjectAdminRepositoriesFunction(projectKey);
        List<RepositoryInfo> repositoryInfoListInfo =
                RepositoryInfoListFactory
                        .createRepositoryInfo(repoType, includeShippingOrBillingLogsRepos,
                                excludeReleaseBundlesV2Repos,
                                configService, repositoryService,
                                repositoryConfigService, packageSpecification).stream()
                        .filter(filterProjectAdminRepositories::apply)
                        .collect(Collectors.toList());
        addProjectRelatedData(projectKey, repositoryInfoListInfo);
        if (fedAttrOptIn) {
            applyFederationAttribute(repoType, repositoryInfoListInfo);
        }
        response.iModelList(repositoryInfoListInfo);
    }

    /**
     * When the caller opted into the federation attribute, mark local/remote/virtual repos as federated only when
     * they have at least one federation member in addition to the (implicit) self member. A self member is added
     * when missing so the count is consistent across repo types, without duplicating an existing self member.
     * The {@code isFederated()} evaluation itself is intentionally left untouched.
     */
    private void applyFederationAttribute(String repoType, List<RepositoryInfo> repositoryInfoListInfo) {
        if (!LOCAL_REPO.equals(repoType) && !REMOTE_REPO.equals(repoType) && !VIRTUAL_REPO.equals(repoType)) {
            return;
        }
        for (RepositoryInfo repositoryInfo : repositoryInfoListInfo) {
            RepoConfig repoConfig = repositoryService.getRepoConfigByKey(repositoryInfo.getRepoKey());
            if (repoConfig == null) {
                continue;
            }
            repositoryInfo.setFederated(repoConfig.isFederated());
        }
    }

    private void addProjectRelatedData(String projectKey, List<RepositoryInfo> repositoryInfoListInfo) {
        boolean ignoreCache = ConstantValues.uiAdminRepoInfoIgnoreProjectsCache.getBoolean();
        Set<String> repoKeys =
                repositoryInfoListInfo.stream().map(RepositoryInfo::getRepoKey).collect(Collectors.toSet());
        Map<String, String> repoKeyToProjectMap = projectsService.getRepoKeyToProjectMap(repoKeys, ignoreCache);
        if (ProjectsUtils.isDefaultProject(projectKey) || ProjectsUtils.isUnassignedContext(projectKey)) {
            Map<String, Map<String, ProjectResourceEntity>> projectsResources =
                    projectsService.getAllProjectsRepositoriesMap(ignoreCache);
            repositoryInfoListInfo.forEach(repositoryInfo -> {
                String repoProjectKey =
                        repoKeyToProjectMap.getOrDefault(repositoryInfo.getRepoKey(), ProjectsService.DEFAULT_PROJECT);
                ProjectResourceEntity resourceEntity =
                        projectsResources.getOrDefault(repoProjectKey, Map.of()).get(repositoryInfo.getRepoKey());
                setProjectsData(repoProjectKey, repositoryInfo, resourceEntity);
            });
        } else {
            Map<String, ProjectResourceEntity> projectResources =
                    projectsService.getProjectRepositoriesMap(projectKey, ignoreCache);
            repositoryInfoListInfo.forEach(repositoryInfo -> {
                String repoProjectKey =
                        repoKeyToProjectMap.getOrDefault(repositoryInfo.getRepoKey(), ProjectsService.DEFAULT_PROJECT);
                ProjectResourceEntity resourceEntity = projectResources.get(repositoryInfo.getRepoKey());
                setProjectsData(repoProjectKey, repositoryInfo, resourceEntity);
            });
        }
    }

    private void setProjectsData(@NonNull String projectKey, @NonNull RepositoryInfo repositoryInfo,
            @Nullable ProjectResourceEntity resourceEntity) {
        repositoryInfo.setProjectKey(projectKey);
        repositoryInfo.setProjectName(projectsService.getProjectDisplayName(projectKey));
        log.debug("Setting projectKey {} and projectName {} to repo {}", projectKey, repositoryInfo.getProjectName(),
                repositoryInfo.getRepoKey());
        if (resourceEntity == null) {
            return;
        }
        if (resourceEntity.getResourceStatus().equals(ProjectResourceStatus.SHARED_WITH_ALL) ||
                resourceEntity.getResourceStatus().equals(ProjectResourceStatus.SHARED_WITH_ALL_TARGET)) {
            repositoryInfo.setShareWithAllProjects(true);
        }
        repositoryInfo.setEnvironments(ofNullable(resourceEntity.getEnvironments()).orElseGet(Set::of));
        repositoryInfo.setSharedWithProjects(resourceEntity.getSharedWithProjects());
        repositoryInfo.setSharedReadOnly(resourceEntity.isSharedReadOnly());
        repositoryInfo.setTarget(resourceEntity.getResourceStatus().isTarget());
        log.debug(
                "Setting shared data for repo {}. ShareWithAll: {}, SharedWithProjects: {}, SharedReadOnly: {}, " +
                        "Target: {}",
                repositoryInfo.getRepoKey(), repositoryInfo.isShareWithAllProjects(),
                repositoryInfo.getSharedWithProjects(), repositoryInfo.isSharedReadOnly(), repositoryInfo.isTarget());
    }

    @NonNull
    private Function<RepositoryInfo, Boolean> createFilterProjectAdminRepositoriesFunction(
            String projectKey) {
        Function<RepositoryInfo, Boolean> containsFunction;
        if (ProjectsUtils.isDefaultProject(projectKey)) {
            boolean isAdmin = securityService.isAdmin();
            containsFunction = (repositoryInfo) -> isAdmin;
        } else if (ProjectsUtils.isUnassignedContext(projectKey)) {
            boolean isAdmin = securityService.isAdmin();
            Set<String> repositories = getUnassignedRepositories();
            log.debug("Limiting the repoList to contain only unassigned repositories within the list: {}",
                    repositories);
            containsFunction = repositoryInfo -> isAdmin && repositories.contains(repositoryInfo.getRepoKey());
        } else if (securityService.isProjectAdmin(projectKey)) {
            Set<String> repositories = projectsService.getProjectRepositoriesKeys(projectKey, true);
            log.debug("Limiting the repoList to contain only repositories from project {} within the list: {}",
                    projectKey, repositories);
            containsFunction = repositoryInfo -> repositories.contains(repositoryInfo.getRepoKey());
        } else {
            containsFunction = (repositoryInfo) -> false;
        }
        return containsFunction;
    }

    private Set<String> getUnassignedRepositories() {
        Map<String, String> repoToProjectMap =
                projectsService.getRepoKeyToProjectMap(repositoryService.getAllRepoKeys(),
                        ConstantValues.uiAdminRepoInfoIgnoreProjectsCache.getBoolean());
        return repoToProjectMap.entrySet()
                .stream()
                .filter(entry -> ProjectsUtils.isDefaultProject(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
