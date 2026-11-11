package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.download.RateLimiterException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;

/**
 * Intercepts and maps {@link RateLimiterException} to 429 Too Many Requests.
 * Without this mapper, uncaught RateLimiterExceptions fall through to
 * {@link GlobalExceptionMapper} and are incorrectly returned as 500.
 *
 * @author Amir Avidan
 */
@Component
@Provider
public class RateLimiterExceptionMapper implements ExceptionMapper<RateLimiterException> {

    @Override
    public Response toResponse(RateLimiterException exception) {
        ErrorResponse errorResponse = new ErrorResponse(TOO_MANY_REQUESTS.getStatusCode(), exception.getMessage());
        return Response.status(TOO_MANY_REQUESTS)
                .type(APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}
