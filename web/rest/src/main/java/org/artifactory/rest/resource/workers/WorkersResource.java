package org.artifactory.rest.resource.workers;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.worker.WorkerEventsModel;
import org.artifactory.addon.worker.WorkersAddon;
import org.artifactory.api.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.artifactory.api.rest.constant.WorkersRestConstants.PATH_ROOT;

/**
 * @author Bhanu Reddy
 */
@Component
@Path(PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_USER, AuthorizationService.ROLE_ADMIN})
public class WorkersResource {

    @Autowired
    private AddonsManager addonsManager;

    @GET
    @Path("/events")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWorkers() {
        WorkersAddon workersAddon = addonsManager.addonByType(WorkersAddon.class);
        List<WorkerEventsModel> workerEvents =  workersAddon.getAvailableEvents();
        return new WorkersEventsResponseBuilder().buildResponse(workerEvents);
    }
}