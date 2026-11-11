package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.concurrent.LockingFailedException;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * Intercepts and maps {@link LockingFailedException} exceptions thrown by REST API.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
@Provider
public class LockingFailedExceptionMapper implements ExceptionMapper<LockingFailedException> {

    @Override
    public Response toResponse(LockingFailedException exception) {
        ErrorResponse errorResponse = new ErrorResponse(INTERNAL_SERVER_ERROR.getStatusCode(),
                INTERNAL_SERVER_ERROR.getReasonPhrase()); // do not disclose the cause in the response
        log.warn(exception.getMessage(), exception);
        return Response.status(INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
