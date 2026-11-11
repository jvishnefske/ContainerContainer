package org.artifactory.ui.rest.resource.admin.configuration.trashcan;

import org.artifactory.api.security.AuthorizationService;
import org.artifactory.rest.common.resource.BaseResource;
import org.artifactory.ui.rest.service.admin.configuration.ConfigServiceFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("trashcan")
@Component
public class TrashcanConfigResource extends BaseResource {

    private ConfigServiceFactory factory;

    public TrashcanConfigResource(ConfigServiceFactory factory) {
        this.factory = factory;
    }

    @GET
    @Path("disableStrategy")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed({AuthorizationService.ROLE_ADMIN, AuthorizationService.ROLE_USER})
    public Response disableStrategy() {
        return runService(factory.getDisableTrashcanStrategy());
    }

}
