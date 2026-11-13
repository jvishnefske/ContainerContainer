/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.artifactory.security.access.embedded.core.EaOutcome;
import org.artifactory.security.access.embedded.store.EmbeddedAccessStore;
import org.jfrog.access.client.AccessClientException;
import org.jfrog.access.client.AccessClientHttpException;

/**
 * Turns {@link EaOutcome} results into the exceptions Access client callers already handle.
 * <p>
 * Call sites in Artifactory branch on {@link AccessClientHttpException#getStatusCode()} (for example "ignore 404 on
 * delete") and read {@link AccessClientHttpException#getErrorsModel()}, so refusals are mapped to the status a
 * remote server would have answered, with an errors body: missing rows are 404, duplicates and compare-and-swap
 * conflicts 409, storage failures 500, anything else 400. {@code Unsupported} becomes
 * {@link EmbeddedAccessUnsupportedException}.
 */
final class EaErrors {

    /** Status of a refused login. */
    static final int UNAUTHORIZED = 401;
    /** Status of a missing row. */
    static final int NOT_FOUND = 404;
    /** Status of a duplicate or conflicting write. */
    static final int CONFLICT = 409;
    /** Status of invalid input. */
    static final int BAD_REQUEST = 400;
    /** Status of a storage failure. */
    static final int SERVER_ERROR = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EaErrors() {
    }

    /**
     * Returns the value of an {@code Ok} outcome or throws the matching exception.
     *
     * @param outcome the outcome
     * @param <T>     the value type
     * @return the value
     * @throws AccessClientHttpException            for {@code Refused}
     * @throws EmbeddedAccessUnsupportedException for {@code Unsupported}
     */
    static <T> T orThrow(EaOutcome<T> outcome) {
        return switch (outcome) {
            case EaOutcome.Ok<T> ok -> ok.value();
            case EaOutcome.Refused<T> refused -> throw refusal(refused.reason());
            case EaOutcome.Unsupported<T> unsupported ->
                    throw new EmbeddedAccessUnsupportedException(unsupported.operation());
        };
    }

    /**
     * Maps a refusal reason to an HTTP-shaped exception.
     *
     * @param reason the reason
     * @return the exception to throw
     */
    static AccessClientHttpException refusal(String reason) {
        return http(statusFor(reason), reason);
    }

    /**
     * Picks the status for a refusal reason by the store's reason conventions.
     *
     * @param reason the reason
     * @return the status code
     */
    static int statusFor(String reason) {
        if (reason.startsWith(EmbeddedAccessStore.STORAGE_ERROR_PREFIX)) {
            return SERVER_ERROR;
        }
        if (reason.startsWith("no such ")) {
            return NOT_FOUND;
        }
        if (reason.contains("already exists") || reason.startsWith("conflict")) {
            return CONFLICT;
        }
        return BAD_REQUEST;
    }

    /**
     * Builds an exception with an errors body, so {@code getErrorsModel()} is never {@code null}.
     *
     * @param status  the status
     * @param message the message
     * @return the exception
     */
    static AccessClientHttpException http(int status, String message) {
        return http(status, String.valueOf(status), message);
    }

    /**
     * Builds an exception with an errors body carrying an explicit error code.
     *
     * @param status  the status
     * @param code    the error code, for example {@code CODE_DISABLED}'s value
     * @param message the message
     * @return the exception
     */
    static AccessClientHttpException http(int status, String code, String message) {
        ObjectNode body = MAPPER.createObjectNode();
        ObjectNode error = body.putArray("errors").addObject();
        error.put("code", code);
        error.put("message", message);
        return new AccessClientHttpException(status, message, body.toString());
    }

    /**
     * Wraps an unexpected failure in a plain Access client exception.
     *
     * @param message what was being done
     * @param cause   the cause
     * @return the exception
     */
    static AccessClientException failure(String message, Throwable cause) {
        return new AccessClientException(message, cause);
    }
}
