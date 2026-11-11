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

import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.model.LocalRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.ph.PackageSpecification;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.ProjectsUtils;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.packagetype.DockerApiVersion;
import org.artifactory.repo.packagetype.configuration.DebianConfiguration;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.AvailableRepositories;
import org.artifactory.ui.utils.RequestUtils;
import org.artifactory.util.DebianPackageTypeConfigMapper;
import org.artifactory.util.DockerPackageTypeConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Boolean.parseBoolean;
import static org.artifactory.util.HuggingFaceMLPackageTypeConfigMapper.IS_ML_REPO_LAYOUT;


/**
 * Retrieves the available repositories in the Virtual repository creation process
 *
 * @author Aviad Shikloshi
 */
@Component("allAvailableRepositories")
@NoArgsConstructor
public class GetAvailableRepositories implements RestService {

    @Autowired
    private RepositoryConfigService repositoryConfigService;
    @Autowired
    private ProjectsService projectsService;
    @Autowired
    private SecurityService securityService;
    @Setter(onMethod_ = {@Autowired})
    private PackageSpecification packageSpecification;


    @Override
    public void execute(ArtifactoryRestRequest request, RestResponse response) {
        String type = request.getQueryParamByKey("type");
        if (StringUtils.isBlank(type)) {
            response.responseCode(HttpStatus.SC_BAD_REQUEST).error("Request must specify package type.");
            return;
        }
        Predicate<RepoConfig> filter = getFilter(request, type);
        String projectKey = RequestUtils.getProjectKeyFromRequest(request);
        List<String> localRepos = availableRepos(repositoryConfigService.getLocalRepoConfigsExcludingInfoRepos()
                        .stream().map(RepoConfig.class::cast).toList(),
                filter, projectKey);
        List<String> remoteRepos =
                availableRepos(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.REMOTE),
                        filter, projectKey);
        List<String> federatedRepos =
                availableRepos(repositoryConfigService.getRepoConfigsByType(RepoDetailsType.FEDERATED), filter,
                        projectKey);
        String currentRepoKey = RequestUtils.getRepoKeyFromRequest(request);
        List<String> virtualRepos = availableVirtualRepos(currentRepoKey, filter, projectKey);
        AvailableRepositories allRepos =
                new AvailableRepositories(localRepos, federatedRepos, remoteRepos, virtualRepos);
        response.iModel(allRepos);
    }

    protected Predicate<RepoConfig> getFilter(ArtifactoryRestRequest request, String type) {
        return repo -> filterByType(RepoType.valueOf(type), repo);
    }

    private boolean filterByType(RepoType type, RepoConfig repo) {
        boolean isDocker = type.equals(RepoType.Docker) && repo.getPackageType().equals(RepoType.Docker);
        boolean isDebian = type.equals(RepoType.Debian) && repo.getPackageType().equals(RepoType.Debian);
        boolean isHuggingFaceML =
                type.equals(RepoType.HuggingFaceML) && repo.getPackageType().equals(RepoType.HuggingFaceML);
        boolean isLocal = repo instanceof LocalRepoConfig;
        if (isDocker) {
            if (isLocal) {
                DockerApiVersion dockerApiVersion = DockerPackageTypeConfigMapper.INSTANCE
                        .map(repo.getPackageTypeConfig())
                        .getDockerApiVersion();
                return DockerApiVersion.V2.equals(dockerApiVersion);
            }
        } else if (isDebian) {
            if (isLocal) {
                Map<String, String> packageTypeConfig = repo.getPackageTypeConfig();
                DebianConfiguration debianConfiguration = DebianPackageTypeConfigMapper.INSTANCE.map(packageTypeConfig);
                return !debianConfiguration.isDebianTrivialLayout();
            }
        } else if (isHuggingFaceML) {
            Map<String, String> packageTypeConfig = repo.getPackageTypeConfig();
            return parseBoolean(packageTypeConfig.get(IS_ML_REPO_LAYOUT));
        }
        return packageSpecification.isVirtualCanContainRepoWithType(type,repo.getPackageType());
    }

    /**
     * List all available repositories that has the same package type as the new repository
     *
     * @param repoConfigs list of existing repository keys
     * @param filter      The filter predicate
     * @return repositories keys
     */
    private List<String> availableRepos(Collection<RepoConfig> repoConfigs, Predicate<RepoConfig> filter,
            String project) {
        Function<String, Boolean> filterProjectAdminRepositories =
                createFilterProjectAdminRepositoriesFunction(securityService.isAdmin(), project);
        return repoConfigs.stream()
                .filter(filter)
                .map(RepoConfig::getKey)
                .filter(filterProjectAdminRepositories::apply)
                .collect(Collectors.toList());
    }

    /**
     * Collect all available virtual repositories keys, excluding the current repo key (in edit mode)
     *
     * @param repoKey the requesting current repo key
     * @param filter  The filter predicate
     * @return list of available keys
     */
    private List<String> availableVirtualRepos(String repoKey, Predicate<RepoConfig> filter, String projectKey) {
        Function<String, Boolean> filterProjectAdminRepositories =
                createFilterProjectAdminRepositoriesFunction(securityService.isAdmin(), projectKey);
        return repositoryConfigService.getRepoConfigsByType(RepoDetailsType.VIRTUAL).stream()
                .filter(filter)
                .map(RepoConfig::getKey)
                .filter(currentKey -> !StringUtils.equals(repoKey, currentKey)) //exclude current to avoid cycles
                .filter(filterProjectAdminRepositories::apply)
                .toList();

    }

    @NonNull
    private Function<String, Boolean> createFilterProjectAdminRepositoriesFunction(boolean isAdmin,
            String projectKey) {
        Function<String, Boolean> containsFunction;
        if (isAdmin && ProjectsUtils.isDefaultProject(projectKey)) {
            containsFunction = repo -> true;
        } else if (StringUtils.isNotEmpty(projectKey)) {
            Set<String> repositories = projectsService.getProjectRepositoriesKeys(projectKey, false);
            containsFunction = repositories::contains;
        } else {
            containsFunction = repo -> true;
        }
        return containsFunction;
    }
}