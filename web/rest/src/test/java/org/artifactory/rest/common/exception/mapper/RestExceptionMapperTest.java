package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.exception.RestException;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
class RestExceptionMapperTest {

    @Test
    void testToResponse() {
        RestException e = new RestException(BAD_REQUEST.getStatusCode(), "cool");
        RestExceptionMapper mapper = new RestExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(e.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(e.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    @Test
    void testToResponseNoStatus() {
        RestException e = new RestException("cool-message");
        RestExceptionMapper mapper = new RestExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(e.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(e.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    @Test
    void testToResponseWithHeaders() {
        var headers = Map.of("cool-header", "cool-value", "another-cool-header", "another-cool-value");
        RestException e = new RestException(BAD_REQUEST.getStatusCode(), "cool", headers);
        RestExceptionMapper mapper = new RestExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            assertEquals("cool-value", response.getHeaderString("cool-header"));
            assertEquals("another-cool-value", response.getHeaderString("another-cool-header"));
        }
    }
}
