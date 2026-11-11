package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import org.artifactory.exception.http.InternalServerException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InternalServerExceptionMapperTest {

    private final InternalServerExceptionMapper mapper = new InternalServerExceptionMapper();

    @Test
    void toResponseReturns500WithMessage() {
        String message = "Unexpected failure while processing request";
        InternalServerException e = new InternalServerException(message);
        try (Response response = mapper.toResponse(e)) {
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertNotNull(errorResponse);
            assertEquals(1, errorResponse.getErrors().size());
            ErrorResponse.Error error = errorResponse.getErrors().getFirst();
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), error.getStatus());
            assertEquals(message, error.getMessage());
        }
    }
}
