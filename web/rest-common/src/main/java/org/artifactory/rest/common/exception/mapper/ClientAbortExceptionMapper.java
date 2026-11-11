package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.apache.catalina.connector.ClientAbortException;
import org.artifactory.rest.ErrorResponse;
import org.springframework.stereotype.Component;

@Component
@Provider
public class ClientAbortExceptionMapper implements ExceptionMapper<ClientAbortException> {

    @Override
    public Response toResponse(ClientAbortException exception) {
        return Response.status(Response.Status.REQUEST_TIMEOUT)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(Response.Status.REQUEST_TIMEOUT.getStatusCode(),
                        "Client closed connection prematurely"))
                .build();
    }
}

