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
import org.artifactory.exception.http.ForbiddenException;
import org.artifactory.repo.interceptor.modle.RemoteRepoOfflineTriggersConfig;
import org.artifactory.service.RemoteRepoOfflineGuardService;
import org.springframework.stereotype.Component;

/**
 * @author Hussein Alshqerat
 */
@Component
@Path(SystemRestConstants.PATH_ROOT + "/remoteRepoOfflineTriggers")
@AllArgsConstructor
public class RemoteRepoOfflineTriggersResource {

    private final AolService aolService;
    private final RemoteRepoOfflineGuardService offlineGuardService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        if (!aolService.isDashboardUser()) {
            throw new ForbiddenException("Only dashboard users can view remote repo offline triggers");
        }
        return Response.ok(offlineGuardService.getRemoteRepoOfflineGuardTriggersConfig()).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(RemoteRepoOfflineTriggersConfig triggers) {
        if (!aolService.isDashboardUser()) {
            throw new ForbiddenException("Only dashboard users can modify remote repo offline triggers");
        }
        offlineGuardService.updateTriggers(triggers);
        return Response.ok(offlineGuardService.getRemoteRepoOfflineGuardTriggersConfig()).build();
    }
}
