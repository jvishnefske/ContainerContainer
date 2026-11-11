package org.artifactory.rest.common.exception.mapper;

import org.artifactory.exception.InvalidOperationException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidOperationExceptionMapperTest {

    /**
     * Tests the toResponse method of InvalidNameExceptionMapper.
     */
    @Test
    void testInvalidNameExceptionMapper() {
        InvalidOperationException exception = new InvalidOperationException("Invalid operation performed");
        InvalidOperationExceptionMapper mapper = new InvalidOperationExceptionMapper();

        Response response = mapper.toResponse(exception);
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
        assertEquals(1, errorResponse.getErrors().size());
        ErrorResponse.Error error = errorResponse.getErrors().get(0);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), error.getStatus());
        assertEquals("Invalid operation performed", error.getMessage());
    }

}