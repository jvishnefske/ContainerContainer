package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.artifactory.exception.http.ContentTooLargeException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static org.apache.http.HttpStatus.SC_REQUEST_TOO_LONG;

/**
 * Intercepts and maps {@link ContentTooLargeException} exceptions thrown by the REST API.
 */
@Component
@Provider
public class ContentTooLargeExceptionMapper implements ExceptionMapper<ContentTooLargeException> {

    @Override
    public Response toResponse(ContentTooLargeException exception) {
        ErrorResponse errorResponse = new ErrorResponse(SC_REQUEST_TOO_LONG, exception.getMessage());
        return Response.status(SC_REQUEST_TOO_LONG)
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}


