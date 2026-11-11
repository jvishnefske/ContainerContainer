package org.artifactory.rest.common.exception.mapper;

import org.artifactory.exception.InvalidOperationException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

/**
 * Exception mapper for handling InvalidOperationException and returning a JSON error response.
 *
 * @author prasannasudharsanr
 */
@Component
@Provider
public class InvalidOperationExceptionMapper implements ExceptionMapper<InvalidOperationException> {

    /**
     * Converts an InvalidOperationException to a Response object.
     *
     * @param exception the InvalidOperationException to be converted.
     * @return a Response object with status 400 and a JSON error message.
     */
    @Override
    public Response toResponse(InvalidOperationException exception) {
        ErrorResponse errorResponse = new ErrorResponse(
                BAD_REQUEST.getStatusCode(),
                exception.getMessage()
        );
        return Response
                .status(BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}
