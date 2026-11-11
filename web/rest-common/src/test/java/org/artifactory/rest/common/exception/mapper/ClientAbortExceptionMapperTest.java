package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.catalina.connector.ClientAbortException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientAbortExceptionMapperTest {

    @Test
    void toResponse() {
        // Create the exception
        ClientAbortException exception = new ClientAbortException("Connection aborted");

        // Create the mapper
        ClientAbortExceptionMapper mapper = new ClientAbortExceptionMapper();

        // Get the response
        Response response = mapper.toResponse(exception);
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();

        // Verify response properties
        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
        assertEquals(1, errorResponse.getErrors().size());

        // Verify error details
        ErrorResponse.Error error = errorResponse.getErrors().getFirst();
        assertEquals(Response.Status.REQUEST_TIMEOUT.getStatusCode(), error.getStatus());
        assertEquals("Client closed connection prematurely", error.getMessage());
    }
}