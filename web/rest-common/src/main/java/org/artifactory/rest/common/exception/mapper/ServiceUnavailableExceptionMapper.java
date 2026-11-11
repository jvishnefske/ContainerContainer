package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.http.ServiceUnavailableException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

@Slf4j
@Provider
@Component
public class ServiceUnavailableExceptionMapper implements ExceptionMapper<ServiceUnavailableException> {

    @Override
    public Response toResponse(ServiceUnavailableException e) {
        log.error("Service unavailable error:", e);
        ErrorResponse errorResponse = new ErrorResponse(SERVICE_UNAVAILABLE.getStatusCode(), e.getMessage());
        return Response.status(SERVICE_UNAVAILABLE).type(APPLICATION_JSON).entity(errorResponse).build();
    }
}
