package org.artifactory.rest.resource.federation;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.api.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
/**
 * @author Tamar Rosen
 */
@Getter
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Path("v2/federation/status")
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Slf4j
@AllArgsConstructor
public class FederatedStatusResourceV2 {

    @Autowired
    FederatedStatusResource resource;

    @GET
    @Path("repo/{repoKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFederatedRepoStatus(@PathParam("repoKey") String repoKey) {
        return resource.getFederatedRepoStatus(repoKey, true);
    }

    @GET
    @Path("repo/{repoKey}/{federatedKey}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFederatedRepoStatusByFedKey(
            @PathParam("repoKey") String repoKey, @PathParam("federatedKey") String federatedKey) {
        return resource.getFederatedRepoStatusByFedKey(repoKey, federatedKey, true);
    }
}

