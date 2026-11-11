package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.artifactory.exception.http.TooManyRequestsException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

/**
 * Intercepts and maps {@link TooManyRequestsException} exceptions thrown by the REST API.
 *
 * @author Andrey Tashlitsky
 */
@Component
@Provider
public class TooManyRequestsExceptionMapper implements ExceptionMapper<TooManyRequestsException> {

    @Override
    public Response toResponse(TooManyRequestsException exception) {
        ErrorResponse errorResponse =
                new ErrorResponse(Response.Status.TOO_MANY_REQUESTS.getStatusCode(), exception.getMessage());
        return Response.status(TOO_MANY_REQUESTS).type(MediaType.APPLICATION_JSON).entity(errorResponse).build();
    }
}
