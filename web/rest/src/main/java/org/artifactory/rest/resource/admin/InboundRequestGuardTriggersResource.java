package org.artifactory.rest.resource.admin;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import org.artifactory.aol.AolService;
import org.artifactory.api.rest.constant.SystemRestConstants;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.request.guard.InboundRequestGuardService;
import org.artifactory.request.guard.model.InboundRequestGuardTriggersConfig;
import org.springframework.stereotype.Component;

/**
 * @author Hussein Alshqerat
 */
@Component
@Path(SystemRestConstants.PATH_ROOT + "/inboundRequestGuardTriggers")
@AllArgsConstructor
public class InboundRequestGuardTriggersResource {

    private final AolService aolService;
    private final InboundRequestGuardService guardService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        if (!aolService.isDashboardUser()) {
            throw new ForbiddenException("Only dashboard users can view inbound request guard triggers");
        }
        return Response.ok(guardService.getInboundRequestGuardTriggersConfig()).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(InboundRequestGuardTriggersConfig triggers) {
        if (!aolService.isDashboardUser()) {
            throw new ForbiddenException("Only dashboard users can modify inbound request guard triggers");
        }
        try {
            guardService.updateTriggers(triggers);
        } catch (IllegalArgumentException e) {
            // Surface validation failures (e.g. malformed IP/CIDR in ipAddresses) as 400 to the admin.
            throw new BadRequestException(e.getMessage(), e);
        }
        return Response.ok(guardService.getInboundRequestGuardTriggersConfig()).build();
    }
}
