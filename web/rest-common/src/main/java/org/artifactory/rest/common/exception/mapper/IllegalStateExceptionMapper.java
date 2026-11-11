package org.artifactory.rest.common.exception.mapper;

import lombok.extern.slf4j.Slf4j;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Slf4j
@Component
@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    @Override
    public Response toResponse(IllegalStateException exception) {
        final int UnprocessableContentException = 422;
        log.warn("Request failed due to invalid state: {}", exception.getMessage(), exception);
        ErrorResponse errorResponse = new ErrorResponse(UnprocessableContentException,
                exception.getMessage());
        return Response.status(UnprocessableContentException).type(MediaType.APPLICATION_JSON).entity(errorResponse)
                .build();
    }
}
