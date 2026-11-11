package org.artifactory.rest.common.exception.mapper;

import org.artifactory.rest.ErrorResponse;
import org.artifactory.concurrent.LockAlreadyTakenException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Oleksandr Legenkiy
 */
class LockAlreadyTakenExceptionMapperTest {

    @Test
    void testToResponse() {
        var mapper = new LockAlreadyTakenExceptionMapper();
        var exception = new LockAlreadyTakenException("test");
        var response = mapper.toResponse(exception);
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertInstanceOf(ErrorResponse.class, response.getEntity());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals(HttpStatus.CONFLICT.getReasonPhrase(), errorResponse.getErrors().getFirst().getMessage());
    }
}
