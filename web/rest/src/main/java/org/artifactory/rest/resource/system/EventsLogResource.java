package org.artifactory.rest.resource.system;

import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.event.EventsLogCleanUpService;
import org.artifactory.rest.ResponseModel;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@Component
@Path("events/log")
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class EventsLogResource {

    @Autowired
    EventsLogCleanUpService eventsLogCleanUpService;

    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_JSON})
    @Path("cleanup")
    public Response startEventsCleanup() throws JobExecutionException {
        eventsLogCleanUpService.cleanup();
        ResponseModel responseModel = new ResponseModel(200, "Started events log cleanup.");
        return Response.ok(responseModel).build();
    }
}
