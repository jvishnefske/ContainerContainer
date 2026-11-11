package org.artifactory.rest.resource.repositories;


import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.rest.RestAddon;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.artifactory.api.rest.constant.RepositoriesRestConstants.BATCH_PATH_ROOT;

/**
 * A resource to manage repository batch operations
 *
 * @author William Arustamyan
 */
@Component
@Path(BATCH_PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class RepositoriesBatchResource {

    @Autowired
    private AddonsManager addonsManager;

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Response createRepositories(List<Map<String, Object>> repoConfigs,
            @Context HttpHeaders requestHeaders) {
        return addonsManager.addonByType(RestAddon.class)
                .createRepositories(repoConfigs, requestHeaders.getMediaType());
    }

    @GET
    @Produces(APPLICATION_JSON)
    public Response getRepositoriesByKeys(@QueryParam("names") List<String> repoKeys,
            @Context HttpHeaders requestHeaders) {
        return addonsManager.addonByType(RestAddon.class)
                .getRepositoryConfigurations(repoKeys, requestHeaders.getMediaType());
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Response updateRepositories(List<Map<String, Object>> repoConfigs, @Context HttpHeaders requestHeaders) {
        return addonsManager.addonByType(RestAddon.class)
                .updateRepositories(repoConfigs, requestHeaders.getMediaType());
    }

    @DELETE
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response deleteRepositories(LinkedHashSet<String> repoKeys) {
        RestAddon restAddon = addonsManager.addonByType(RestAddon.class);
        return restAddon.deleteRepositories(repoKeys);
    }
}
