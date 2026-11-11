package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.common.JsonParsingException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * An exception mapper to process a custom JsonParsingException
 *
 * @author Oleksandr Legenkiy
 */
@Component
@Provider
public class JsonParsingExceptionMapper implements ExceptionMapper<JsonParsingException> {

    @Override
    public Response toResponse(JsonParsingException exception) {
        ErrorResponse errorResponse = new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
