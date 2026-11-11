package org.artifactory.rest.common.exception;

import org.artifactory.concurrent.LockAlreadyTakenException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Oleksandr Legenkiy
 */
class LockAlreadyTakenExceptionTest {

    @Test
    void testConstructor() {
        var e = new LockAlreadyTakenException("test");
        assertEquals("test", e.getMessage());
    }
}
