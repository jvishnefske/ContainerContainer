package org.artifactory.rest.util;

import jakarta.ws.rs.core.Response;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.common.exception.mapper.BadCredentialsExceptionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BadCredentialsExceptionMapperTest {

    @Test
    void toResponseTest() {
        final BadCredentialsException e = new BadCredentialsException("cool-exception");
        final BadCredentialsExceptionMapper mapper = new BadCredentialsExceptionMapper();

        try (Response response = mapper.toResponse(e)) {
            final ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().getFirst().getMessage());
            assertEquals(SC_UNAUTHORIZED, errorResponse.getErrors().getFirst().getStatus());
            assertEquals(SC_UNAUTHORIZED, response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}
