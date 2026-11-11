package org.artifactory.addon.common.keys.exceptions;

/**
 * @author Oshrat Zairi
 */
public class KeyCreationException extends RuntimeException {
    public KeyCreationException(String message) {
        super(message);
    }

    public KeyCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}