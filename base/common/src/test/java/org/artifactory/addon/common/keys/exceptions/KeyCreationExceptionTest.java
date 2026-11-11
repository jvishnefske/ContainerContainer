package org.artifactory.addon.common.keys.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Oshrat Zairi
 */
class KeyCreationExceptionTest {
    @Test
    void testMessageConstructor() {
        String message = "Key creation failed";

        KeyCreationException exception = new KeyCreationException(message);

        assertEquals(message, exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testMessageAndCauseConstructor() {
        String message = "Key creation failed";
        Throwable cause = new RuntimeException("Underlying cause");

        KeyCreationException exception = new KeyCreationException(message, cause);

        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}