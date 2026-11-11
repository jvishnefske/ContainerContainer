package org.artifactory.repo.service.validation;

import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Component
@Provider
public class ArtifactoryVersionExceptionMapper implements ExceptionMapper<ArtifactoryVersionException> {
    @Override
    public Response toResponse(ArtifactoryVersionException exception) {
        ErrorResponse errorResponse =
                new ErrorResponse(SC_METHOD_NOT_ALLOWED, exception.getMessage());
        return Response.status(SC_METHOD_NOT_ALLOWED)
                .type(APPLICATION_JSON)
                .entity(errorResponse)
                .build();
    }
}
