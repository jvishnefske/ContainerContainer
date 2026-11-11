package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.Response;
import org.artifactory.exception.http.ServiceUnavailableException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServiceUnavailableExceptionMapperTest {

    private final ServiceUnavailableExceptionMapper mapper = new ServiceUnavailableExceptionMapper();

    @Test
    void toResponseReturns503WithMessage() {
        String message = "Metadata service is temporarily unavailable";
        ServiceUnavailableException e = new ServiceUnavailableException(message);
        try (Response response = mapper.toResponse(e)) {
            assertEquals(SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertNotNull(errorResponse);
            assertEquals(1, errorResponse.getErrors().size());
            ErrorResponse.Error error = errorResponse.getErrors().getFirst();
            assertEquals(SERVICE_UNAVAILABLE.getStatusCode(), error.getStatus());
            assertEquals(message, error.getMessage());
        }
    }
}
