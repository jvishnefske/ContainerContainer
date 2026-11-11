package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.exception.http.NotImplementedException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.NOT_IMPLEMENTED;

@Slf4j
@Provider
@Component
public class NotImplementedExceptionMapper implements ExceptionMapper<NotImplementedException> {

    @Override
    public Response toResponse(NotImplementedException e) {
        log.error("Not implemented error:", e);
        ErrorResponse errorResponse = new ErrorResponse(NOT_IMPLEMENTED.getStatusCode(), e.getMessage());
        return Response.status(NOT_IMPLEMENTED).type(APPLICATION_JSON).entity(errorResponse).build();
    }
}
