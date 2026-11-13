/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.AccessClientException;

/**
 * Thrown when a caller reaches an Access client operation that embedded access does not implement. It is an
 * {@link AccessClientException}, so existing call sites that already handle Access failures handle it too.
 * <p>
 * The message is always {@code not supported by embedded access: <surface>.<method>}.
 */
public final class EmbeddedAccessUnsupportedException extends AccessClientException {

    /** The fixed message prefix. */
    public static final String PREFIX = "not supported by embedded access: ";

    private final String operation;

    /**
     * Creates the exception.
     *
     * @param operation {@code <surface>.<method>}
     */
    public EmbeddedAccessUnsupportedException(String operation) {
        super(PREFIX + operation);
        this.operation = operation;
    }

    /**
     * Returns the operation that was refused.
     *
     * @return {@code <surface>.<method>}
     */
    public String operation() {
        return operation;
    }
}
