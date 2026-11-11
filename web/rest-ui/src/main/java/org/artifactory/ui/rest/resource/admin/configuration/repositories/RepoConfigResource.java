/*
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

package org.artifactory.ui.rest.resource.admin.configuration.repositories;

import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Nonnull;
import jakarta.annotation.security.RolesAllowed;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Providers;
import org.artifactory.api.config.RepositoryConfigService;
import org.artifactory.api.repo.RepoActionType;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.common.ConstantValues;
import org.artifactory.exception.http.NotFoundException;
import org.artifactory.metrics.cuj.RepoCreationMetricsWrapper;
import org.artifactory.model.RepoConfig;
import org.artifactory.projects.ProjectsService;
import org.artifactory.projects.repo.ProjectRepoPermissionsHelper;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepoPathFactory;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.common.service.RestService;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.model.admin.configuration.repository.RepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.info.RepoConfigRequest;
import org.artifactory.ui.rest.model.admin.configuration.repository.local.LocalRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.MetadataCachePeriodExclusionRequestModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.remote.RemoteRepositoryConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualAllowedReposModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.virtual.VirtualRepositoryConfigModel;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.ui.rest.resource.admin.configuration.repositories.RepoResourceConstants.PATH_REPOSITORIES;

/**
 * @author Aviad Shikloshi
 * @author Dan Feldman
 */
@Component
@Path(PATH_REPOSITORIES)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Produces(MediaType.APPLICATION_JSON)
public class RepoConfigResource extends BaseResource {

    /**
     * When {@code true}, {@code .../local/info} omits Release Bundle V2 storage repos (assign-repo-to-project flows).
     */
    private static final String EXCLUDE_RBV2_REPOS_QUERY_PARAM = "excludeReleaseBundleV2";

    @Autowired
    protected ConfigServiceFactory configServiceFactory;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private ProjectsService projectsService;
    @Autowired
    private RepositoryConfigService repositoryConfigService;
    @Autowired
    private RepoCreationMetricsWrapper repoCreationMetricsWrapper;
    @Autowired
    private ProjectRepoPermissionsHelper projectRepoPermissionsHelper;
    @Context
    private Providers providers;

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateRepositoryConfig(RepositoryConfigModel repositoryConfigModel) {
        projectRepoPermissionsHelper.validateAnyProjectAdmin();
        RepoConfig repoConfig = fetchRepoConfig(repositoryConfigModel.getGeneral().getRepoKey());
        if (projectRepoPermissionsHelper.projAdminNotAuthorizedToUpdateRepo(repoConfig,
                repositoryConfigModel.getGeneral().getProjectKey())) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.updateRepositoryConfig(), repositoryConfigModel);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createRepository(RepositoryConfigModel repositoryConfigModel) {
        return repoCreationMetricsWrapper.createRepoAndCollectMetrics(
                repositoryConfigModel::getRepoType,
                () -> repositoryConfigModel.getTypeSpecific().getRepoType().getEffectiveType(),
                () -> {
                    String repoKey = repositoryConfigModel.getGeneral().getRepoKey();
                    String projectKey = repositoryConfigModel.getGeneral().getProjectKey();
                    if (!projectsService.canDoToProjectRepoResource(RepoActionType.CREATE, repoKey, projectKey,
                            RepoDetailsType.fromString(repositoryConfigModel.getRepoType()))) {
                        throw new AuthorizationRestException();
                    }
                    return runService(configServiceFactory.createRepositoryConfig(), repositoryConfigModel);
                },
                providers);
    }

    @GET
    @Path("repoKeyLengthLimits")
    public Response getRepoKeyLengthLimits() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getRepoKeyLengthLimits());
    }

    @GET
    @Path("validatereponame")
    public Response validateRepoName() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.validateRepoName());
    }

    @GET
    @Path("{repoType: local|federated|remote|virtual|distribution|releaseBundles}/{repoKey}")
    public Response getRepositoryConfigByType(@PathParam("repoType") String repoType,
            @PathParam("repoKey") String repoKey) {
        // single-repo config model carries secret-bearing fields (e.g. remote upstream credentials),
        // so this endpoint must enforce the full-config read boundary
        if (!projectsService.canReadFullRepoConfig(repoKey, RepoDetailsType.fromString(repoType))) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getRepositoryConfig());
    }

    @GET
    @Path("availablechoices")
    public Response getAvailableRepositoryFieldChoices() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getAvailableRepositoryFieldChoices());
    }

    @GET
    @Path("localavailablerepositories")
    public Response getLocalAvailableRepositories() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getLocalAvailableRepositories());
    }

    @GET
    @Path("defaultvalues")
    public Response getRepoConfigDefaultValues() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getDefaultRepositoryValues());
    }

    @GET
    @Path("remoteurls/{repoType}")
    public Response getRemoteUrlsByType() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getRemoteUrlsByType());
    }

    @GET
    @Path("remoteUrlMap")
    public Response getRemoteReposUrlMapping() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getRemoteReposUrlMapping());
    }

    @GET
    @Path("{repoType: local|federated|remote|virtual|distribution}/info")
    public Response getRepositoriesInfo(@QueryParam(ProjectsService.PROJECT_KEY) String projectKey,
            @QueryParam(EXCLUDE_RBV2_REPOS_QUERY_PARAM) Boolean excludeReleaseBundleV2) {
        if (!securityService.isProjectAdmin(projectKey)) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getRepositoriesInfo(), Boolean.TRUE.equals(excludeReleaseBundleV2));
    }

    @GET
    @Path("availablerepositories")
    public Response getAvailableRepositories(@QueryParam(ProjectsService.PROJECT_KEY) String projectKey) {
        if (!securityService.isProjectAdmin(projectKey)) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getAvailableRepositories());
    }

    @GET
    @Path("indexeravailablerepositories")
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response getIndexerAvailableRepositories() {
        return runService(configServiceFactory.getIndexerAvailableRepositories());
    }

    @GET
    @Path("indexingStats/{packageType}")
    public Response getPackageIndexingPercentiles() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getPackageIndexingPercentiles());
    }

    @POST
    @Path("resolvedrepositories")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getResolvedRepositories(VirtualRepositoryConfigModel virtualConfigModel) {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getResolvedRepositories(), virtualConfigModel);
    }

    @POST
    @Path("virtualallowedrepositories")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getVirtualAllowedRepos(VirtualAllowedReposModel virtualAllowedReposModel) {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getVirtualAllowedRepos(), virtualAllowedReposModel);
    }

    @POST
    @Path("testremote")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remoteRepositoryUrlTest(RemoteRepositoryConfigModel remoteRepositoryModel) {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.remoteRepositoryTestUrl(), remoteRepositoryModel);
    }

    @POST
    @Path("discoversmartrepocapabilities")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response discoverSmartRepoCapabilities(RemoteRepositoryConfigModel remoteRepositoryModel) {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.discoverSmartRepoCapabilities(), remoteRepositoryModel);
    }

    @POST
    @Path("validatemetadatacacheperiod")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response validateMetadataCachePeriod(MetadataCachePeriodExclusionRequestModel request) {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.validateMetadataCachePeriod(), request);
    }

    @POST
    @Path("testlocalreplication")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testLocalReplicationConfig(LocalRepositoryConfigModel localRepoModel) {
        if (!securityService.isProjectAdmin(RepoPathFactory.create(localRepoModel.getGeneral().getRepoKey()))) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.testLocalReplication(), localRepoModel);
    }

    @POST
    @Path("testremotereplication")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testRemoteReplicationConfig(RemoteRepositoryConfigModel remoteRepositoryModel) {
        if (!securityService.isProjectAdmin(RepoPathFactory.create(remoteRepositoryModel.getGeneral().getRepoKey()))) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.testRemoteReplication(), remoteRepositoryModel);
    }

    @POST
    @Path("exeucteremotereplication")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeRemoteReplicationNow() {
        return runService(configServiceFactory.executeImmediateReplication());
    }

    @DELETE
    @Path("{repoKey}/delete")
    public Response deleteRepository(@PathParam("repoKey") String repoKey) {
        if (isRemoveRepositoryFlowV2Enabled()) {
            return runService(configServiceFactory.deleteRepositoryConfig(), repoKey);
        }
        return deprecatedDeleteRepository(repoKey);
    }

    /**
     * @deprecated for removal soon
     */
    @Deprecated(since = "7.104.x")
    private Response deprecatedDeleteRepository(String repoKey) {
        projectRepoPermissionsHelper.validateAnyProjectAdmin();
        RepoConfig repoConfig = fetchRepoConfig(repoKey);
        if (!projectsService.canDoToProjectRepoResource(RepoActionType.DELETE, repoKey, repoConfig.getRepoType())) {
            throw new AuthorizationRestException();
        }
        return runServiceAndWait(
                configServiceFactory.deleteRepositoryConfig(),
                "Delete Repository operation in progress"
        );
    }

    @POST
    @Path("executeall")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeAllLocalReplications() {
        return runService(configServiceFactory.executeAllLocalReplications());
    }

    @POST
    @Path("executereplicationnow")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response executeLocalReplication(LocalRepositoryConfigModel localRepoModel) {
        if (!securityService.isProjectAdmin(RepoPathFactory.create(localRepoModel.getGeneral().getRepoKey()))) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.executeLocalReplication(), localRepoModel);
    }

    @POST
    @Path("{repoType: local|remote|virtual|distribution}/reorderrepositories")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response reorderRepositories(List<String> newOrderList) {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.reorderRepositories(), newOrderList);
    }

    @GET
    @Path("isjcenterconfigured")
    @RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
    public Response isJcenterConfigured() {
        return runService(configServiceFactory.isJcenterConfigured());
    }

    @POST
    @Path("createdefaultjcenterrepo")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response createDefaultJcenterRepo() {
        return runService(configServiceFactory.createDefaultJcenterRepo());
    }

    @GET
    @Path("getdockerstatus")
    public Response docker() {
        if (!securityService.isAnyProjectAdmin()) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getDockerRepo());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/info")
    public Response getRepositoryConfigByFilters(RepoConfigRequest request) {
        return runService(configServiceFactory.getRepoConfigsByFilters(), request);
    }

    @Override
    @VisibleForTesting
    protected <Y> Response runService(RestService<Y> service, Y model) {
        return super.runService(service, model);
    }

    private boolean isRemoveRepositoryFlowV2Enabled() {
        return ConstantValues.removeRepositoryFlowV2Enabled.getBoolean();
    }

    @Nonnull
    private RepoConfig fetchRepoConfig(String repoKey) {
        RepoConfig repoConfig = repositoryConfigService.getRepoConfigByKey(repoKey);
        if (repoConfig == null) {
            throw new NotFoundException("Repository '" + repoKey + "' not found");
        }
        return repoConfig;
    }
}