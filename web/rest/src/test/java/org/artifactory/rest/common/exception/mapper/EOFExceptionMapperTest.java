package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.io.EOFException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EOFExceptionMapperTest {

    @Test
    public void toResponseWithoutMessage() {
        EOFExceptionMapper eofExceptionMapper = new EOFExceptionMapper();
        Response response = eofExceptionMapper.toResponse(new EOFException());
        assertEquals(response.getStatus(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        ErrorResponse errorResponse = (ErrorResponse) response.getEntity();
        assertNotNull(errorResponse);
        assertEquals(1, errorResponse.getErrors().size());
        ErrorResponse.Error error = errorResponse.getErrors().get(0);
        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, error.getStatus());
        assertEquals("An unexpected error has occurred, please check Artifactory logs for further details.",
                error.getMessage());
    }
}