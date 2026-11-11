package org.artifactory.exception.mapper;

import org.artifactory.exception.WorkerStopException;
import org.artifactory.rest.ErrorResponse;
import org.artifactory.unifiedevent.constants.SupportedUnifiedEvents;
import org.jfrog.worker.proto.execution.Action;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerStopExceptionMapperTest {

    @Test
    void testToResponse() {
        WorkerStopException exception = new WorkerStopException(Action.BEFORE_PROPERTY_DELETE,
                "worker returned with Stop");
        WorkerStopExceptionMapper mapper = new WorkerStopExceptionMapper();

        try (Response response = mapper.toResponse(exception)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(exception.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(CONFLICT.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(CONFLICT.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    @Test
    void testToResponseForUnifiedEvents() {
        WorkerStopException exception = new WorkerStopException(SupportedUnifiedEvents.BEFORE_DOWNLOAD,
                "worker returned with Stop");
        WorkerStopExceptionMapper mapper = new WorkerStopExceptionMapper();

        try (Response response = mapper.toResponse(exception)) {
            ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
            assertEquals(exception.getMessage(), errorResponse.getErrors().get(0).getMessage());
            assertEquals(CONFLICT.getStatusCode(), errorResponse.getErrors().get(0).getStatus());
            assertEquals(CONFLICT.getStatusCode(), response.getStatus());
            assertEquals(APPLICATION_JSON, response.getMediaType().toString());
        }
    }

    @Test
    void testExceptionConstructor() {
        String message = "worker returned with Stop";
        WorkerStopException exception = new WorkerStopException(message);
        assertEquals(message, exception.getMessage());
    }
}