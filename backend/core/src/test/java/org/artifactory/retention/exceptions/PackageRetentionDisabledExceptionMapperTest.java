package org.artifactory.retention.exceptions;

import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
class PackageRetentionDisabledExceptionMapperTest {

    @Test
    void testToResponse() {
        var mapper = new PackageRetentionDisabledExceptionMapper();
        var response = mapper.toResponse(new PackageRetentionDisabledException("Test message"));
        assertEquals(400, response.getStatus());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals("Test message", errorResponse.getErrors().get(0).getMessage());
        assertEquals(400, errorResponse.getErrors().get(0).getStatus());
    }
}