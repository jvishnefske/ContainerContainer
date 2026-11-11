package org.artifactory.rest.util;

import org.artifactory.rest.ErrorResponse;
import org.artifactory.rest.common.exception.mapper.AccessClientHttpExceptionMapper;
import org.jfrog.access.client.AccessClientHttpException;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Amir Avidan
 */
class AccessClientHttpExceptionMapperTest {

    @Test
    void toResponseTest() {
        AccessClientHttpException e = new AccessClientHttpException(500, "cool-exception");
        AccessClientHttpExceptionMapper mapper = new AccessClientHttpExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    @Test
    void badRequestTest() {
        AccessClientHttpException e = new AccessClientHttpException(400, "bad-request-exception");
        AccessClientHttpExceptionMapper mapper = new AccessClientHttpExceptionMapper();
        try (Response response = mapper.toResponse(e)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(e.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(BAD_REQUEST.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }
}
