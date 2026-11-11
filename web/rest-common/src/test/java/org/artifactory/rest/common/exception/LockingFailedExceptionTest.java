package org.artifactory.rest.common.exception;

import org.artifactory.concurrent.LockingFailedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
class LockingFailedExceptionTest {

    @Test
    void testConstructor() {
        var cause = new RuntimeException("cause");
        var e = new LockingFailedException("test", cause);
        assertEquals("test", e.getMessage());
        assertEquals(cause, e.getCause());
    }
}
