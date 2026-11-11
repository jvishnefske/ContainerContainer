package org.artifactory.federation.exceptions;

import lombok.Getter;

/**
 * @author Shahar Palmor
 */
@Getter
public class BinariesTaskException extends RuntimeException {
    private final int statusCode;
    public BinariesTaskException(String message, Throwable cause, int statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}