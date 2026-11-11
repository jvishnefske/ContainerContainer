package org.artifactory.rest.resource.system;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.throtlling.common.model.RateLimiterConfig;
import org.artifactory.throtlling.common.service.IRateLimiterConfigService;

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
 * @author Andrey Tashlitsky
 */
@Slf4j
public abstract class RateLimiterResource {

    static final String RL_UPDATED_SUCCESSFULLY = " configuration was updated successfully";
    static final String RL_DELETED_SUCCESSFULLY = " configuration was deleted successfully";
    private final IRateLimiterConfigService rlService;

    RateLimiterResource(IRateLimiterConfigService rlService) {
        this.rlService = rlService;
    }

    abstract String getName();

    @GET
    @Path("config")
    @Produces(APPLICATION_JSON)
    public Response getConfig() {
        try {
            assertDashboardUser();
            return Response.ok().entity(rlService.getConfig()).build();
        } catch (Exception e) {
            return buildBadResponse(e);
        }
    }

    @POST
    @Path("config")
    @Consumes(APPLICATION_JSON)
    public Response postConfig(RateLimiterConfig rlConfig) {
        try {
            assertDashboardUser();
            rlService.updateConfig(rlConfig);
            return Response.ok().entity(getName() + RL_UPDATED_SUCCESSFULLY).build();
        } catch (Exception e) {
            return buildBadResponse(e);
        }
    }

    @DELETE
    @Path("config")
    public Response deleteConfig() {
        try {
            assertDashboardUser();
            rlService.deleteConfig();
            return Response.ok().entity(getName() + RL_DELETED_SUCCESSFULLY).build();
        } catch (Exception e) {
            return buildBadResponse(e);
        }
    }

    private static Response buildBadResponse(Exception e) {
        log.debug("Failed to get rate limiter configuration: ", e);
        return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
    }
}
