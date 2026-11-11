package org.artifactory.rest.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExpiredRestExceptionTest {
    @Test
    void testExpiredRestException() {
        ExpiredRestException expiredRestException = new ExpiredRestException("message");
        assertEquals("message", expiredRestException.getMessage());
        expiredRestException = new ExpiredRestException(404, "message");
        assertEquals("message", expiredRestException.getMessage());
        assertEquals(404, expiredRestException.getStatusCode());
        Map<String, String> headers = new HashMap<>();
        expiredRestException = new ExpiredRestException(404, "message", headers);
        assertEquals("message", expiredRestException.getMessage());
        assertEquals(404, expiredRestException.getStatusCode());
        assertNotNull(expiredRestException.getHeaders());
    }
}