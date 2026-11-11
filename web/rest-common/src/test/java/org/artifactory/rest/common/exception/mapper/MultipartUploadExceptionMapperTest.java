package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.jfrog.storage.mpu.exceptions.MultipartUploadException;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Andrey Tashlitsky
 */
class MultipartUploadExceptionMapperTest {

    @Test
    void toResponseTest() {
        MultipartUploadException e = new MultipartUploadException("cool-exception", BAD_REQUEST.getStatusCode());
        MultipartUploadExceptionMapper mapper = new MultipartUploadExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(e.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(e.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}
