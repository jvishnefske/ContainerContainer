package org.artifactory.storage.db.devopsagent.requestlog.service;

import lombok.Getter;

/**
 * Domain exception thrown by {@link RequestLogService}. The {@code statusCode} field carries
 * the HTTP status the REST resource layer will surface; {@code message} is the deterministic
 * prefix string the agent's tool definition branches on (see api-documentation-v1.md §6.2).
 * <p>
 * Storage-db does not depend on base-rest's {@code RestException} — the REST resource layer
 * catches this and translates.
 */
@Getter
public class RequestLogException extends RuntimeException {

    private final int statusCode;

    public RequestLogException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public RequestLogException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
