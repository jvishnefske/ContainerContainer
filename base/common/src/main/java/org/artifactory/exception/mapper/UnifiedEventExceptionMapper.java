package org.artifactory.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.exception.UnifiedEventException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

@Component
@Provider
public class UnifiedEventExceptionMapper implements ExceptionMapper<UnifiedEventException> {
    @Override
    public Response toResponse(UnifiedEventException exception) {
        int statusCode = exception.getStatusCode();
        return Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(statusCode, exception.getMessage()))
                .build();
    }

}
