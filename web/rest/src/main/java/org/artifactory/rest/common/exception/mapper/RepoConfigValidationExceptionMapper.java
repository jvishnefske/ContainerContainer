package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.repo.service.validation.RepoConfigValidationException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

/**
 * @author Oleksandr Legenkiy
 */
@Component
@Provider
public class RepoConfigValidationExceptionMapper implements ExceptionMapper<RepoConfigValidationException> {

    @Override
    public Response toResponse(RepoConfigValidationException exception) {
        ErrorResponse errorResponse = new ErrorResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
