package org.artifactory.ui.rest.resource.admin.configuration.repositories;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.rest.exception.AuthorizationRestException;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationConfigModel;
import org.artifactory.ui.rest.model.admin.configuration.repository.federation.FederationPriority;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.ui.rest.resource.admin.configuration.repositories.RepoResourceConstants.PATH_FEDERATED;

/**
 * @author Alexei Vainshtein
 */
@Component
@Path(PATH_FEDERATED)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Produces(MediaType.APPLICATION_JSON)
public class FederatedRepoUIResource extends BaseResource {

    @Autowired
    protected ConfigServiceFactory configServiceFactory;
    @Autowired
    protected SecurityService securityService;

    @POST
    @Path("validateMirror")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response validateMirror(FederationConfigModel federationConfigModel) {
        return runService(configServiceFactory.validateFederatedRepoMirrors(), federationConfigModel);
    }

    @POST
    @Path("findMirrorRepos")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response findMirrorRepos(FederationConfigModel federationConfigModel) {
        return runService(configServiceFactory.discoverMirrorRepos(), federationConfigModel);
    }

    @POST
    @Path("findMirrorBaseUrlAndRepos")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response findMirrorBaseUrlAndRepos(FederationConfigModel federationConfigModel) {
        return runService(configServiceFactory.discoverMirrorBaseUrlAndRepos(), federationConfigModel);
    }

    @POST
    @Path("migrate/{repoKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response migrate(@PathParam("repoKey") String repoKey) {
        return runService(configServiceFactory.migrateLocalToFederatedRepository(), repoKey);
    }

    @GET
    @Path("buildInfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivatedBuildInfo(@QueryParam("projectKey") String projectKey) {
        if (!securityService.isProjectAdmin(projectKey)) {
            throw new AuthorizationRestException();
        }
        return runService(configServiceFactory.getFederatedActivatedBuildInfo(), projectKey);
    }

    @PUT
    @Path("prioritization/{repoKey}")
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN})
    public Response updateFederationPriority(@PathParam("repoKey") String repoKey,
            @QueryParam("priorityLevel") String priorityLevel) {
        return runService(configServiceFactory.updateFederationPriority(),
                new FederationPriority(repoKey, priorityLevel));
    }
}
