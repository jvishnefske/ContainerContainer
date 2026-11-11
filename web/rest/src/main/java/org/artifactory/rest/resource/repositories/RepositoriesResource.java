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

package org.artifactory.rest.resource.repositories;

import com.google.common.collect.Lists;
import jakarta.annotation.Nullable;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Providers;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.common.ConstantValues;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.RepositoriesRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.metrics.cuj.RepoCreationMetricsWrapper;
import org.artifactory.model.LocalConfig;
import org.artifactory.model.RemoteRepoConfig;
import org.artifactory.model.RepoConfig;
import org.artifactory.repo.RepoDetails;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepositoryConfiguration;
import org.artifactory.rest.common.util.RestUtils;
import org.artifactory.rest.filter.AllowedPermissionScope;
import org.jfrog.common.StringList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpHeaders.CACHE_CONTROL;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.CACHE_CONTROL_NO_STORE;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_FEDERATED_REPOSITORY_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_LOCAL_REPOSITORY_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_REMOTE_REPOSITORY_CONFIG;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_REPOSITORY_CONFIGURATIONS_LIST;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_REPOSITORY_DETAILS_LIST;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_VIRTUAL_REPOSITORY_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PARAM_PACKAGE_TYPE;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PARAM_REPO_TYPE;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PARAM_TYPE;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PATH_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PATH_CONFIGURATIONS;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PATH_ROOT;
import static org.artifactory.descriptor.repo.RepoType.Generic;
import static org.artifactory.projects.ProjectsService.PROJECT;
import static org.artifactory.projects.ProjectsService.PROJECT_KEY;
import static org.artifactory.repo.RepoDetailsType.FEDERATED;
import static org.artifactory.repo.RepoDetailsType.LOCAL;
import static org.artifactory.repo.RepoDetailsType.REMOTE;
import static org.artifactory.repo.RepoDetailsType.VIRTUAL;
import static org.artifactory.repo.RepoDetailsType.valueOf;
import static org.artifactory.security.permissionscope.PermissionScopeToken.ARTIFACT_ALL_READ;
import static org.artifactory.security.permissionscope.PermissionScopeToken.INTERNAL_MISSION_CONTROL_READ;
import static org.artifactory.security.permissionscope.PermissionScopeToken.SYSTEM_INFO_READ;
import static org.artifactory.util.RepositoriesUtils.addNonRemoteRepoConfigDetails;

/**
 * A resource to manage all repository related operations
 *
 * @author Noam Tenne
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class RepositoriesResource {

    public static final String REPO_PACKAGE_TYPE_CONFIG_KEY = "packageType";

    @Context
    HttpServletRequest httpRequest;

    @Context
    private HttpHeaders requestHeaders;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private RepoCreationMetricsWrapper repoCreationMetricsWrapper;

    @Context
    private Providers providers;

    /**
     * Repository configuration resource delegator
     *
     * @return RepoConfigurationResource
     */
    @Path("{repoKey}/" + RepositoriesRestConstants.PATH_CONFIGURATION)
    @Deprecated
    public RepoConfigurationResource getRepoConfigResource(@PathParam("repoKey") String repoKey) {
        return new RepoConfigurationResource(centralConfigService, repositoryService, repoKey);
    }

    /**
     * Get repository configuration depending on the repository's type
     *
     * @param repoKey The repo Key
     * @return The repository configuration JSON
     */
    @GET
    @Path("{repoKey: .+}")
    @Produces({MT_LOCAL_REPOSITORY_CONFIGURATION, MT_REMOTE_REPOSITORY_CONFIG, MT_VIRTUAL_REPOSITORY_CONFIGURATION,
            MT_FEDERATED_REPOSITORY_CONFIGURATION, APPLICATION_JSON})
    public Response getRepoConfig(@PathParam("repoKey") String repoKey) {
        MediaType mediaType = requestHeaders.getMediaType();
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.getRepositoryConfiguration(repoKey, mediaType);
    }

    @PUT
    @Consumes({MT_LOCAL_REPOSITORY_CONFIGURATION, MT_REMOTE_REPOSITORY_CONFIG, MT_VIRTUAL_REPOSITORY_CONFIGURATION,
            MT_FEDERATED_REPOSITORY_CONFIGURATION, APPLICATION_JSON})
    @Produces(TEXT_PLAIN)
    @Path("{repoKey: .+}")
    public Response createRepository(@PathParam("repoKey") String repoKey, Map<String, Object> repoConfigMap) {
        MediaType mediaType = requestHeaders.getMediaType();
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return repoCreationMetricsWrapper.createRepoAndCollectMetrics(
                () -> getConfigValue(RepositoryConfiguration.TYPE_KEY, repoConfigMap),
                () -> getConfigValue(REPO_PACKAGE_TYPE_CONFIG_KEY, repoConfigMap),
                () -> restAddon.createRepository(repoKey, repoConfigMap, mediaType),
                providers);
    }

    @POST
    @Consumes({MT_LOCAL_REPOSITORY_CONFIGURATION, MT_REMOTE_REPOSITORY_CONFIG, MT_VIRTUAL_REPOSITORY_CONFIGURATION,
            MT_FEDERATED_REPOSITORY_CONFIGURATION, APPLICATION_JSON})
    @Produces(TEXT_PLAIN)
    @Path("{repoKey: .+}")
    public Response updateRepository(@PathParam("repoKey") String repoKey,
            Map<String, Object> repositoryConfiguration) {
        MediaType mediaType = requestHeaders.getMediaType();
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.updateRepository(repoKey, repositoryConfiguration, mediaType);
    }

    /**
     * Returns a JSON list of repository details.
     * <p/>
     * NOTE: Used by CI integration to get a list of deployment repository targets.
     *
     * @param repoType   Name of repository type, as defined in {@link org.artifactory.repo.RepoDetailsType}. Can be
     *                   null
     * @param projectKey If supplied then we shall return only repositories mapped to this particular project
     * @return JSON repository details list. Will return details of defined type, if given. And will return details of
     * all types if not
     */
    @GET
    @AllowedPermissionScope(scopes = {SYSTEM_INFO_READ, INTERNAL_MISSION_CONTROL_READ, ARTIFACT_ALL_READ})
    @Produces({MT_REPOSITORY_DETAILS_LIST, APPLICATION_JSON})
    public Response getAllRepoDetails(@QueryParam(PARAM_TYPE) String repoType,
            @QueryParam(PARAM_PACKAGE_TYPE) String packageType,
            @QueryParam(PROJECT) String projectKey) {
        log.debug("Fetching all repositories of type {}, package {}, project {}", repoType, packageType, projectKey);
        List<RepoDetails> repoDetailsList = getRepoDetailsList(repoType, packageType, projectKey);
        return Response.ok().entity(repoDetailsList).header(CACHE_CONTROL, CACHE_CONTROL_NO_STORE).build();
    }

    /**
     * Retrieves all repository configurations
     * <p/>
     * @return JSON containing all repository configurations grouped by repository type
     */
    @GET
    @Path(PATH_CONFIGURATIONS)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    @Produces({MT_REPOSITORY_CONFIGURATIONS_LIST, APPLICATION_JSON})
    public Response getAllRepositoryConfigurations(@QueryParam(PARAM_PACKAGE_TYPE) StringList packageTypes,
            @QueryParam(PARAM_REPO_TYPE) StringList repoTypes) {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.getAllRepositoryConfigurations(packageTypes, repoTypes);
    }

    @GET
    @Path("existence")
    @Produces(MediaType.APPLICATION_JSON)
    public Response hasAnyRepositories(@QueryParam(PROJECT_KEY) String projectKey,
            @QueryParam(PARAM_TYPE) Set<String> types) {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.hasRepositoriesByProjectAndTypes(projectKey, types);
    }

    @DELETE
    @Produces(APPLICATION_JSON)
    @Path("{repoKey: .+}")
    public Response deleteRepository(@PathParam("repoKey") String repoKey) {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.deleteRepository(repoKey);
    }

    /**
     * Returns a list of repository details
     *
     * @param repoType Name of repository type, as defined in {@link org.artifactory.repo.RepoDetailsType}
     * @return List of repository details
     */
    private List<RepoDetails> getRepoDetailsList(@Nullable String repoType, @Nullable String packageType,
            @Nullable String projectKey) {
        RepoDetailsType selectedType = null;
        if (isNotBlank(repoType)) {
            try {
                selectedType = valueOf(repoType.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Lists.newArrayList();
            }
        }
        RepoType selectedPackageType = null;
        if (isNotBlank(packageType)) {
            selectedPackageType = RepoType.fromType(packageType);
            //RepoType.fromType(packageType) returns "Generic" when the input does not match the enum, and we
            //don't want to return all generic repos on user error.
            if (Generic.equals(selectedPackageType) && !Generic.name().equalsIgnoreCase(packageType)) {
                return Lists.newArrayList();
            }
        }
        return getReposList(selectedType, selectedPackageType, projectKey);
    }

    private List<RepoDetails> getReposList(@Nullable RepoDetailsType selectedType, @Nullable RepoType packageType,
            @Nullable String projectKey) {
        List<RepoDetails> detailsList = Lists.newArrayList();
        String contextUrl = RestUtils.getServletContextUrl(httpRequest);
        Function<String, Boolean> projectsFilter = repositoryService.createProjectReposFilter(projectKey);
        if (selectedType == null || LOCAL.equals(selectedType)) {
            List<LocalConfig> repoConfigs =
                    repositoryService.getLocalRepoConfigsExcludingInfoReposByProject(projectsFilter);
            List<LocalConfig> localRepoConfigsList = filterRepoConfigsByPackageType(repoConfigs, packageType);
            addNonRemoteRepoConfigDetails(contextUrl, detailsList, localRepoConfigsList, LOCAL, authorizationService);
        }
        if (selectedType == null || FEDERATED.equals(selectedType) || shouldIncludeFederatedAsLocal(selectedType)) {
            RepoDetailsType federatedType = shouldIncludeFederatedAsLocal(selectedType) ? LOCAL : FEDERATED;
            List<RepoConfig> federatedRepos = filterRepoConfigsByPackageType(
                    repositoryService.getRepoConfigsByProjectAndType(projectsFilter, Set.of(FEDERATED)),
                    packageType);
            addNonRemoteRepoConfigDetails(contextUrl, detailsList, federatedRepos, federatedType, authorizationService);
        }
        if (selectedType == null || REMOTE.equals(selectedType)) {
            addRemoteRepoDetails(detailsList, packageType, projectKey);
        }
        if (selectedType == null || VIRTUAL.equals(selectedType)) {
            List<RepoConfig> virtualRepoConfigs = filterRepoConfigsByPackageType(
                    repositoryService.getRepoConfigsByProjectAndType(projectsFilter, Set.of(VIRTUAL)),
                    packageType);
            addNonRemoteRepoConfigDetails(contextUrl, detailsList, virtualRepoConfigs, VIRTUAL, authorizationService);
        }
        detailsList.sort(Comparator.comparing(RepoDetails::getType).thenComparing(RepoDetails::getKey));
        return detailsList;
    }

    /**
     * Adds a list of remote repositories to the repo details list
     *
     * @param detailsList List that details should be appended to
     */
    private void addRemoteRepoDetails(List<RepoDetails> detailsList, RepoType packageType,
            @Nullable String projectKey) {
        Function<String, Boolean> projectFilter = repositoryService.createProjectReposFilter(projectKey);
        List<RepoConfig> remoteRepos = filterRepoConfigsByPackageType(
                repositoryService.getRepoConfigsByProjectAndType(projectFilter, Set.of(REMOTE)), packageType);

        for (RepoConfig repoConfig : remoteRepos) {
            String key = repoConfig.getKey();
            if (authorizationService.userHasPermissionsOnRepositoryRoot(key)) {
                String configUrl = null;
                RemoteRepoConfig remoteRepoConfig = (RemoteRepoConfig) repoConfig;
                if (remoteRepoConfig.getRepoTypeConfig().isShareConfiguration()) {
                    configUrl = getRepoConfigUrl(key);
                }
                detailsList.add(new RepoDetails(key, remoteRepoConfig.getBaseConfig().getDescription(), REMOTE,
                        remoteRepoConfig.getRepoTypeConfig().getUrl(), configUrl, remoteRepoConfig.getPackageType()));
            }
        }
    }

    private <T extends RepoConfig> List<T> filterRepoConfigsByPackageType(List<T> repos,
            @Nullable RepoType packageType) {
        //No type - return all requested repos not filtered by any type
        if (packageType == null) {
            return repos;
        }
        return repos.stream()
                .filter(repo -> packageType.equals(repo.getPackageType()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the repository configuration URL
     *
     * @param repoKey Key of repository to assemble URL for
     * @return Repository configuration URL
     */
    private String getRepoConfigUrl(String repoKey) {
        return RestUtils.getRestApiUrl(httpRequest) + "/" + PATH_ROOT + "/" + repoKey + "/" + PATH_CONFIGURATION;
    }

    private String getConfigValue(String key, Map<String, Object> repoConfigMap) {
        Object value = repoConfigMap.get(key);
        if (value != null) {
            return value.toString();
        }
        return null;
    }

    /**
     * Returns true if federated repos should be included as local repos in the listing.
     * This happens when:
     * - The selected type is LOCAL, AND
     * - Either grid is enabled OR federatedAsLocal feature flag is enabled
     */
    private boolean shouldIncludeFederatedAsLocal(RepoDetailsType selectedType) {
        return LOCAL.equals(selectedType) &&
                (ConstantValues.gridEnabled.getBoolean() || ConstantValues.federatedAsLocalEnabled.getBoolean());
    }
}
