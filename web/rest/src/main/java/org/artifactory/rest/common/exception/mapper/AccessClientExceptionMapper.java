package org.artifactory.rest.common.exception.mapper;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.rest.ErrorResponse;
import org.jfrog.access.client.AccessClientException;
import org.springframework.stereotype.Component;

/**
 * Intercepts and maps exceptions thrown by the Access http client.
 *
 * @author Amir Avidan
 */

@Component
@Provider
public class AccessClientExceptionMapper implements ExceptionMapper<AccessClientException> {

    @Override
    public Response toResponse(final AccessClientException exception) {
        final ErrorResponse errorResponse = new ErrorResponse(HttpServletResponse.SC_UNAUTHORIZED,
                                                              exception.getMessage());
        return Response.status(HttpServletResponse.SC_UNAUTHORIZED)
                       .type(MediaType.APPLICATION_JSON)
                       .entity(errorResponse)
                       .build();
    }
}
