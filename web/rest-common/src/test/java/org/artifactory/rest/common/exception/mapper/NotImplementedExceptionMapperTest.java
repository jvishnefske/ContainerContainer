package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import org.artifactory.exception.http.NotImplementedException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.NOT_IMPLEMENTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotImplementedExceptionMapperTest {

    private final NotImplementedExceptionMapper mapper = new NotImplementedExceptionMapper();

    @Test
    void toResponseReturns501WithMessage() {
        String message = "This operation is not supported on this repository type";
        NotImplementedException e = new NotImplementedException(message);
        try (Response response = mapper.toResponse(e)) {
            assertEquals(NOT_IMPLEMENTED.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertNotNull(errorResponse);
            assertEquals(1, errorResponse.getErrors().size());
            ErrorResponse.Error error = errorResponse.getErrors().getFirst();
            assertEquals(NOT_IMPLEMENTED.getStatusCode(), error.getStatus());
            assertEquals(message, error.getMessage());
        }
    }
}
