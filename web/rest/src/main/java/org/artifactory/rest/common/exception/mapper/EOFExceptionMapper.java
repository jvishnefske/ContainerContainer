package org.artifactory.rest.common.exception.mapper;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.io.EOFException;

/**
 * Intercepts and maps {@link java.io.EOFException} exceptions thrown by the REST API.
 *
 * @author vovam
 */
@Component
@Provider
@Slf4j
public class EOFExceptionMapper implements ExceptionMapper<EOFException> {

    @Override
    public Response toResponse(EOFException exception) {
        log.error(exception.getMessage(), exception);
        ErrorResponse response = new ErrorResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                ExceptionMapperConstants.GENERAL_ERROR_MESSAGE);
        return Response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }
}