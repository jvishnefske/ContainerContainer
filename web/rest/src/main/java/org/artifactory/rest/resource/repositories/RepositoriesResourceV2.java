package org.artifactory.rest.resource.repositories;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.config.CentralConfigService;
import org.artifactory.api.repo.RepositoryService;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_FEDERATED_REPOSITORY_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_LOCAL_REPOSITORY_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_REMOTE_REPOSITORY_CONFIG;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.MT_VIRTUAL_REPOSITORY_CONFIGURATION;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.PATH_ROOT_V2;

/**
 * A resource to return specific type repository configuration.
 *
 * @author Inbar Tal
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path(PATH_ROOT_V2)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class RepositoriesResourceV2 {

    @Context
    private HttpHeaders requestHeaders;

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private CentralConfigService centralConfigService;

    @Autowired
    private RepositoryService repositoryService;

    private static final String REPO_KEY = "repoKey";

    @GET
    @Path("{repoKey: .+}")
    @Produces({MT_LOCAL_REPOSITORY_CONFIGURATION, MT_REMOTE_REPOSITORY_CONFIG, MT_FEDERATED_REPOSITORY_CONFIGURATION,
            MT_VIRTUAL_REPOSITORY_CONFIGURATION, MediaType.APPLICATION_JSON})
    public Response getRepoConfig(@PathParam(REPO_KEY) String repoKey) {
        MediaType mediaType = requestHeaders.getMediaType();
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.getRepositoryConfigurationV2(repoKey, mediaType);
    }

    @Path("{repoKey: .+}/keyPairs")
    public RepositoryKeyPairsResource handle(@PathParam(REPO_KEY) String repoKey) {
        return new RepositoryKeyPairsResource(centralConfigService, repositoryService, repoKey);
    }

}