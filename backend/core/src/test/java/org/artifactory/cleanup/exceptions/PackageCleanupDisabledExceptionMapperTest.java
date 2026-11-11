package org.artifactory.cleanup.exceptions;

import org.artifactory.rest.ErrorResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
class PackageCleanupDisabledExceptionMapperTest {

    @Test
    void testToResponse() {
        var mapper = new PackageCleanupDisabledExceptionMapper();
        var response = mapper.toResponse(new PackageCleanupDisabledException("Test message"));
        assertEquals(400, response.getStatus());
        var errorResponse = (ErrorResponse) response.getEntity();
        assertEquals("Test message", errorResponse.getErrors().get(0).getMessage());
        assertEquals(400, errorResponse.getErrors().get(0).getStatus());
    }
}
