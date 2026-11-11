package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.access.client.AccessClientException;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AccessClientExceptionMapperTest {

    @Test
    void toResponseTest() {
        final String message = "unauthorized-access";
        final AccessClientException exception = new AccessClientException(message);
        final AccessClientExceptionMapper mapper = new AccessClientExceptionMapper();
        try (final Response response = mapper.toResponse(exception)) {
            final ErrorResponse errorResponse = (ErrorResponse) response.getEntity();

            assertEquals(message, errorResponse.getErrors().getFirst().getMessage());
            assertEquals(401, errorResponse.getErrors().getFirst().getStatus());
            assertEquals(401, response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}