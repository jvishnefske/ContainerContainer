package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.access.client.AccessClientHttpException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Intercepts and maps exceptions thrown by the Access http client.
 *
 * @author Amir Avidan
 */

@Component
@Provider
public class AccessClientHttpExceptionMapper implements ExceptionMapper<AccessClientHttpException> {

    @Override
    public Response toResponse(AccessClientHttpException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getStatusCode(), exception.getMessage());
        return Response.status(exception.getStatusCode()).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
