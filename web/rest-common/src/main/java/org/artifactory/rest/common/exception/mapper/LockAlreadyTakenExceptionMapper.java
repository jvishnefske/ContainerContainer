package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.concurrent.LockAlreadyTakenException;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.Response.Status.CONFLICT;

/**
 * Intercepts and maps {@link LockAlreadyTakenException} exceptions thrown by REST API.
 *
 * @author Oleksandr Legenkiy
 */
@Slf4j
@Component
@Provider
public class LockAlreadyTakenExceptionMapper implements ExceptionMapper<LockAlreadyTakenException> {

    @Override
    public Response toResponse(LockAlreadyTakenException exception) {
        ErrorResponse errorResponse = new ErrorResponse(CONFLICT.getStatusCode(),
                CONFLICT.getReasonPhrase());
        log.warn(exception.getMessage(), exception);
        return Response.status(CONFLICT).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
