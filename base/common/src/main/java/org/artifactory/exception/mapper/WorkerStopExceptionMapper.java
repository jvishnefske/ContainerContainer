package org.artifactory.exception.mapper;

import org.artifactory.exception.WorkerStopException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * @author Dvir Twina
 */
@Component
@Provider
public class WorkerStopExceptionMapper implements ExceptionMapper<WorkerStopException> {

    @Override
    public Response toResponse(WorkerStopException exception) {
        ErrorResponse errorResponse = new ErrorResponse(Response.Status.CONFLICT.getStatusCode(),
                exception.getMessage());
        return Response.status(Response.Status.CONFLICT).type(MediaType.APPLICATION_JSON).entity(errorResponse).build();
    }
}
