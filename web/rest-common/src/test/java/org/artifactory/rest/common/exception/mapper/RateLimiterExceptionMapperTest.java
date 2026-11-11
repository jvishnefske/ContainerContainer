package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.download.RateLimiterException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.Response.Status.TOO_MANY_REQUESTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Amir Avidan
 */
class RateLimiterExceptionMapperTest {

    private final RateLimiterExceptionMapper mapper = new RateLimiterExceptionMapper();

    @Test
    void toResponseReturns429WithMessage() {
        RateLimiterException e = new RateLimiterException("Request rate limiter time exceeded");
        try (Response response = mapper.toResponse(e)) {
            assertEquals(TOO_MANY_REQUESTS.getStatusCode(), response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertNotNull(errorResponse);
            assertEquals(1, errorResponse.getErrors().size());
            ErrorResponse.Error error = errorResponse.getErrors().getFirst();
            assertEquals(TOO_MANY_REQUESTS.getStatusCode(), error.getStatus());
            assertEquals("Request rate limiter time exceeded", error.getMessage());
        }
    }
}
