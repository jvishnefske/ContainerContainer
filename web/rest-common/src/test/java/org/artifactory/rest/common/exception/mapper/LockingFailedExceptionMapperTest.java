package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.artifactory.concurrent.LockingFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Oleksandr Legenkiy
 */
class LockingFailedExceptionMapperTest {

    @Test
    void testToResponse() {
        var mapper = new LockingFailedExceptionMapper();
        var exception = new LockingFailedException("test", new RuntimeException());
        var response = mapper.toResponse(exception);
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                errorResponse.getErrors().getFirst().getMessage());
    }

}
