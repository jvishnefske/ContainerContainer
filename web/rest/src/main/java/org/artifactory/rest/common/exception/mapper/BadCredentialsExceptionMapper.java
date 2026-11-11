package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.artifactory.rest.ErrorResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;


@Component
@Provider
public class BadCredentialsExceptionMapper implements ExceptionMapper<BadCredentialsException> {

    @Override
    public Response toResponse(BadCredentialsException exception) {
        ErrorResponse errorResponse = new ErrorResponse(SC_UNAUTHORIZED, exception.getMessage());
        return Response.status(SC_UNAUTHORIZED).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
