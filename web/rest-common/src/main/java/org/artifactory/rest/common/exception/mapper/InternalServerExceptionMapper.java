package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.http.InternalServerException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

@Slf4j
@Provider
@Component
public class InternalServerExceptionMapper implements ExceptionMapper<InternalServerException> {

    @Override
    public Response toResponse(InternalServerException e) {
        log.error("Internal server error:", e);
        ErrorResponse errorResponse = new ErrorResponse(INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage());
        return Response.status(INTERNAL_SERVER_ERROR).type(APPLICATION_JSON).entity(errorResponse).build();
    }
}
