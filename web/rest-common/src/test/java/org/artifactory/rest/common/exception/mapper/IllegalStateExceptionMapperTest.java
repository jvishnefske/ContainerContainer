package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IllegalStateExceptionMapperTest {

    private final IllegalStateExceptionMapper mapper = new IllegalStateExceptionMapper();

    @Test
    void toResponseReturns422WithMessage() {
        final String message = "Repo offline-remote-repo is offline. Cannot use the HTTP client.";
        final int UnprocessableContentException = 422;
        final IllegalStateException e = new IllegalStateException(message);
        try (Response response = mapper.toResponse(e)) {
            assertEquals(UnprocessableContentException, response.getStatus());
            assertEquals(APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertNotNull(errorResponse);
            assertEquals(1, errorResponse.getErrors().size());
            ErrorResponse.Error error = errorResponse.getErrors().getFirst();
            assertEquals(UnprocessableContentException, error.getStatus());
            assertEquals(message, error.getMessage());
        }
    }
}
