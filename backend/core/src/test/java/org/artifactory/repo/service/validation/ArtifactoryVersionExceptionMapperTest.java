package org.artifactory.repo.service.validation;

import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactoryVersionExceptionMapperTest {

    @Test
    void toResponseTest() {
        ArtifactoryVersionException e = new ArtifactoryVersionException("cool-exception");
        ArtifactoryVersionExceptionMapper mapper = new ArtifactoryVersionExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(METHOD_NOT_ALLOWED.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(METHOD_NOT_ALLOWED.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}
