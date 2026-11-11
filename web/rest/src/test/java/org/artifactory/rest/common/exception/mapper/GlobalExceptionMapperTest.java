package org.artifactory.rest.common.exception.mapper;

import org.artifactory.aql.QueryBlockedException;
import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionMapperTest {

    @ParameterizedTest
    @ValueSource(strings = {"An unexpected error has occurred, please check Artifactory logs for further details."})
    @NullAndEmptySource
    void toResponseWithoutMessage(String message) {
        GlobalExceptionMapper globalExceptionMapper = new GlobalExceptionMapper();
        Response response = globalExceptionMapper.toResponse(new Exception(message));
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertNotNull(errorResponse);
        assertEquals(1, errorResponse.getErrors().size());
        ErrorResponse.Error error = errorResponse.getErrors().get(0);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error.getStatus());
        assertEquals("An unexpected error has occurred, please check Artifactory logs for further details.",
                error.getMessage());
    }

    @Test
    void toResponseWhenCauseIsBlockedQueryReturnsBadRequest() {
        GlobalExceptionMapper globalExceptionMapper = new GlobalExceptionMapper();
        QueryBlockedException blocked = new QueryBlockedException(250000L, 100000, "SELECT * FROM nodes");
        Exception wrapped = new RuntimeException("outer", new RuntimeException("inner", blocked));
        Response response = globalExceptionMapper.toResponse(wrapped);

        assertEquals(BAD_REQUEST.getStatusCode(), response.getStatus());
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertNotNull(errorResponse);
        assertEquals(1, errorResponse.getErrors().size());
        ErrorResponse.Error error = errorResponse.getErrors().get(0);
        assertEquals(BAD_REQUEST.getStatusCode(), error.getStatus());
        assertEquals(blocked.getMessage(), error.getMessage());
    }
}