package org.artifactory.rest.resource.unifiedevent;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.exception.http.BadRequestException;
import org.artifactory.rest.resource.unifiedevent.dto.ConsumerRegistrationRequest;
import org.artifactory.unifiedevent.constants.SupportedConsumers;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.artifactory.unifiedevent.registry.Consumer;
import org.artifactory.unifiedevent.registry.RegistrationService;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;

import static org.artifactory.rest.resource.unifiedevent.RegistrationResource.PATH_ROOT;

@Component
@Path(PATH_ROOT)
@RolesAllowed({AuthorizationService.ROLE_ADMIN})
@AllArgsConstructor
public class RegistrationResource {
    public static final String PATH_ROOT = "/v1/unifiedevent";
    protected RegistrationService registrationService;

    @POST
    @Path("/registration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setRegistration(ConsumerRegistrationRequest registrationRequest) {
        // Input validation
        if (registrationRequest.name().equals(SupportedConsumers.WORKER_CLIENT)) {
            throw new BadRequestException("Cannot override WorkerClient consumer");
        }
        registrationService.registerConsumer(registrationRequest.name(), registrationRequest.url(), true,
                registrationRequest.event());
        return Response.ok().entity("Successfully updated consumer registration").build();
    }

    @PUT
    @Path("/registration")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateRegistration(ConsumerRegistrationRequest registrationRequest) {
        // Input validation
        if (registrationRequest.name().equals(SupportedConsumers.WORKER_CLIENT)) {
            throw new BadRequestException("Cannot override WorkerClient consumer");
        }
        registrationService.registerConsumer(registrationRequest.name(), registrationRequest.url(), false,
                registrationRequest.event());
        return Response.ok().entity("Successfully updated consumer registration").build();
    }

    @DELETE
    @Path("/registration/{event}/{consumer}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRegistration(@PathParam("event") SupportedUnifiedEvents event,
            @PathParam("consumer") SupportedConsumers consumerName) {
        if (consumerName.equals(SupportedConsumers.WORKER_CLIENT)) {
            throw new BadRequestException("Cannot delete WorkerClient consumer");
        }
        registrationService.unregisterConsumer(event, consumerName);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @GET
    @Path("/registration/{event}/{consumer}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRegistration(@PathParam("event") SupportedUnifiedEvents event,
            @PathParam("consumer") SupportedConsumers consumerName) {
        Consumer consumer = registrationService.getConsumerRegistration(event, consumerName);
        return Response.ok().entity(consumer).build();
    }
}
