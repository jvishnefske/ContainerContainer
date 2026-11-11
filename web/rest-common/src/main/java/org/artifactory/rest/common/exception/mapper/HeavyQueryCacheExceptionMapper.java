package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.storage.hqc.exception.HeavyQueryCacheException;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.REQUEST_TIMEOUT;

/**
 * Intercepts and maps {@link HeavyQueryCacheException} exceptions thrown by the "Heavy Query Cache" mechanism.
 *
 * @author Andrey Tashlitsky
 */
@Component
@Provider
public class HeavyQueryCacheExceptionMapper implements ExceptionMapper<HeavyQueryCacheException> {

    @Override
    public Response toResponse(HeavyQueryCacheException exception) {
        ErrorResponse errorResponse = new ErrorResponse(REQUEST_TIMEOUT.getStatusCode(), exception.getMessage());
        return Response.status(REQUEST_TIMEOUT)
                .type(APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}
