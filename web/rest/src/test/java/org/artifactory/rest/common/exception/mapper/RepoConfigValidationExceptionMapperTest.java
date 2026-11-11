package org.artifactory.rest.common.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.artifactory.repo.service.validation.RepoConfigValidationException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
class RepoConfigValidationExceptionMapperTest {

    @Test
    void testToResponse() {
        RepoConfigValidationExceptionMapper mapper = new RepoConfigValidationExceptionMapper();
        RepoConfigValidationException exception = new RepoConfigValidationException("test");
        try (Response response = mapper.toResponse(exception)) {
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals("test", errorResponse.getErrors().getFirst().getMessage());
            assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), errorResponse.getErrors().getFirst().getStatus());
        }
    }
}