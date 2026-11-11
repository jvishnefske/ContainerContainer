package org.artifactory.rest.resource.system;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.throtlling.generic.rrl.model.IRateLimiterConfig;
import org.artifactory.throtlling.generic.rrl.service.IRequestRateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.artifactory.util.AolUtils.assertDashboardUser;

/**
 * @author Omri Cohen
 */
@Component
@Path(SystemRestConstants.PATH_RRL_V2)
@RolesAllowed(AuthorizationService.ROLE_ADMIN)
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
@Slf4j
public class RequestRateLimiterResourceV2 {
    static final String RL_UPDATED_SUCCESSFULLY = "Request rate limiter configuration was updated successfully";
    static final String RL_DELETED_SUCCESSFULLY = "Request rate limiter configuration was deleted successfully";

    private final IRequestRateLimiterService rrlService;

    @Autowired
    RequestRateLimiterResourceV2(IRequestRateLimiterService rrlService) {
        this.rrlService = rrlService;
    }

    @GET
    @Path("config")
    @Produces(APPLICATION_JSON)
    public Response getConfig() {
        try {
            assertDashboardUser();
            return Response.ok().entity(rrlService.getConfig()).build();
        } catch (Exception e) {
            return buildBadResponse(e);
        }
    }

    @POST
    @Path("config")
    @Consumes(APPLICATION_JSON)
    public Response postConfig(IRateLimiterConfig rlConfig) {
        try {
            assertDashboardUser();
            rrlService.updateConfig((rlConfig));
            return Response.ok().entity(RL_UPDATED_SUCCESSFULLY).build();
        } catch (Exception e) {
            return buildBadResponse(e);
        }
    }

    @DELETE
    @Path("config")
    public Response deleteConfig() {
        try {
            assertDashboardUser();
            rrlService.deleteConfig();
            return Response.ok().entity(RL_DELETED_SUCCESSFULLY).build();
        } catch (Exception e) {
            return buildBadResponse(e);
        }
    }

    private static Response buildBadResponse(Exception e) {
        log.info("Failed to get rate limiter configuration: ", e);
        return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
    }

}
