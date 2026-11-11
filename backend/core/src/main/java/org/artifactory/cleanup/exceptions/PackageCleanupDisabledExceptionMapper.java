package org.artifactory.cleanup.exceptions;

import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Provider
public class PackageCleanupDisabledExceptionMapper implements ExceptionMapper<PackageCleanupDisabledException> {

    @Override
    public Response toResponse(PackageCleanupDisabledException exception) {
        ErrorResponse errorResponse = new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}